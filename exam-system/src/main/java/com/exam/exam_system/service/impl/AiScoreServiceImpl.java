package com.exam.exam_system.service.impl;

import com.exam.exam_system.config.AiProperties;
import com.exam.exam_system.dto.AiScoreResult;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamAnswer;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.mapper.ExamReviewMapper;
import com.exam.exam_system.service.AiScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主观题 AI 预评分服务实现
 *
 * <p>评分通道：
 * <ol>
 *     <li>已配置 ai.api-key：把「题干 + 参考答案（评分要点）+ 满分 + 学生作答」拼成提示词，
 *         调用 OpenAI 兼容 Chat Completions 接口，要求模型按 0.5 分档给出 0~满分 的得分，
 *         并输出给学生看的点评、给教师看的评分依据、命中/缺失的得分点与置信度；</li>
 *     <li>未配置 api-key 或调用失败：降级为本地规则引擎，基于：
 *         <ul>
 *             <li>得分点覆盖率：把参考答案切成评分要点，用 2-gram（中文）/ 词覆盖（英文）判断学生是否命中；</li>
 *             <li>文字相似度：学生作答与参考答案的字符 2-gram Dice 系数；</li>
 *             <li>作答完整度：学生作答长度与参考答案长度的比值（防止「一句话糊弄」拿高分）。</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p>未作答的题直接记 0 分并给出提示语，不再消耗大模型调用。
 * 批量预评分（整卷待阅题）只发起 1 次大模型请求，避免逐题调用导致超时。
 */
@Service
public class AiScoreServiceImpl implements AiScoreService {

    /** 单次批量预评分最多题数（整卷预评场景一般为 3~8 道主观题） */
    private static final int MAX_BATCH = 10;

    /** 学生作答送入大模型的最大长度，防止超长作答撑爆上下文 */
    private static final int MAX_ANSWER_CHARS = 1500;

    /** 参考答案切分后的评分要点数上限 */
    private static final int MAX_POINTS = 8;

    /** 未取到试题满分时的估算满分（并在 warning 中提示教师） */
    private static final BigDecimal FALLBACK_MAX_SCORE = new BigDecimal("10");

    /** 本地引擎置信度：规则命中可解释但不如大模型准，提示教师复核 */
    private static final BigDecimal LOCAL_CONFIDENCE = new BigDecimal("0.55");

    private static final String LOCAL_TIP =
            "未配置 ai.api-key，本次为本地规则引擎预评分（配置后可启用大模型评分）";

    /** 系统提示词：约束大模型角色、评分尺度与输出格式 */
    private static final String SYSTEM_PROMPT =
            "你是一名严谨、公正的高校阅卷教师，负责按评分标准为主观题打分。\n" +
            "评分规则：\n" +
            "1. 得分必须在 0 到满分之间，按 0.5 分档递进，保留 1 位小数；\n" +
            "2. 以【参考答案/评分要点】为唯一评分依据：要点答全给满分，部分覆盖按比例给分，答错或未作答给 0 分；\n" +
            "3. 学生使用同义表述、语言不同但意思正确时同样给分，不因表述口语化或字数少而扣分；\n" +
            "4. comment 是给学生看的简短点评（60 字以内，指出亮点与不足，不要出现「AI」「模型」「系统」等字样）；\n" +
            "5. reason 是给教师看的评分依据（80 字以内）；\n" +
            "6. hits 列出学生答到的评分要点，missing 列出缺失的评分要点，每条 20 字以内，最多 5 条；\n" +
            "7. confidence 是 0~1 的置信度，把握不足时给低值，便于教师重点复核；\n" +
            "8. 只输出 JSON，不要输出 Markdown 代码块、注释或任何多余说明。";

    @Autowired
    private ExamReviewMapper reviewMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private AiProperties aiProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 对外入口 ====================

    @Override
    public AiScoreResult score(Long answerId, Long operatorId, Integer operatorRole) {
        ExamAnswer answer = loadChecked(answerId, operatorId, operatorRole);
        return scoreOne(answer);
    }

