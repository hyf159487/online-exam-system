package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.AiDifficultyResult;
import com.exam.exam_system.dto.WordParseResult;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.AiDifficultyService;
import com.exam.exam_system.service.WordParseService;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Word (.docx) 文档解析实现
 */
@Service
public class WordParseServiceImpl implements WordParseService {

    private static final Logger log = LoggerFactory.getLogger(WordParseServiceImpl.class);

    /** AI 难度评估总超时（秒）：超过此时间立即降级为本地 7 维启发式规则，避免长时间阻塞解析 */
    private static final int AI_TOTAL_TIMEOUT_SECONDS = 5;

    @Autowired
    private AiDifficultyService aiDifficultyService;

    /** 题干起始行正则：数字 + 点号，如 "1." "2．" */
    private static final Pattern QUESTION_START = Pattern.compile(
            "^\\s*(\\d+)[\\.．、)）]\\s*(.*)"
    );

    /** 选项起始行正则：A/B/C/D/E/F + 点号，如 "A." "B．" */
    private static final Pattern OPTION_START = Pattern.compile(
            "^\\s*([A-F])[\\.．、)）]\\s*(.*)"
    );

    /** 答案匹配：题干中的 （X ） 或 (X )，支持 A-F、T/F、对/错 */
    private static final Pattern ANSWER_INLINE = Pattern.compile(
            "[（(]\\s*([A-F对错TF])\\s*[)）]"
    );

    /** 多选答案匹配：如 "(ABC)" 或 "（ABD）" */
    private static final Pattern MULTI_ANSWER = Pattern.compile(
            "[（(]\\s*([A-F]{2,})\\s*[)）]"
    );

    /** 判断题答案匹配：（T）/（F）/（对）/（错） */
    private static final Pattern TF_ANSWER = Pattern.compile(
            "[（(]\\s*([TFtf对错])\\s*[)）]"
    );

    /** 填空题空白标记匹配：「xxx」或 ___ */
    private static final Pattern BLANK_MARKER = Pattern.compile("「([^」]+)」|_{2,}");

    /** 填空题答案提取模式：匹配 "包括/是/为/有" 等关键词后面的内容作为答案 */
    private static final Pattern FILL_ANSWER_PATTERN = Pattern.compile(
            "(?:包括|是|为|有|包含|分为|等于|表示|指的是)\\s*([^。.\\n]+)"
    );

    /** 「题目：」/「题干：」起始行（简答题等无编号格式） */
    private static final Pattern TITLE_START = Pattern.compile(
            "^\\s*(?:题\\s*目|题\\s*干)\\s*[：:]\\s*(.*)$"
    );

    /** 「答案：」行：可同行给出答案，也可换行后多行作答（简答题常见） */
    private static final Pattern ANSWER_LINE = Pattern.compile(
            "^\\s*答\\s*案\\s*[：:]\\s*(.*)$"
    );

    /** 题干中内联的「答案：」标记（答案直接跟在题干同一行，填空题常见） */
    private static final Pattern INLINE_ANSWER_MARK = Pattern.compile("答\\s*案\\s*[：:]");

    /** 答案后内联的「解析：」标记 */
    private static final Pattern INLINE_ANALYSIS_MARK = Pattern.compile("解\\s*析\\s*[：:]");

    @Override
    public WordParseResult parseWord(MultipartFile file, Long courseId, Long creatorId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传有效的 Word 文档");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("仅支持 .docx 格式文件");
        }
        long totalStart = System.currentTimeMillis();
        log.info("[parseWord] ========== 开始解析文件：{}，size={}B，courseId={} ==========",
                originalFilename, file.getSize(), courseId);

