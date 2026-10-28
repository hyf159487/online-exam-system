package com.exam.exam_system.service.impl;

import com.exam.exam_system.config.AiProperties;
import com.exam.exam_system.dto.AiDifficultyResult;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.AiDifficultyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * AI 题目难度评估服务实现
 *
 * <p>评估策略：
 * <ol>
 *     <li>已配置 ai.api-key：调用 OpenAI 兼容 Chat Completions 接口，由大模型综合判断；</li>
 *     <li>未配置 api-key 或调用失败：降级为本地规则启发式引擎，基于以下维度加权评分：
 *         <ul>
 *             <li>题型（简答/多选通常更难）</li>
 *             <li>题干长度与复杂度（长题干、含嵌套条件通常更难）</li>
 *             <li>关键词（"简述""分析""综合""推导"→偏难；"以下哪个""是""不是"→偏易）</li>
 *             <li>选项数量（多选题选项多、迷惑项相似度高→偏难）</li>
 *             <li>解析长度（解析越长通常题目越难）</li>
 *         </ul>
 *     </li>
 * </ol>
 */
@Service
public class AiDifficultyServiceImpl implements AiDifficultyService {

    /** 系统提示词：约束大模型只输出 JSON，便于解析 */
    private static final String SYSTEM_PROMPT =
            "你是一名经验丰富的考试命题专家。请根据题目信息（学科、题型、题干、选项、答案、解析）判断题目的难度等级。\n" +
            "难度等级定义：\n" +
            "  1=简单：基础概念题、记忆性题、单一知识点、直接判断；\n" +
            "  2=中等：需要简单分析、2-3个步骤、常见应用场景；\n" +
            "  3=困难：综合多个知识点、需要推导/计算/深度分析、容易混淆的选项、开放性问答。\n" +
            "必须严格输出 JSON 对象，不要输出任何 Markdown 标记或额外说明，格式为：\n" +
            "{\"difficulty\": 1|2|3, \"reason\": \"一句话说明理由\"}";

    @Autowired
    private AiProperties aiProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 启发式规则关键词库 ====================

    /** 偏难关键词（命中 +1 分） */
    private static final List<String> HARD_KEYWORDS = Arrays.asList(
            "简述", "论述", "分析", "综合", "推导", "证明", "计算", "设计",
            "为什么", "原因", "区别", "联系", "优缺点", "适用场景",
            "实现机制", "底层原理", "深入理解", "架构", "优化",
            "compare", "analyze", "discuss", "explain why", "derive"
    );

    /** 偏简单关键词（命中 -0.5 分） */
    private static final List<String> EASY_KEYWORDS = Arrays.asList(
            "以下哪个", "哪一个", "是", "不是", "是否", "对的是", "错的是",
            "正确的是", "错误的是", "下列", "关键字", "属于", "不属于",
            "默认", "基础", "基本", "概念", "定义",
            "which of the following", "is correct", "is incorrect", "what is"
    );

    /** 题干中出现表示"嵌套/多步骤"的标点或连接词 → +0.5 分 */
    private static final Pattern COMPLEX_PATTERN =
            Pattern.compile("(如果.*那么|若.*则|假设.*并且|不仅.*而且|不仅.*还|既.*又|与此同时|此外|因此|所以|→|=>|&amp;&amp;|\\|\\|)");

    /** 数字/公式/代码出现较多 → +0.5 分 */
    private static final Pattern CODE_OR_MATH_PATTERN =
            Pattern.compile("(\\{.*\\}|\\[.*\\]|<.*>|[a-zA-Z]\\([^)]*\\)|\\d+[\\.\\+\\-\\*\\/=<>]\\d+|System\\.|public |class |void |int |String |new )");

    // ==================== 对外接口 ====================

    @Override
    public AiDifficultyResult evaluate(Question question) {
        boolean configured = aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank();
        if (configured) {
            try {
                return evaluateSingleByLlm(question);
            } catch (Exception e) {
                // 大模型调用失败，降级为本地规则
                return evaluateByRules(question);
            }
        }
        return evaluateByRules(question);
    }

    @Override
    public List<AiDifficultyResult> evaluateBatch(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return Collections.emptyList();
        // 批量评估统一走本地规则启发式引擎，不依赖大模型：
        // 导入解析 / 重新评估全部难度等大批量场景，可避免调用大模型导致的超时、网络波动
        List<AiDifficultyResult> results = new ArrayList<>(questions.size());
        for (Question q : questions) {
            results.add(evaluateByRules(q));
        }
        return results;
    }

    // ==================== 大模型调用 ====================

    private AiDifficultyResult evaluateSingleByLlm(Question q) throws Exception {
        String prompt = buildSinglePrompt(q);
        String raw = callLlm(prompt);
        JsonNode json = parseJsonSafely(raw);
        int diff = clampDifficulty(json.path("difficulty").asInt(2));
        String reason = json.path("reason").asText("基于大模型综合评估");
        return AiDifficultyResult.ofLlm(diff, aiProperties.getModel(), reason);
    }