    @Override
    public List<AiScoreResult> scoreBatch(List<Long> answerIds, Long operatorId, Integer operatorRole) {
        if (answerIds == null || answerIds.isEmpty()) {
            throw new RuntimeException("请至少选择一道题进行 AI 预评分");
        }
        // 去重 + 限制单次题数 + 权限校验
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : answerIds) {
            if (id != null) distinct.add(id);
            if (distinct.size() >= MAX_BATCH) break;
        }
        List<ExamAnswer> answers = new ArrayList<>();
        for (Long id : distinct) {
            answers.add(loadChecked(id, operatorId, operatorRole));
        }
        return scoreBatchInternal(answers);
    }

    // ==================== 评分主流程 ====================

    /** 单题评分：配置了 key 且学生有作答时走大模型，否则走本地规则 */
    private AiScoreResult scoreOne(ExamAnswer answer) {
        BigDecimal max = resolveMax(answer);
        String maxTip = maxTip(answer);
        if (!llmConfigured()) {
            return localScore(answer, max, joinTips(maxTip, LOCAL_TIP));
        }
        if (isBlank(answer.getUserAnswer())) {
            return localScore(answer, max, maxTip);
        }
        try {
            String raw = callLlm(buildSinglePrompt(answer, max), 800);
            return fromJson(answer, max, parseJsonSafely(raw), maxTip);
        } catch (Exception e) {
            return localScore(answer, max, joinTips(maxTip, "大模型调用失败，已降级本地规则引擎：" + brief(e)));
        }
    }

    /** 批量评分：未作答的题本地记 0 分，其余合并为 1 次大模型请求，失败则整批降级本地规则 */
    private List<AiScoreResult> scoreBatchInternal(List<ExamAnswer> answers) {
        Map<Long, AiScoreResult> resultMap = new HashMap<>();
        List<ExamAnswer> needLlm = new ArrayList<>();
        boolean configured = llmConfigured();

        for (ExamAnswer answer : answers) {
            BigDecimal max = resolveMax(answer);
            String maxTip = maxTip(answer);
            if (!configured || isBlank(answer.getUserAnswer())) {
                resultMap.put(answer.getId(),
                        localScore(answer, max, joinTips(maxTip, configured ? null : LOCAL_TIP)));
            } else {
                needLlm.add(answer);
            }
        }

        if (!needLlm.isEmpty()) {
            Map<Long, JsonNode> nodeMap = null;
            String failure = null;
            try {
                nodeMap = callLlmForBatch(needLlm);
            } catch (Exception e) {
                failure = "大模型调用失败，已降级本地规则引擎：" + brief(e);
            }
            for (ExamAnswer answer : needLlm) {
                BigDecimal max = resolveMax(answer);
                String maxTip = maxTip(answer);
                JsonNode node = nodeMap == null ? null : nodeMap.get(answer.getId());
                if (node == null) {
                    resultMap.put(answer.getId(), localScore(answer, max,
                            joinTips(maxTip, failure != null ? failure : "大模型未返回该题评分，已降级本地规则引擎")));
                } else {
                    resultMap.put(answer.getId(), fromJson(answer, max, node, maxTip));
                }
            }
        }

        // 保持与入参一致的顺序返回
        List<AiScoreResult> ordered = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            AiScoreResult r = resultMap.get(answer.getId());
            if (r != null) ordered.add(r);
        }
        return ordered;
    }

    // ==================== 大模型通道 ====================

    /** 单题提示词 */
    private String buildSinglePrompt(ExamAnswer answer, BigDecimal max) {
        StringBuilder sb = new StringBuilder("请为下面这道主观题评分：\n");
        appendQuestion(sb, answer, max);
        sb.append("\n请严格按 {\"score\": 数字, \"comment\": \"给学生看的点评\", \"reason\": \"给教师看的评分依据\", ")
          .append("\"hits\": [\"命中的评分要点\"], \"missing\": [\"缺失的评分要点\"], \"confidence\": 0~1} 的 JSON 格式输出。");
        return sb.toString();
    }

    /** 批量提示词：一次请求多题，按序号回填，省去逐题往返 */
    private String buildBatchPrompt(List<ExamAnswer> answers, List<BigDecimal> maxScores) {
        StringBuilder sb = new StringBuilder("请为下面 ").append(answers.size()).append(" 道主观题分别评分：\n");
        for (int i = 0; i < answers.size(); i++) {
            sb.append("--- 第 ").append(i + 1).append(" 题 ---\n");
            appendQuestion(sb, answers.get(i), maxScores.get(i));
        }
        sb.append("\n请严格按 {\"items\": [{\"index\": 1, \"score\": 数字, \"comment\": \"给学生看的点评\", ")
          .append("\"reason\": \"给教师看的评分依据\", \"hits\": [\"要点\"], \"missing\": [\"要点\"], \"confidence\": 0~1}]} ")
          .append("的 JSON 格式输出，index 必须与题号一一对应，不要遗漏任何一题。");
        return sb.toString();
    }

    /** 拼装单题题目信息 */
    private void appendQuestion(StringBuilder sb, ExamAnswer answer, BigDecimal max) {
        sb.append("【题型】").append(typeText(answer.getQuestionType())).append('\n');
        sb.append("【题干】").append(empty(answer.getContent())).append('\n');
        sb.append("【参考答案/评分要点】").append(empty(answer.getCorrectAnswer())).append('\n');
        sb.append("【满分】").append(max.toPlainString()).append(" 分\n");
        String userAnswer = answer.getUserAnswer();
        sb.append("【学生作答】")
          .append(isBlank(userAnswer) ? "（未作答）" : truncate(userAnswer, MAX_ANSWER_CHARS))
          .append('\n');
    }

    /** 批量评分：返回 answerId → 该题 JSON 结果 */
    private Map<Long, JsonNode> callLlmForBatch(List<ExamAnswer> answers) throws Exception {
        List<BigDecimal> maxScores = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            maxScores.add(resolveMax(answer));
        }
        String raw = callLlm(buildBatchPrompt(answers, maxScores), 2400);
        JsonNode root = parseJsonSafely(raw);
        JsonNode items = root.path("items");
        Map<Long, JsonNode> map = new HashMap<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                int index = item.path("index").asInt(0);
                if (index >= 1 && index <= answers.size()) {
                    map.put(answers.get(index - 1).getId(), item);
                }
            }
        } else if (root.isArray() && root.size() == answers.size()) {
            // 兼容模型直接返回数组的情况
            for (int i = 0; i < answers.size(); i++) {
                map.put(answers.get(i).getId(), root.path(i));
            }
        }
        return map;
    }

    /** 调用 OpenAI 兼容 Chat Completions 接口，返回 choices[0].message.content */
    private String callLlm(String userPrompt, int maxTokens) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", aiProperties.getModel());
        payload.put("messages", messages);
        payload.put("temperature", 0.2);   // 低温度：评分结果更稳定、可复现
        payload.put("max_tokens", maxTokens);
        payload.put("response_format", Map.of("type", "json_object"));

        String body = objectMapper.writeValueAsString(payload);
        String url = aiProperties.getBaseUrl();
        if (!url.endsWith("/")) url = url + "/";
        url = url + "chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(aiProperties.getTimeoutSeconds(), 15)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("大模型返回内容为空");
        }
        return content.asText();
    }

    /** 把大模型返回的单项 JSON 转成评分结果 */
    private AiScoreResult fromJson(ExamAnswer answer, BigDecimal max, JsonNode json, String warning) {
        AiScoreResult result = newResult(answer, max, "llm", aiProperties.getModel(), warning);
        // 先按满分夹取，再按 0.5 分档取整，最后再夹一次：避免满分非 0.5 整数倍时取整后超分
        BigDecimal score = clamp(roundToHalf(clamp(decimal(json, "score"), max)), max);
        result.setScore(score);
        result.setComment(text(json, "comment", ""));
        result.setReason(text(json, "reason", ""));
        result.setHits(textList(json, "hits"));
        result.setMissing(textList(json, "missing"));
        BigDecimal confidence = clampConfidence(decimal(json, "confidence"));
        result.setConfidence(confidence == null ? new BigDecimal("0.80") : confidence);
        if (isBlank(result.getComment())) {
            result.setComment("本题建议得分 " + display(result.getScore()) + "/" + display(max) + " 分，请核对要点覆盖情况。");
        }
        return result;
    }

    // ==================== 本地规则引擎通道 ====================

    /**
     * 本地规则评分：
     * 有评分要点时 得分率 = 0.65×要点覆盖率 + 0.20×文字相似度 + 0.15×作答完整度；
     * 参考答案无法切出要点时退化为 0.80×相似度 + 0.20×完整度。
     *
     * <p>包级可见，便于单元测试直接验证（不依赖 Spring 容器与外部大模型）。
     */
    AiScoreResult localScore(ExamAnswer answer, BigDecimal max, String warning) {
        AiScoreResult result = newResult(answer, max, "local", null, warning);
        result.setConfidence(LOCAL_CONFIDENCE);

        String user = strip(answer.getUserAnswer());
        String ref = strip(answer.getCorrectAnswer());
        List<String> points = extractPoints(answer.getCorrectAnswer());

        if (user.isEmpty()) {
            result.setScore(BigDecimal.ZERO);
            result.setConfidence(new BigDecimal("1.00"));
            result.setMissing(points);
            result.setComment("本题未作答，不得分。建议对照参考答案梳理答题要点，避免会做的题空着。");
            result.setReason("学生作答为空，直接记 0 分。");
            return result;
        }

        List<String> hits = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String point : points) {
            if (covers(point, user)) hits.add(point);
            else missing.add(point);
        }

        double coverage = points.isEmpty() ? -1 : (double) hits.size() / points.size();
        double similarity = diceSimilarity(user, ref);
        double lengthRatio = ref.isEmpty() ? 0.5
                : Math.min(1.0, (double) user.length() / Math.max(1.0, ref.length() * 0.6));

        double ratio = coverage < 0
                ? 0.80 * similarity + 0.20 * lengthRatio
                : 0.65 * coverage + 0.20 * similarity + 0.15 * lengthRatio;

        BigDecimal score = clamp(roundToHalf(clamp(max.multiply(BigDecimal.valueOf(ratio)), max)), max);
        result.setScore(score);
        result.setHits(hits);
        result.setMissing(missing);
        result.setComment(buildLocalComment(points, hits, missing, similarity, score, max));
        result.setReason(buildLocalReason(points, hits, similarity, user.length(), ref.length(),
                answer.getCorrectAnswer() == null || answer.getCorrectAnswer().isBlank()));
        return result;
    }

    /** 学生可见点评 */
    private String buildLocalComment(List<String> points, List<String> hits, List<String> missing,
                                     double similarity, BigDecimal score, BigDecimal max) {
        StringBuilder sb = new StringBuilder();
        if (points.isEmpty()) {
            sb.append("作答与参考答案的契合度约 ").append(pct(similarity)).append("%");
        } else {
            sb.append("本题共 ").append(points.size()).append(" 个评分要点，你答到 ").append(hits.size()).append(" 个");
            if (!hits.isEmpty()) {
                sb.append("（").append(joinShort(hits, 3)).append("）");
            }
        }
        sb.append("，建议得分 ").append(display(score)).append("/").append(display(max)).append(" 分。");
        if (!missing.isEmpty()) {
            sb.append("可补充：").append(joinShort(missing, 2)).append("。");
        } else if (!points.isEmpty()) {
            sb.append("要点覆盖完整，继续保持。");
        }
        return sb.toString();
    }

    /** 教师可见评分依据 */
    private String buildLocalReason(List<String> points, List<String> hits, double similarity,
                                    int userLen, int refLen, boolean noReference) {
        StringBuilder sb = new StringBuilder("本地规则引擎：");
        if (noReference) {
            sb.append("该题未维护参考答案，仅按作答完整度与相似度估算，请人工复核");
        } else if (points.isEmpty()) {
            sb.append("参考答案无法切分评分要点，仅按文字相似度（").append(pct(similarity)).append("%）估算");
        } else {
            sb.append("命中 ").append(hits.size()).append("/").append(points.size()).append(" 个评分要点，")
              .append("文字相似度 ").append(pct(similarity)).append("%");
        }
        sb.append("；学生作答 ").append(userLen).append(" 字，参考答案 ").append(refLen).append(" 字。");
        return sb.toString();
    }

    // ==================== 评分要点匹配 ====================

    /** 把参考答案切成评分要点：按换行/分句/序号切分；句子过长时再按逗号细分 */
    private List<String> extractPoints(String reference) {
        if (isBlank(reference)) return Collections.emptyList();
        List<String> points = splitPoints(reference, false);
        // 单一长句时按逗号细分，避免「一刀切」导致覆盖率只有 0% 或 100%
        if (points.size() <= 2 && reference.length() > 40) {
            List<String> finer = splitPoints(reference, true);
            if (finer.size() > points.size()) points = finer;
        }
        if (points.size() > MAX_POINTS) {
            points = new ArrayList<>(points.subList(0, MAX_POINTS));
        }
        return points;
    }

    private List<String> splitPoints(String reference, boolean splitComma) {
        String regex = splitComma
                ? "[\\n\\r；;。!？?，,]|\\s*[（(]\\d{1,2}[)）]\\s*|\\s*\\d{1,2}[、.．]\\s*"
                : "[\\n\\r；;。!？?]|\\s*[（(]\\d{1,2}[)）]\\s*|\\s*\\d{1,2}[、.．]\\s*";
        Set<String> unique = new LinkedHashSet<>();
        for (String piece : reference.split(regex)) {
            String point = cleanPoint(piece);
            if (point.length() >= 2) unique.add(point);
            if (unique.size() >= MAX_POINTS) break;
        }
        return new ArrayList<>(unique);
    }

    /** 去掉要点里的序号前缀与首尾标点 */
    private String cleanPoint(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceFirst("^[\\(（]?[0-9一二三四五六七八九十①②③④⑤⑥⑦⑧⑨⑩]{1,2}[\\)）]?[、.．:：]?\\s*", "");
        s = s.replaceAll("^[\\p{P}\\p{S}\\s]+", "");
        s = s.replaceAll("[\\p{P}\\p{S}\\s]+$", "");
        return s.trim();
    }

    /** 判断学生作答是否覆盖某个要点：短要点看包含，长要点看 2-gram/词覆盖率是否过半 */
    private boolean covers(String point, String user) {
        String p = strip(point);
        if (p.isEmpty()) return false;
        if (p.length() <= 4) return user.contains(p);
        if (isMostlyAscii(p)) {
            List<String> words = asciiWords(p);
            if (words.isEmpty()) return user.contains(p);
            int hit = 0;
            for (String w : words) {
                if (user.contains(w)) hit++;
            }
            return (double) hit / words.size() >= 0.6;
        }
        Set<String> grams = bigrams(p);
        if (grams.isEmpty()) return user.contains(p);
        int hit = 0;
        for (String g : grams) {
            if (user.contains(g)) hit++;
        }
        return (double) hit / grams.size() >= 0.6;
    }

    /** 字符 2-gram Dice 相似度，用于衡量整体作答与参考答案的接近程度 */
    private double diceSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> ga = bigrams(a);
        Set<String> gb = bigrams(b);
        if (ga.isEmpty() || gb.isEmpty()) return 0;
        int inter = 0;
        for (String g : ga) {
            if (gb.contains(g)) inter++;
        }
        return 2.0 * inter / (ga.size() + gb.size());
    }

    private static Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s.length() < 2) {
            if (!s.isEmpty()) set.add(s);
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private static boolean isMostlyAscii(String s) {
        int ascii = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 128) ascii++;
        }
        return ascii > s.length() * 0.7;
    }

    private static List<String> asciiWords(String s) {
        List<String> words = new ArrayList<>();
        for (String w : s.split("\\s+")) {
            if (w.length() >= 3) words.add(w);
        }
        return words;
    }

    /** 去掉空白与标点（\p{P} 标点、\p{S} 符号，均为 Unicode 属性），便于做包含/相似度比较 */
    private static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\p{P}\\p{S}\\s]+", "").toLowerCase();
    }

    // ==================== 公共小工具 ====================

    /** 加载答题明细并做权限校验（教师只能评分自己创建考试的主观题） */
    private ExamAnswer loadChecked(Long answerId, Long operatorId, Integer operatorRole) {
        if (answerId == null) {
            throw new RuntimeException("答题ID不能为空");
        }
        ExamAnswer answer = reviewMapper.findDetailById(answerId);
        if (answer == null) {
            throw new RuntimeException("答题记录不存在");
        }
        if (operatorRole != null && operatorRole == 1) {
            Exam exam = examMapper.selectById(answer.getExamId());
            if (exam != null && !exam.getCreatorId().equals(operatorId)) {
                throw new RuntimeException("无权批阅该答题");
            }
        }
        return answer;
    }

    private boolean llmConfigured() {
        return aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank();
    }

    private BigDecimal resolveMax(ExamAnswer answer) {
        BigDecimal max = answer.getQuestionScore();
        if (max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
            return FALLBACK_MAX_SCORE;
        }
        return max;
    }

    /** 满分缺失时的提示（试题未在 paper_question 中维护分值） */
    private String maxTip(ExamAnswer answer) {
        BigDecimal max = answer.getQuestionScore();
        if (max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
            return "未取到该题满分，已按 " + FALLBACK_MAX_SCORE.toPlainString() + " 分估算，请核对试卷分值";
        }
        return null;
    }

    private AiScoreResult newResult(ExamAnswer answer, BigDecimal max, String source,
                                    String model, String warning) {
        AiScoreResult result = new AiScoreResult();
        result.setAnswerId(answer.getId());
        result.setMaxScore(max);
        result.setSource(source);
        result.setModel(model);
        result.setWarning(warning);
        return result;
    }

    /** 得分不超过满分、不低于 0，并按 0.5 分档取整 */
    private static BigDecimal roundToHalf(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.multiply(new BigDecimal("2")).setScale(0, RoundingMode.HALF_UP)
                .divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal max) {
        if (value == null) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(max) > 0) return max;
        return value;
    }

    private static BigDecimal clampConfidence(BigDecimal value) {
        if (value == null) return null;
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        if (v.isNumber()) {
            try {
                return v.decimalValue();
            } catch (Exception ignored) {
                // 落到下面的字符串解析
            }
        }
        try {
            String s = v.asText().trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field, String def) {
        if (node == null) return def;
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return def;
        String s = v.asText();
        if (s == null || s.isBlank()) return def;
        return s.trim();
    }

    private static List<String> textList(JsonNode node, String field) {
        List<String> list = new ArrayList<>();
        if (node == null) return list;
        JsonNode v = node.path(field);
        if (v.isArray()) {
            for (JsonNode item : v) {
                String s = item.asText();
                if (s != null && !s.isBlank()) list.add(s.trim());
            }
        } else if (v.isValueNode()) {
            String s = v.asText();
            if (s != null && !s.isBlank()) list.add(s.trim());
        }
        return list;
    }

    /** 尝试从模型输出中解析 JSON（大模型可能包裹在 ```json ... ``` 中） */
    private JsonNode parseJsonSafely(String raw) {
        try {
            if (raw == null) return objectMapper.readTree("{}");
            String s = raw.trim();
            if (s.startsWith("```")) {
                int first = s.indexOf('\n');
                int last = s.lastIndexOf("```");
                if (first > 0 && last > first) s = s.substring(first + 1, last).trim();
            }
            int objStart = s.indexOf('{');
            int arrStart = s.indexOf('[');
            if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
                int end = s.lastIndexOf('}');
                if (end > objStart) s = s.substring(objStart, end + 1);
            } else if (arrStart >= 0) {
                int end = s.lastIndexOf(']');
                if (end > arrStart) s = s.substring(arrStart, end + 1);
            }
            return objectMapper.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException("大模型评分结果解析失败");
        }
    }

    private static String joinTips(String a, String b) {
        if (isBlank(a)) return isBlank(b) ? null : b;
        if (isBlank(b)) return a;
        return a + "；" + b;
    }

    private static String joinShort(List<String> items, int limit) {
        List<String> parts = new ArrayList<>();
        for (String item : items) {
            if (parts.size() >= limit) break;
            parts.add(truncate(item, 14));
        }
        return String.join("、", parts);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String brief(Exception e) {
        String msg = e.getMessage();
        return truncate(msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg.replaceAll("\\s+", " "), 60);
    }

    private static String pct(double ratio) {
        return String.valueOf(Math.round(Math.max(0, ratio) * 100));
    }

    /** 统一保留 1 位小数，避免出现科学计数法 */
    private static String display(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String empty(String s) {
        return isBlank(s) ? "-" : s;
    }

    private static String typeText(Integer type) {
        if (type == null) return "主观题";
        return switch (type) {
            case 4 -> "填空题";
            case 5 -> "简答题";
            case 6 -> "名词解释";
            default -> "主观题";
        };
    }
}