        try (InputStream is = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(is)) {

            long t0 = System.currentTimeMillis();
            String title = extractTitleFromDoc(doc);
            List<ParagraphInfo> allLines = extractLinesFromDoc(doc);
            log.info("[parseWord] 1. 文档提取完成：段落={}行，标题={}，耗时={}ms",
                    allLines.size(), title, System.currentTimeMillis() - t0);

            t0 = System.currentTimeMillis();
            ParseReport parseReport = doParse(allLines, courseId, creatorId);
            log.info("[parseWord] 2. 分题完成：成功={}，失败={}，耗时={}ms",
                    parseReport.questions.size(), parseReport.failedReasons.size(), System.currentTimeMillis() - t0);

            // AI 批量评估难度（总超时 AI_TOTAL_TIMEOUT_SECONDS 秒，避免大模型网络阻塞解析）
            applyAiDifficulty(parseReport.questions);

            WordParseResult result = new WordParseResult();
            result.setTitle(title);
            result.setQuestions(parseReport.questions);
            result.setSuccessCount(parseReport.questions.size());
            result.setFailedCount(parseReport.failedReasons.size());
            result.setFailedReasons(parseReport.failedReasons);
            log.info("[parseWord] ========== 解析完成：成功={}，失败={}，总耗时={}ms ==========",
                    parseReport.questions.size(), parseReport.failedReasons.size(),
                    System.currentTimeMillis() - totalStart);
            return result;

        } catch (IOException e) {
            log.error("[parseWord] IO 异常：" + e.getMessage(), e);
            throw new RuntimeException("解析 Word 文档失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[parseWord] 解析异常：" + e.getMessage(), e);
            throw new RuntimeException("解析 Word 文档失败：" + e.getMessage(), e);
        }
    }

    @Override
    public WordParseResult parseText(String text, Long courseId, Long creatorId) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入要解析的文本内容");
        }
        long totalStart = System.currentTimeMillis();
        log.info("[parseText] ========== 开始解析文本：长度={}，courseId={} ==========", text.length(), courseId);
        long t0 = System.currentTimeMillis();
        String title = extractTitleFromText(text);
        List<ParagraphInfo> allLines = extractLinesFromText(text);
        log.info("[parseText] 1. 文本提取完成：段落={}行，标题={}，耗时={}ms",
                allLines.size(), title, System.currentTimeMillis() - t0);

        t0 = System.currentTimeMillis();
        ParseReport parseReport = doParse(allLines, courseId, creatorId);
        log.info("[parseText] 2. 分题完成：成功={}，失败={}，耗时={}ms",
                parseReport.questions.size(), parseReport.failedReasons.size(), System.currentTimeMillis() - t0);

        // AI 批量评估难度
        applyAiDifficulty(parseReport.questions);

        WordParseResult result = new WordParseResult();
        result.setTitle(title);
        result.setQuestions(parseReport.questions);
        result.setSuccessCount(parseReport.questions.size());
        result.setFailedCount(parseReport.failedReasons.size());
        result.setFailedReasons(parseReport.failedReasons);
        log.info("[parseText] ========== 解析完成：成功={}，失败={}，总耗时={}ms ==========",
                parseReport.questions.size(), parseReport.failedReasons.size(),
                System.currentTimeMillis() - totalStart);
        return result;
    }

    /**
     * 对解析出的题目列表调用 AI 难度评估，并回填 difficulty 字段。
     * 总超时 AI_TOTAL_TIMEOUT_SECONDS 秒：超时后立即降级为本地中等难度（2），不让解析整体卡住。
     */
    private void applyAiDifficulty(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return;
        long t0 = System.currentTimeMillis();
        try {
            List<AiDifficultyResult> results = CompletableFuture
                    .supplyAsync(() -> aiDifficultyService.evaluateBatch(questions))
                    .get(AI_TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (int i = 0; i < questions.size() && i < results.size(); i++) {
                AiDifficultyResult r = results.get(i);
                if (r != null && r.getDifficulty() != null) {
                    questions.get(i).setDifficulty(r.getDifficulty());
                }
            }
            log.info("[applyAiDifficulty] 完成：评估 {} 题，耗时 {}ms", questions.size(), System.currentTimeMillis() - t0);
        } catch (java.util.concurrent.TimeoutException te) {
            log.warn("[applyAiDifficulty] 超时（>{}s），已降级为本地默认难度（中等 2），题目数={}",
                    AI_TOTAL_TIMEOUT_SECONDS, questions.size());
            for (Question q : questions) {
                if (q.getDifficulty() == null) q.setDifficulty(2);
            }
        } catch (Exception e) {
            log.warn("[applyAiDifficulty] 评估异常，已降级为本地默认难度（中等 2）：{}，耗时 {}ms",
                    e.getMessage(), System.currentTimeMillis() - t0);
            for (Question q : questions) {
                if (q.getDifficulty() == null) q.setDifficulty(2);
            }
        }
    }

    /**
     * 从 Word 文档提取段落信息列表
     */
    private List<ParagraphInfo> extractLinesFromDoc(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        List<ParagraphInfo> allLines = new ArrayList<>();

        if (paragraphs.isEmpty()) {
            return allLines;
        }

        // 第一行为试卷标题，跳过（若首行是「题目：」格式则属于题目，不能跳过）
        int startIdx = 0;
        String firstLine = getParagraphText(paragraphs.get(0)).trim();
        if (!firstLine.isEmpty() && !QUESTION_START.matcher(firstLine).matches()
                && !TITLE_START.matcher(firstLine).matches()) {
            startIdx = 1;
        }

        for (int i = startIdx; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            String text = getParagraphText(p).trim();
            List<String> images = getImagesFromParagraph(p);
            if (!text.isEmpty() || !images.isEmpty()) {
                allLines.add(new ParagraphInfo(text, images));
            }
        }
        return allLines;
    }

    /**
     * 从纯文本提取段落信息列表
     */
    private List<ParagraphInfo> extractLinesFromText(String text) {
        List<ParagraphInfo> allLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue; // 跳过空行
                }
                // 第一行如果不是题目开头，视为标题跳过
                if (firstLine) {
                    firstLine = false;
                    if (!QUESTION_START.matcher(trimmed).matches()
                            && !TITLE_START.matcher(trimmed).matches()) {
                        continue;
                    }
                }
                allLines.add(new ParagraphInfo(trimmed, Collections.emptyList()));
            }
        } catch (IOException e) {
            throw new RuntimeException("解析文本失败：" + e.getMessage(), e);
        }
        return allLines;
    }

    /**
     * 从 Word 文档提取标题（首行）
     */
    private String extractTitleFromDoc(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        if (paragraphs.isEmpty()) {
            return "";
        }
        String firstLine = getParagraphText(paragraphs.get(0)).trim();
        if (firstLine.isEmpty() || QUESTION_START.matcher(firstLine).matches()
                || TITLE_START.matcher(firstLine).matches()) {
            return "";
        }
        return firstLine;
    }

    /**
     * 从纯文本提取标题（首行）
     */
    private String extractTitleFromText(String text) {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (QUESTION_START.matcher(trimmed).matches()
                        || TITLE_START.matcher(trimmed).matches()) {
                    return "";
                }
                return trimmed;
            }
        } catch (IOException e) {
            return "";
        }
        return "";
    }

    /**
     * 核心解析逻辑（通用）—— 返回带失败追踪的结构
     */
    private ParseReport doParse(List<ParagraphInfo> allLines, Long courseId, Long creatorId) {
        ParseReport report = new ParseReport();

        if (allLines.isEmpty()) {
            return report;
        }

        // 识别题目起始位置（数字编号 或 「题目：」/「题干：」格式）
        List<Integer> questionStarts = new ArrayList<>();
        for (int i = 0; i < allLines.size(); i++) {
            ParagraphInfo line = allLines.get(i);
            if (QUESTION_START.matcher(line.text).matches()
                    || TITLE_START.matcher(line.text).matches()) {
                questionStarts.add(i);
            }
        }

        if (questionStarts.isEmpty()) {
            // 没识别到题目，把整个文档当作一题处理
            Question q = buildQuestion(allLines, 0, allLines.size(), courseId, creatorId);
            if (q != null) {
                report.questions.add(q);
            } else {
                report.failedReasons.add("整篇文本未识别到有效题目");
            }
            return report;
        }

        // 逐题解析
        for (int s = 0; s < questionStarts.size(); s++) {
            int from = questionStarts.get(s);
            int to = (s + 1 < questionStarts.size()) ? questionStarts.get(s + 1) : allLines.size();
            Question q = buildQuestion(allLines, from, to, courseId, creatorId);
            if (q != null) {
                report.questions.add(q);
            } else {
                report.failedReasons.add("第 " + (s + 1) + " 题（起始行 " + (from + 1) + "）：题干为空或格式异常");
            }
        }

        return report;
    }

    /**
     * 构建一道题目
     */
    private Question buildQuestion(List<ParagraphInfo> lines, int from, int to,
                                   Long courseId, Long creatorId) {
        StringBuilder content = new StringBuilder();
        List<String> contentImages = new ArrayList<>();
        List<String> optionImages = new ArrayList<>();
        List<OptionInfo> options = new ArrayList<>();
        StringBuilder analysis = new StringBuilder();
        String answer = "";
        /** 通过「答案：」行显式标注的答案（简答题等多行内容） */
        String explicitAnswer = "";

        // 题干从起始行开始，直到遇到第一个选项行、答案行或解析行
        int i = from;
        {
            ParagraphInfo first = lines.get(i);
            Matcher qm = QUESTION_START.matcher(first.text);
            Matcher tm = TITLE_START.matcher(first.text);
            if (qm.matches()) {
                // 支持编号后带「题目：」前缀（如 "5. 题目：简述..."）
                String stemPart = qm.group(2).trim()
                        .replaceFirst("^题\\s*目\\s*[：:]\\s*", "")
                        .replaceFirst("^题\\s*干\\s*[：:]\\s*", "");
                content.append(stemPart);
                contentImages.addAll(first.images);
                i++;
            } else if (tm.matches()) {
                content.append(tm.group(1).trim());
                contentImages.addAll(first.images);
                i++;
            } else {
                // 首行不符合题目格式，整段作为题干
                content.append(first.text.trim());
                contentImages.addAll(first.images);
                i++;
            }
            while (i < to) {
                ParagraphInfo line = lines.get(i);
                if (isOptionLine(line.text) || isAnalysisLine(line.text) || isAnswerLine(line.text)) {
                    break;
                }
                content.append(line.text);
                contentImages.addAll(line.images);
                i++;
            }
        }

        // 提取选项 + 解析（可能交叉出现，统一处理）
        List<String> analysisParts = new ArrayList<>();
        while (i < to) {
            ParagraphInfo line = lines.get(i);
            if (isOptionLine(line.text)) {
                Matcher om = OPTION_START.matcher(line.text);
                if (om.matches()) {
                    String label = om.group(1);
                    String optText = om.group(2).trim();
                    List<String> optImgs = new ArrayList<>(line.images);
                    int j = i + 1;
                    while (j < to) {
                        ParagraphInfo next = lines.get(j);
                        if (isOptionLine(next.text) || isAnswerLine(next.text) || isNextQuestionOrEnd(lines, j, to)) {
                            break;
                        }
                        if (isAnalysisLine(next.text)) {
                            // 解析出现在选项中间，跳过
                            break;
                        }
                        optText += next.text;
                        optImgs.addAll(next.images);
                        j++;
                    }
                    options.add(new OptionInfo(label, optText, optImgs));
                    optionImages.addAll(optImgs);
                    i = j;
                } else {
                    i++;
                }
            } else if (isAnswerLine(line.text)) {
                // 「答案：」行：同行内容 + 跨行答案主体（简答题常以 ①②③ 分条）
                Matcher am = ANSWER_LINE.matcher(line.text);
                String inline = am.matches() ? am.group(1).trim() : "";
                List<String> answerLines = new ArrayList<>();
                if (!inline.isEmpty()) {
                    answerLines.add(inline);
                }
                contentImages.addAll(line.images);
                i++;
                while (i < to) {
                    ParagraphInfo next = lines.get(i);
                    if (isOptionLine(next.text) || isAnalysisLine(next.text)
                            || isAnswerLine(next.text) || isNextQuestionOrEnd(lines, i, to)) {
                        break;
                    }
                    answerLines.add(next.text);
                    contentImages.addAll(next.images);
                    i++;
                }
                explicitAnswer = String.join("\n", answerLines).trim();
            } else if (isAnalysisLine(line.text)) {
                String analysisPart = line.text.replaceFirst("^\\s*解析[：:]*\\s*", "").trim();
                analysisParts.add(analysisPart);
                contentImages.addAll(line.images);
                i++;
                // 解析可能跨多行
                while (i < to && !isOptionLine(lines.get(i).text) && !isAnswerLine(lines.get(i).text)
                        && !isNextQuestionOrEnd(lines, i, to)) {
                    ParagraphInfo aline = lines.get(i);
                    if (isAnalysisLine(aline.text)) {
                        analysisParts.add(aline.text.replaceFirst("^\\s*解析[：:]*\\s*", "").trim());
                    } else {
                        analysisParts.add(aline.text);
                    }
                    contentImages.addAll(aline.images);
                    i++;
                }
            } else {
                // 既不是选项也不是解析，归入题干
                content.append(line.text);
                contentImages.addAll(line.images);
                i++;
            }
        }

        // 合并解析
        if (!analysisParts.isEmpty()) {
            analysis.append(String.join(" ", analysisParts));
        }

        if (content.length() == 0) {
            return null;
        }

        // 判断题型 & 答案
        String contentStr = content.toString().trim();
        int questionType;
        String detectedAnswer = "";

        // 题干中内联的「答案：xxx」——从题干中剥离（填空题答案常直接跟在题干同一行）
        if (explicitAnswer.isEmpty()) {
            Matcher inlineAns = INLINE_ANSWER_MARK.matcher(contentStr);
            if (inlineAns.find()) {
                String stemPart = contentStr.substring(0, inlineAns.start()).trim();
                String ansPart = contentStr.substring(inlineAns.end()).trim();
                if (!stemPart.isEmpty() && !ansPart.isEmpty()) {
                    contentStr = stemPart;
                    // 答案后面若还内联了「解析：xxx」，继续剥离到解析
                    Matcher inlineAna = INLINE_ANALYSIS_MARK.matcher(ansPart);
                    if (inlineAna.find()) {
                        String a = ansPart.substring(0, inlineAna.start()).trim();
                        String ana = ansPart.substring(inlineAna.end()).trim();
                        if (!a.isEmpty()) {
                            explicitAnswer = a;
                        }
                        if (!ana.isEmpty() && analysis.length() == 0) {
                            analysis.append(ana);
                        }
                    } else {
                        explicitAnswer = ansPart;
                    }
                }
            }
        }

        Matcher multiMatcher = MULTI_ANSWER.matcher(contentStr);
        Matcher singleMatcher = ANSWER_INLINE.matcher(contentStr);
        Matcher tfMatcher = TF_ANSWER.matcher(contentStr);

        if (multiMatcher.find()) {
            detectedAnswer = multiMatcher.group(1).trim();
            questionType = 2;
            // 从题干中剥离内联答案 "(ABD)"
            contentStr = multiMatcher.replaceFirst("").trim();
        } else if (tfMatcher.find()) {
            detectedAnswer = tfMatcher.group(1).trim();
            if ("对".equals(detectedAnswer) || "t".equalsIgnoreCase(detectedAnswer)) {
                detectedAnswer = "T";
            } else {
                detectedAnswer = "F";
            }
            questionType = 3;
            // 从题干中剥离内联答案 "(T)" / "(F)"
            contentStr = tfMatcher.replaceFirst("").trim();
        } else if (singleMatcher.find()) {
            detectedAnswer = singleMatcher.group(1).trim();
            questionType = 1;
            // 从题干中剥离内联答案 "(A)" / "(B)"
            contentStr = singleMatcher.replaceFirst("").trim();
        } else if (!explicitAnswer.isEmpty()) {
            // 「答案：」行显式标注的答案
            String a = explicitAnswer.trim();
            if (a.equalsIgnoreCase("T") || a.equalsIgnoreCase("F") || "对".equals(a) || "错".equals(a)) {
                detectedAnswer = (a.equalsIgnoreCase("T") || "对".equals(a)) ? "T" : "F";
                questionType = 3;
            } else if (!options.isEmpty()) {
                // 选择题显式答案：单字母为单选，多字母为多选
                detectedAnswer = a;
                questionType = a.length() > 1 ? 2 : 1;
            } else if (a.length() > 20 || a.contains("\n") || a.matches(".*[①②③④⑤⑥⑦⑧⑨⑩].*")) {
                // 答案内容较长或分条（①②③）→ 简答题
                detectedAnswer = a;
                questionType = 5;
            } else {
                // 短文本答案 → 填空题
                detectedAnswer = a;
                questionType = 4;
            }
        } else if (!options.isEmpty()) {
            questionType = 1;
        } else {
            questionType = 4;
        }

        if (detectedAnswer.isEmpty() && !options.isEmpty()) {
            detectedAnswer = findAnswerFromOptions(options);
        }

        // 填空题：从题干中提取答案
        if (detectedAnswer.isEmpty() && questionType == 4) {
            detectedAnswer = extractFillAnswer(contentStr);
        }

        // 简答题：如果有解析但没有答案，使用解析作为答案
        if (questionType == 5 && detectedAnswer.isEmpty() && analysis.length() > 0) {
            detectedAnswer = analysis.toString().trim();
        }

        // 重新判断题型：如果原先是 type 4，但有长解析，应改为 type 5
        // 注意：若用户已通过「答案：」行显式给出短答案（填空题格式），则信任用户标注，保持填空题
        if (questionType == 4 && analysis.length() > 30 && explicitAnswer.isEmpty()) {
            questionType = 5;
            // 如果还没有答案，用解析作为答案
            if (detectedAnswer.isEmpty()) {
                detectedAnswer = analysis.toString().trim();
            }
        }

        if (!detectedAnswer.isEmpty()) {
            answer = detectedAnswer;
        }

        Question q = new Question();
        q.setCourseId(courseId);
        q.setCreatorId(creatorId);
        q.setAuditStatus(0);
        q.setContent(contentStr);
        q.setAnalysis(analysis.toString());
        // 难度由 AI 评估服务在解析完成后统一回填，此处先置 null
        q.setDifficulty(null);
        q.setScore(new BigDecimal("2.0"));

        if (questionType == 1 || questionType == 2) {
            List<String> optionTexts = new ArrayList<>();
            for (OptionInfo oi : options) {
                optionTexts.add(oi.text);
            }
            q.setOptions(toJsonArray(optionTexts));
            q.setAnswer(answer);
        } else if (questionType == 3) {
            q.setOptions(null);
            q.setAnswer(answer.isEmpty() ? "T" : answer);
        } else {
            // 填空题或简答题
            q.setOptions(null);
            q.setAnswer(answer);
        }
        q.setQuestionType(questionType);

        return q;
    }

    private String findAnswerFromOptions(List<OptionInfo> options) {
        // 无内联答案时，无法从选项中推断正确答案
        return "";
    }

    /**
     * 从填空题题干中提取答案
     * 策略：
     * 1. 优先提取「xxx」标记中的内容
     * 2. 匹配 "包括/是/为/有" 等关键词后面的内容
     * 3. 返回提取到的答案
     */
    private String extractFillAnswer(String content) {
        // 策略1：提取「xxx」标记中的内容（纯 ___ 空位跳过，不作为答案）
        Matcher blankMatcher = BLANK_MARKER.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean captured = false;
        while (blankMatcher.find()) {
            String group1 = blankMatcher.group(1);
            if (group1 != null) {
                if (captured) {
                    sb.append("、");
                }
                sb.append(group1);
                captured = true;
            }
        }
        if (captured) {
            return sb.toString();
        }

        // 策略2：匹配关键词后面的内容
        Matcher fillMatcher = FILL_ANSWER_PATTERN.matcher(content);
        if (fillMatcher.find()) {
            String extracted = fillMatcher.group(1).trim();
            // 清理：去掉可能的选项字母 A. B. 等
            extracted = extracted.replaceAll("[A-F][\\.．、)）]\\s*", "").trim();
            return extracted;
        }

        return "";
    }

    private boolean isOptionLine(String text) {
        return OPTION_START.matcher(text).matches();
    }

    private boolean isAnalysisLine(String text) {
        return text.startsWith("解析") || text.startsWith("【解析】") || text.startsWith("【解析:】")
                || text.startsWith("解析：") || text.startsWith("解析:");
    }

    private boolean isAnswerLine(String text) {
        return ANSWER_LINE.matcher(text).matches();
    }

    private boolean isNextQuestionOrEnd(List<ParagraphInfo> lines, int idx, int to) {
        if (idx >= to) return true;
        ParagraphInfo line = lines.get(idx);
        return QUESTION_START.matcher(line.text).matches()
                || TITLE_START.matcher(line.text).matches();
    }

    private String getParagraphText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            String text = run.text();
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private List<String> getImagesFromParagraph(XWPFParagraph p) {
        List<String> images = new ArrayList<>();
        for (XWPFRun run : p.getRuns()) {
            try {
                for (XWPFPicture picture : run.getEmbeddedPictures()) {
                    try {
                        XWPFPictureData pictureData = picture.getPictureData();
                        if (pictureData != null) {
                            String ext = pictureData.suggestFileExtension();
                            if (ext != null) {
                                images.add("image." + ext);
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
            }
        }
        return images;
    }

    private String toJsonArray(List<String> options) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) sb.append(",");
            String escaped = options.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(escaped).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 段落信息内部类
     */
    private static class ParagraphInfo {
        final String text;
        final List<String> images;

        ParagraphInfo(String text, List<String> images) {
            this.text = text;
            this.images = images;
        }
    }

    /**
     * 选项信息内部类
     */
    private static class OptionInfo {
        final String label;
        final String text;
        final List<String> images;

        OptionInfo(String label, String text, List<String> images) {
            this.label = label;
            this.text = text;
            this.images = images;
        }
    }

    /**
     * 解析报告内部类：收集成功题目 + 失败原因
     * （部分失败策略：成功入库，失败丢弃并报告）
     */
    private static class ParseReport {
        final List<Question> questions = new ArrayList<>();
        final List<String> failedReasons = new ArrayList<>();
    }
}
