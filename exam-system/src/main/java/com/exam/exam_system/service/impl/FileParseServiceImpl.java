package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.AiDifficultyResult;
import com.exam.exam_system.dto.WordParseResult;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.AiDifficultyService;
import com.exam.exam_system.service.FileParseService;
import com.exam.exam_system.service.WordParseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileParseServiceImpl implements FileParseService {

    private final WordParseService wordParseService;
    private final AiDifficultyService aiDifficultyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WordParseResult parseFile(MultipartFile file, Long courseId, Long creatorId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名无效");
        }
        String ext = getExtension(filename).toLowerCase();

        return switch (ext) {
            case "docx" -> parseWord(file, courseId, creatorId);
            case "xlsx", "xls" -> parseExcel(file, courseId, creatorId);
            case "pdf" -> parsePdf(file, courseId, creatorId);
            case "json" -> parseJsonFile(file, courseId, creatorId);
            case "txt" -> parseTxt(file, courseId, creatorId);
            case "png", "jpg", "jpeg", "gif", "bmp" -> handleImage(file);
            default -> throw new IllegalArgumentException("不支持的文件格式：" + ext + "，支持：docx/xlsx/xls/pdf/json/txt/png/jpg/jpeg");
        };
    }

    @Override
    public WordParseResult parseJson(String jsonContent, Long courseId, Long creatorId) {
        try {
            List<Map<String, Object>> list = objectMapper.readValue(jsonContent, new TypeReference<List<Map<String, Object>>>() {});
            return convertJsonListToQuestions(list, courseId, creatorId);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败：" + e.getMessage(), e);
        }
    }

    // ============ Word ============
    private WordParseResult parseWord(MultipartFile file, Long courseId, Long creatorId) {
        return wordParseService.parseWord(file, courseId, creatorId);
    }

    // ============ TXT 文本 ============
    private WordParseResult parseTxt(MultipartFile file, Long courseId, Long creatorId) {
        try (InputStream is = file.getInputStream()) {
            String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // 去除可能的 BOM 头
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            return wordParseService.parseText(content, courseId, creatorId);
        } catch (IOException e) {
            throw new IllegalArgumentException("TXT 文件读取失败：" + e.getMessage(), e);
        }
    }

    // ============ Excel ============
    private WordParseResult parseExcel(MultipartFile file, Long courseId, Long creatorId) {
        List<Question> questions = new ArrayList<>();
        List<String> failedReasons = new ArrayList<>();
        String title = "";

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            int sheetCount = workbook.getNumberOfSheets();
            // 记录上一个工作表的列映射，供无表头的工作表沿用（多工作表常见「首个带表头、其余纯数据」）
            int[] prevCols = null;
            List<Integer> prevOptionCols = null;

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet == null) continue;
                String sheetName = "第" + (s + 1) + "个工作表" +
                        (sheet.getSheetName() == null || sheet.getSheetName().isEmpty() ? "" : "「" + sheet.getSheetName() + "」");

                int contentCol = -1, typeCol = -1, optionsCol = -1, answerCol = -1, analysisCol = -1, scoreCol = -1;
                // 分列选项列索引（表头为 A/B/C/D/E/F 各自一列）
                List<Integer> optionCols = new ArrayList<>();
                boolean hasHeader = false;
                int startRow = 0;

                if (sheet.getPhysicalNumberOfRows() > 0) {
                    Row headerRow = sheet.getRow(0);
                    if (headerRow != null) {
                        // 尝试识别表头，确定各列位置
                        for (Cell cell : headerRow) {
                            String header = getCellString(cell).trim();
                            switch (header) {
                                case "题干", "题目", "content" -> contentCol = cell.getColumnIndex();
                                case "题型", "type", "questionType" -> typeCol = cell.getColumnIndex();
                                case "选项", "options" -> optionsCol = cell.getColumnIndex();
                                case "答案", "answer" -> answerCol = cell.getColumnIndex();
                                case "解析", "analysis" -> analysisCol = cell.getColumnIndex();
                                case "分值", "score" -> scoreCol = cell.getColumnIndex();
                            }
                            // 分列选项：A/B/C/D/E/F/H 等单字母列
                            if (header.matches("^[A-Ha-h]$")) {
                                optionCols.add(cell.getColumnIndex());
                            }
                        }
                    }
                    if (contentCol != -1) {
                        // 找到了「题干/题目」列，判定该工作表有表头
                        hasHeader = true;
                        Collections.sort(optionCols);
                        startRow = 1; // 跳过表头行
                    } else if (prevCols != null) {
                        // 无表头：沿用上一工作表的列映射，从第一行开始读数据
                        contentCol = prevCols[0];
                        typeCol = prevCols[1];
                        optionsCol = prevCols[2];
                        answerCol = prevCols[3];
                        analysisCol = prevCols[4];
                        scoreCol = prevCols[5];
                        optionCols = new ArrayList<>(prevOptionCols);
                        startRow = 0;
                    } else {
                        // 首个工作表无表头：按默认列：第0列题干、第1列题型……
                        contentCol = 0;
                        typeCol = 1;
                        optionsCol = 2;
                        answerCol = 3;
                        analysisCol = 4;
                        scoreCol = 5;
                        startRow = 0;
                    }
                }

                for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int rowNum = r + 1; // Excel 从 1 开始
                    String content = getCellString(row.getCell(contentCol)).trim();
                    if (content.isEmpty()) {
                        failedReasons.add(sheetName + " 第 " + rowNum + " 行：题干为空，已跳过");
                        continue;
                    }

                    if (title.isEmpty()) {
                        title = content.length() > 50 ? content.substring(0, 50) : content;
                    }

                    Question q = new Question();
                    q.setCourseId(courseId);
                    q.setCreatorId(creatorId);
                    q.setAuditStatus(0);
                    q.setContent(content);

                    if (typeCol >= 0) {
                        String typeStr = getCellString(row.getCell(typeCol)).trim();
                        q.setQuestionType(parseQuestionType(typeStr));
                    } else {
                        q.setQuestionType(1);
                    }

                    if (optionsCol >= 0) {
                        String optsStr = getCellString(row.getCell(optionsCol)).trim();
                        if (!optsStr.isEmpty()) {
                            // 支持：["A","B"] 或 A|B|C 或 A、B、C
                            q.setOptions(parseOptionsCell(optsStr, q.getQuestionType()));
                        }
                    } else if (!optionCols.isEmpty()) {
                        // 分列选项：A / B / C / D / E / F 各占一列，按顺序合并
                        List<String> optList = new ArrayList<>();
                        for (Integer oc : optionCols) {
                            String v = getCellString(row.getCell(oc));
                            v = v == null ? "" : v.trim();
                            // 去掉前缀 "A. " / "A、" / "A) " / "A、"
                            v = v.replaceAll("^\\s*[A-F][\\.．、)）]\\s*", "");
                            if (!v.isEmpty()) optList.add(v);
                        }
                        if (!optList.isEmpty()) {
                            try { q.setOptions(objectMapper.writeValueAsString(optList)); }
                            catch (Exception ignored) {}
                        }
                    }

                    if (answerCol >= 0) {
                        q.setAnswer(getCellString(row.getCell(answerCol)).trim());
                    }

                    if (analysisCol >= 0) {
                        q.setAnalysis(getCellString(row.getCell(analysisCol)).trim());
                    }

                    if (scoreCol >= 0) {
                        String scoreStr = getCellString(row.getCell(scoreCol)).trim();
                        if (!scoreStr.isEmpty()) {
                            try {
                                q.setScore(BigDecimal.valueOf(Double.parseDouble(scoreStr)));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    if (q.getScore() == null || q.getScore().compareTo(BigDecimal.ZERO) <= 0) q.setScore(BigDecimal.valueOf(2.0));
                    // 难度由 AI 在全部解析完成后统一评估
                    q.setDifficulty(null);
                    questions.add(q);
                }

                // 记录本工作表列映射，供无表头的后续工作表沿用
                if (hasHeader) {
                    prevCols = new int[]{contentCol, typeCol, optionsCol, answerCol, analysisCol, scoreCol};
                    prevOptionCols = optionCols;
                }
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Excel 解析失败：" + e.getMessage(), e);
        }

        // AI 批量评估难度（用户在 Excel 中未提供难度列时，统一 AI 评估）
        applyAiDifficulty(questions);

        WordParseResult result = new WordParseResult();
        result.setTitle(title);
        result.setQuestions(questions);
        result.setSuccessCount(questions.size());
        result.setFailedCount(failedReasons.size());
        result.setFailedReasons(failedReasons);
        return result;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Integer parseQuestionType(String type) {
        if (type == null || type.isEmpty()) return 1;
        type = type.toLowerCase();
        if (type.contains("单选") || type.equals("1") || type.equals("single")) return 1;
        if (type.contains("多选") || type.equals("2") || type.equals("multiple")) return 2;
        if (type.contains("判断") || type.equals("3") || type.contains("tf") || type.equals("truefalse")) return 3;
        if (type.contains("填空") || type.equals("4") || type.contains("blank") || type.contains("fill")) return 4;
        if (type.contains("简答") || type.contains("问答") || type.equals("5") || type.contains("essay")) return 5;
        try {
            int v = Integer.parseInt(type);
            if (v >= 1 && v <= 5) return v;
        } catch (NumberFormatException ignored) {}
        return 1;
    }

    private String parseOptionsCell(String optsStr, Integer type) {
        try {
            // 格式1：JSON数组
            if (optsStr.startsWith("[") && optsStr.endsWith("]")) {
                List<String> list = objectMapper.readValue(optsStr, new TypeReference<List<String>>() {});
                return objectMapper.writeValueAsString(list);
            }
        } catch (Exception ignored) {}

        // 格式2：A|B|C 或 A、B、C 或 A/B/C
        String[] parts;
        if (optsStr.contains("|")) parts = optsStr.split("\\|");
        else if (optsStr.contains("、")) parts = optsStr.split("、");
        else if (optsStr.contains("/")) parts = optsStr.split("/");
        else if (optsStr.contains("\n")) parts = optsStr.split("\n");
        else parts = new String[] { optsStr };

        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            // 去掉前缀 "A. " / "A、" / "A) "
            s = s.replaceAll("^\\s*[A-F][\\.．、)）]\\s*", "");
            if (!s.isEmpty()) list.add(s);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ============ PDF ============
    private WordParseResult parsePdf(MultipartFile file, Long courseId, Long creatorId) {
        try (InputStream is = file.getInputStream();
             PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            // 复用 Word 文本解析逻辑
            return wordParseService.parseText(text, courseId, creatorId);
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF 解析失败：" + e.getMessage(), e);
        }
    }

    // ============ JSON ============
    private WordParseResult parseJsonFile(MultipartFile file, Long courseId, Long creatorId) {
        try (InputStream is = file.getInputStream()) {
            List<Map<String, Object>> list = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            return convertJsonListToQuestions(list, courseId, creatorId);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 文件解析失败：" + e.getMessage(), e);
        }
    }

    private WordParseResult convertJsonListToQuestions(List<Map<String, Object>> list, Long courseId, Long creatorId) {
        List<Question> questions = new ArrayList<>();
        List<String> failedReasons = new ArrayList<>();
        String title = "";
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = list.get(i);
            String content = toStr(m.get("content"));
            if (content.isEmpty()) {
                failedReasons.add("第 " + (i + 1) + " 条 JSON：content 字段为空，已跳过");
                continue;
            }

            Question q = new Question();
            Long cid = toLong(m.get("courseId"));
            q.setCourseId(cid != null ? cid : courseId);
            q.setCreatorId(creatorId);
            q.setContent(content);
            Integer type = toInteger(m.get("questionType"));
            q.setQuestionType(type != null ? type : 1);
            q.setAnswer(toStr(m.get("answer")));
            q.setAnalysis(toStr(m.get("analysis")));
            // 用户在 JSON 中显式指定了难度则优先使用，否则交给 AI 评估（设 null）
            Integer diff = toInteger(m.get("difficulty"));
            q.setDifficulty(diff);
            Double sc = toDouble(m.get("score"));
            q.setScore(sc != null ? BigDecimal.valueOf(sc) : BigDecimal.valueOf(2.0));

            Object options = m.get("options");
            if (options != null) {
                try {
                    q.setOptions(objectMapper.writeValueAsString(options));
                } catch (Exception ignored) {}
            }
            questions.add(q);

            if (i == 0 && !q.getContent().isEmpty()) {
                title = q.getContent().length() > 50 ? q.getContent().substring(0, 50) : q.getContent();
            }
        }
        // 对 JSON 中未显式设置难度的题目，用 AI 批量评估补充
        applyAiDifficultyIfMissing(questions);

        WordParseResult result = new WordParseResult();
        result.setTitle(title);
        result.setQuestions(questions);
        result.setSuccessCount(questions.size());
        result.setFailedCount(failedReasons.size());
        result.setFailedReasons(failedReasons);
        return result;
    }

    // ============ 图片 ============
    private WordParseResult handleImage(MultipartFile file) {
        throw new IllegalArgumentException("图片文件需要 OCR 文字识别，暂不支持自动解析。请先将图片转换为 Word/Excel/JSON/PDF 文本格式后再导入。");
    }

    // ============ 工具方法 ============
    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0) return "";
        return filename.substring(idx + 1);
    }

    private String toStr(Object o) { return o == null ? "" : o.toString(); }
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }
    private Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }
    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }

    // ==================== AI 难度评估辅助方法 ====================

    /**
     * 对所有题目调用 AI 难度评估（用于 Excel / Word / PDF 解析后）。
     * 异常时不中断解析，兜底设为中等难度。
     */
    private void applyAiDifficulty(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return;
        try {
            List<AiDifficultyResult> results = aiDifficultyService.evaluateBatch(questions);
            for (int i = 0; i < questions.size() && i < results.size(); i++) {
                AiDifficultyResult r = results.get(i);
                if (r != null && r.getDifficulty() != null) {
                    questions.get(i).setDifficulty(r.getDifficulty());
                }
            }
        } catch (Exception e) {
            log.warn("AI 难度批量评估失败，兜底使用中等难度：{}", e.getMessage());
        }
        // 兜底：任何仍为 null 的难度统一设为中等
        for (Question q : questions) {
            if (q.getDifficulty() == null) q.setDifficulty(2);
        }
    }

    /**
     * 仅对 difficulty == null 的题目调用 AI 评估（用于 JSON 导入时
     * 用户已显式设置难度的题目保持不变）。
     */
    private void applyAiDifficultyIfMissing(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return;
        // 找出需要评估的题目及其索引
        List<Integer> indexes = new ArrayList<>();
        List<Question> toEvaluate = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            if (q.getDifficulty() == null) {
                indexes.add(i);
                toEvaluate.add(q);
            }
        }
        if (toEvaluate.isEmpty()) return;
        try {
            List<AiDifficultyResult> results = aiDifficultyService.evaluateBatch(toEvaluate);
            for (int i = 0; i < indexes.size() && i < results.size(); i++) {
                AiDifficultyResult r = results.get(i);
                if (r != null && r.getDifficulty() != null) {
                    questions.get(indexes.get(i)).setDifficulty(r.getDifficulty());
                }
            }
        } catch (Exception e) {
            log.warn("AI 难度补充评估失败，兜底使用中等难度：{}", e.getMessage());
        }
        // 兜底
        for (Integer idx : indexes) {
            Question q = questions.get(idx);
            if (q.getDifficulty() == null) q.setDifficulty(2);
        }
    }
}