    private String callLlm(String userPrompt) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));
        message.put("model", aiProperties.getModel());
        message.put("messages", messages);
        message.put("temperature", 0.2);      // 低温度，结果更稳定
        message.put("max_tokens", 800);
        message.put("response_format", Map.of("type", "json_object")); // 请求 JSON 输出（兼容多数模型）

        String body = objectMapper.writeValueAsString(message);
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
            throw new RuntimeException("LLM HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("LLM 返回内容为空");
        }
        return content.asText();
    }

    /** 尝试从模型输出中解析 JSON（大模型可能偶尔包裹在 ```json ... ``` 中） */
    private JsonNode parseJsonSafely(String raw) throws Exception {
        if (raw == null) return objectMapper.createObjectNode();
        String s = raw.trim();
        // 去掉 ```json ... ``` 或 ``` ... ``` 包裹
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first > 0 && last > first) s = s.substring(first + 1, last).trim();
        }
        // 找到最外层 {} 或 []
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
    }

    // ==================== Prompt 构建 ====================

    private String buildSinglePrompt(Question q) {
        StringBuilder sb = new StringBuilder();
        sb.append("请评估以下题目的难度等级：\n");
        appendQuestion(sb, q, 1);
        sb.append("\n请严格按 {\"difficulty\": 数字, \"reason\": \"理由\"} 的 JSON 格式输出。");
        return sb.toString();
    }

    private void appendQuestion(StringBuilder sb, Question q, int idx) {
        sb.append("--- 第 ").append(idx).append("题 ---\n");
        sb.append("【学科】").append(empty(q.getCourseName())).append('\n');
        sb.append("【题型】").append(typeText(q.getQuestionType())).append('\n');
        sb.append("【题干】").append(empty(q.getContent())).append('\n');
        if (q.getOptions() != null && !q.getOptions().isBlank()) {
            sb.append("【选项】").append(q.getOptions()).append('\n');
        }
        sb.append("【参考答案】").append(empty(q.getAnswer())).append('\n');
        if (q.getAnalysis() != null && !q.getAnalysis().isBlank()) {
            sb.append("【解析】").append(q.getAnalysis()).append('\n');
        }
    }

    // ==================== 本地规则启发式引擎 ====================

    /**
     * 基于多维特征的难度启发式评分（0 ~ +∞ 范围），然后映射到 1/2/3：
     *   score < 0.8  → 1 简单
     *   0.8 ≤ score < 1.8 → 2 中等
     *   score ≥ 1.8  → 3 困难
     */
    AiDifficultyResult evaluateByRules(Question q) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        // —— 1. 题型权重（基础分） ——
        Integer type = q.getQuestionType();
        if (type == null) type = 1;
        switch (type) {
            case 3 -> { score += 0.3; reasons.add("判断题通常较简单"); }       // 判断
            case 1 -> { score += 0.8; reasons.add("单选题属中等基础"); }       // 单选
            case 4 -> { score += 1.1; reasons.add("填空题需精确记忆"); }       // 填空
            case 2 -> { score += 1.3; reasons.add("多选题易错选/漏选"); }      // 多选
            case 5 -> { score += 1.9; reasons.add("简答题需综合组织作答"); }   // 简答
            case 6 -> { score += 1.7; reasons.add("名词解释需准确概述概念"); }  // 名词解释
            default -> score += 1.0;
        }

        // —— 2. 题干长度 ——
        int contentLen = q.getContent() == null ? 0 : q.getContent().length();
        if (contentLen > 200) { score += 0.4; reasons.add("题干信息量较大"); }
        else if (contentLen > 100) { score += 0.2; }
        else if (contentLen < 25)  { score -= 0.3; reasons.add("题干简短直观"); }

        // —— 3. 难/易关键词命中 ——
        String text = (q.getContent() == null ? "" : q.getContent()).toLowerCase();
        for (String kw : HARD_KEYWORDS) {
            if (text.contains(kw.toLowerCase())) { score += 0.4; reasons.add("命中难词：" + kw); break; }
        }
        for (String kw : EASY_KEYWORDS) {
            if (text.contains(kw.toLowerCase())) { score -= 0.3; reasons.add("命中易词：" + kw); break; }
        }

        // —— 4. 复杂句式 / 嵌套条件 ——
        if (COMPLEX_PATTERN.matcher(q.getContent() == null ? "" : q.getContent()).find()) {
            score += 0.4;
            reasons.add("含多条件/嵌套逻辑");
        }

        // —— 5. 代码 / 数学公式 ——
        if (CODE_OR_MATH_PATTERN.matcher(q.getContent() == null ? "" : q.getContent()).find()) {
            score += 0.5;
            reasons.add("含代码或数学计算");
        }

        // —— 6. 选项数量（选项越多通常越难，仅对选择题） ——
        if ((type == 1 || type == 2) && q.getOptions() != null) {
            try {
                JsonNode arr = objectMapper.readTree(q.getOptions());
                if (arr.isArray()) {
                    int optCount = arr.size();
                    if (optCount >= 6) { score += 0.4; reasons.add("选项数≥6，干扰项多"); }
                    else if (optCount >= 5) { score += 0.2; }
                }
            } catch (Exception ignored) {}
        }

        // —— 7. 解析长度（解析越长通常题目越难） ——
        int analysisLen = q.getAnalysis() == null ? 0 : q.getAnalysis().length();
        if (analysisLen > 200) score += 0.3;
        else if (analysisLen > 100) score += 0.1;

        // —— 映射到 1/2/3 ——
        int difficulty;
        if (score < 0.8) difficulty = 1;
        else if (score < 1.8) difficulty = 2;
        else difficulty = 3;

        String reasonText = reasons.isEmpty()
                ? "基于题型与题干特征的启发式评估"
                : String.join("；", reasons.subList(0, Math.min(reasons.size(), 3)));
        return AiDifficultyResult.ofLocal(difficulty, reasonText);
    }

    // ==================== 小工具 ====================

    private static int clampDifficulty(int d) {
        if (d < 1) return 1;
        if (d > 3) return 3;
        return d;
    }

    private static String typeText(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "单选";
            case 2 -> "多选";
            case 3 -> "判断";
            case 4 -> "填空";
            case 5 -> "简答";
            case 6 -> "名词解释";
            default -> "未知";
        };
    }

    private static String empty(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
