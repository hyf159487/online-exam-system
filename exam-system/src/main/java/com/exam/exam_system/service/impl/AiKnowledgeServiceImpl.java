package com.exam.exam_system.service.impl;

import com.exam.exam_system.config.AiProperties;
import com.exam.exam_system.dto.KnowledgeMatchResult;
import com.exam.exam_system.entity.KnowledgePoint;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.AiKnowledgeService;
import com.exam.exam_system.service.KnowledgePointService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 知识点匹配服务实现
 *
 * <p>策略：按学科分组。每组先从知识库取该学科全部知识点，再由大模型（或本地规则）
 * 逐题从给定知识点中挑选最合适的一个；无合适项则 knowledgeId 留空。
 */
@Service
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeServiceImpl.class);

    /** 每次大模型调用最多处理的题目数（防止超长 prompt 超 token） */
    private static final int LLM_BATCH = 20;

    /** 本地规则——知识点名称在题目文本中完全出现时给出的加分权重 */
    private static final double FULL_MATCH_SCORE = 5.0;

    /** 本地规则——分数需达到该阈值才认为匹配成功 */
    private static final double MIN_RULE_SCORE = 0.6;

    /** 每道题最多推荐的知识点个数（1~3） */
    private static final int MAX_MATCH_PER_QUESTION = 3;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private KnowledgePointService knowledgePointService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<KnowledgeMatchResult> match(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeMatchResult> results = new ArrayList<>(questions.size());

        // 按学科分组：不同学科的知识点不同，需分别取知识点列表并独立匹配
        Map<Long, List<Question>> byCourse = new LinkedHashMap<>();
        for (Question q : questions) {
            byCourse.computeIfAbsent(q.getCourseId(), k -> new ArrayList<>()).add(q);
        }

        boolean llmConfigured = aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank();

        for (Map.Entry<Long, List<Question>> entry : byCourse.entrySet()) {
            Long courseId = entry.getKey();
            List<Question> group = entry.getValue();
            List<KnowledgePoint> kps = knowledgePointService.listByCourse(courseId, null);

            if (llmConfigured) {
                try {
                    results.addAll(matchByLlm(group, kps));
                    continue;
                } catch (Exception e) {
                    log.warn("AI 知识点匹配 LLM 调用失败，降级本地规则：{}", e.getMessage());
                }
            }
            results.addAll(matchByRules(group, kps));
        }
        return results;
    }

    // ==================== 大模型匹配 ====================

    private List<KnowledgeMatchResult> matchByLlm(List<Question> group, List<KnowledgePoint> kps) throws Exception {
        List<KnowledgeMatchResult> all = new ArrayList<>(group.size());
        for (int i = 0; i < group.size(); i += LLM_BATCH) {
            List<Question> batch = group.subList(i, Math.min(i + LLM_BATCH, group.size()));
            all.addAll(matchBatchByLlm(batch, kps));
        }
        return all;
    }

    private List<KnowledgeMatchResult> matchBatchByLlm(List<Question> batch, List<KnowledgePoint> kps) throws Exception {
        String prompt = buildMatchPrompt(batch, kps);
        String raw = callLlm(prompt);
        JsonNode root = parseJsonSafely(raw);
        JsonNode matches = root.path("matches");
        List<KnowledgeMatchResult> results = new ArrayList<>(batch.size());

        for (Question q : batch) {
            KnowledgeMatchResult r = new KnowledgeMatchResult();
            r.setQuestionId(q.getId());
            r.setSource("llm");
            r.setReason("大模型匹配");
            r.setKnowledgeIds(Collections.emptyList());
            r.setKnowledgeNames(Collections.emptyList());
            results.add(r);
        }

        // 按 questionId 填充结果（保持输入顺序；无法一一对应时保留默认 reason）
        if (matches.isArray()) {
            for (JsonNode m : matches) {
                Long qid = m.path("questionId").asLong(-1);
                String reason = m.path("reason").asText("");
                for (KnowledgeMatchResult r : results) {
                    if (r.getQuestionId() != null && r.getQuestionId().equals(qid)) {
                        fillMatchIds(r, m, kps);
                        if (reason != null && !reason.isBlank()) {
                            r.setReason(reason);
                        }
                        break;
                    }
                }
            }
        }
        return results;
    }

    /**
     * 从大模型返回的单条记录里解析 1~3 个知识点：
     * 优先取 knowledgeIds 数组，兼容旧版单值 knowledgeId；
     * 只保留当前学科知识库中存在（能反查名称）的 id，避免模型乱给编号。
     */
    private static void fillMatchIds(KnowledgeMatchResult r, JsonNode m, List<KnowledgePoint> kps) {
        List<Long> candidates = new ArrayList<>();
        JsonNode arr = m.path("knowledgeIds");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                if (!n.isNull() && n.asLong(-1) > 0) {
                    candidates.add(n.asLong());
                }
            }
        } else {
            JsonNode old = m.path("knowledgeId");
            if (!old.isMissingNode() && !old.isNull() && old.asLong(-1) > 0) {
                candidates.add(old.asLong());
            }
        }
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Long kid : candidates) {
            String nm = findName(kps, kid);
            if (nm == null) {
                continue; // 不属于该学科知识库，忽略
            }
            ids.add(kid);
            names.add(nm);
            if (ids.size() >= MAX_MATCH_PER_QUESTION) {
                break;
            }
        }
        r.setKnowledgeIds(ids);
        r.setKnowledgeNames(names);
        r.setKnowledgeId(ids.isEmpty() ? null : ids.get(0));
        r.setKnowledgeName(names.isEmpty() ? null : names.get(0));
    }

    private String buildMatchPrompt(List<Question> batch, List<KnowledgePoint> kps) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名题库知识点分类专家。下面给出某门课程下的全部知识点（编号：名称），以及若干道题目。\n");
        sb.append("请为每一道题，从『知识点列表』中选择最匹配的 1~3 个知识点，返回其知识点编号数组 knowledgeIds（按相关度从高到低排序）；");
        sb.append("若该题确实不属于任何给定知识点，则 knowledgeIds 填 []。\n");
        sb.append("必须严格输出 JSON，不要输出任何 Markdown 标记或额外说明，格式为：\n");
        sb.append("{\"matches\":[{\"questionId\":题目ID,\"knowledgeIds\":[知识点id,知识点id],\"reason\":\"一句话理由\"}]}\n\n");

        sb.append("【知识点列表】\n");
        if (kps == null || kps.isEmpty()) {
            sb.append("（该课程暂无知识点，所有题 knowledgeIds 一律为 []）\n");
        } else {
            for (KnowledgePoint kp : kps) {
                sb.append(kp.getId()).append(". ").append(kp.getPointName()).append('\n');
            }
        }

        sb.append("\n【题目列表】\n");
        for (Question q : batch) {
            sb.append("--- 题 (questionId=").append(q.getId()).append(") ---\n");
            sb.append("【题干】").append(empty(q.getContent())).append('\n');
            if (q.getOptions() != null && !q.getOptions().isBlank()) {
                sb.append("【选项】").append(q.getOptions()).append('\n');
            }
            sb.append("【答案】").append(empty(q.getAnswer())).append('\n');
            if (q.getAnalysis() != null && !q.getAnalysis().isBlank()) {
                sb.append("【解析】").append(q.getAnalysis()).append('\n');
            }
        }
        return sb.toString();
    }

    private String callLlm(String userPrompt) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是一名严谨的题库知识点归类专家，只输出合法的 JSON。"));
        messages.add(Map.of("role", "user", "content", userPrompt));
        message.put("model", aiProperties.getModel());
        message.put("messages", messages);
        message.put("temperature", 0.2);
        message.put("max_tokens", 2000);
        message.put("response_format", Map.of("type", "json_object"));

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

    private JsonNode parseJsonSafely(String raw) throws Exception {
        if (raw == null) return objectMapper.createObjectNode();
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
    }

    // ==================== 本地规则匹配（降级兜底） ====================

    private List<KnowledgeMatchResult> matchByRules(List<Question> questions, List<KnowledgePoint> kps) {
        List<KnowledgeMatchResult> results = new ArrayList<>(questions.size());
        for (Question q : questions) {
            KnowledgeMatchResult r = new KnowledgeMatchResult();
            r.setQuestionId(q.getId());
            r.setSource("local");

            List<Scored> top = topMatches(q, kps, MAX_MATCH_PER_QUESTION);
            if (!top.isEmpty()) {
                List<Long> ids = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (Scored s : top) {
                    ids.add(s.kp.getId());
                    names.add(s.kp.getPointName());
                }
                r.setKnowledgeIds(ids);
                r.setKnowledgeNames(names);
                r.setKnowledgeId(ids.get(0));
                r.setKnowledgeName(names.get(0));
                r.setReason(top.size() > 1
                        ? "基于关键词相似度匹配到 " + top.size() + " 个知识点（最高命中度 "
                        + String.format("%.2f", top.get(0).score) + "）"
                        : "基于关键词相似度匹配（命中度 " + String.format("%.2f", top.get(0).score) + "）");
            } else {
                r.setKnowledgeIds(Collections.emptyList());
                r.setKnowledgeNames(Collections.emptyList());
                r.setKnowledgeId(null);
                r.setKnowledgeName(null);
                r.setReason("未找到明显匹配的知识点");
            }
            results.add(r);
        }
        return results;
    }

    /** 候选匹配项（知识点 + 相似度分数） */
    private static class Scored {
        final KnowledgePoint kp;
        final double score;

        Scored(KnowledgePoint kp, double score) {
            this.kp = kp;
            this.score = score;
        }
    }

    /** 计算得分达到阈值的前 N 个知识点（按分数从高到低，默认推荐 1~3 个） */
    private static List<Scored> topMatches(Question q, List<KnowledgePoint> kps, int n) {
        if (kps == null || kps.isEmpty()) {
            return Collections.emptyList();
        }
        List<Scored> list = new ArrayList<>();
        for (KnowledgePoint kp : kps) {
            double s = score(kp, q);
            if (s >= MIN_RULE_SCORE) {
                list.add(new Scored(kp, s));
            }
        }
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        list.sort((a, b) -> Double.compare(b.score, a.score));
        return list.subList(0, Math.min(n, list.size()));
    }

    /**
     * 简单相似度打分：知识点名称在题目文本中完全出现→高分；否则逐字命中比例。
     */
    private static double score(KnowledgePoint kp, Question q) {
        String name = kp.getPointName() == null ? "" : kp.getPointName().trim();
        if (name.isEmpty()) return 0;
        String text = (empty(q.getContent()) + " " + empty(q.getAnswer()) + " "
                + (q.getAnalysis() == null ? "" : q.getAnalysis())).toLowerCase();
        if (text.contains(name.toLowerCase())) {
            return FULL_MATCH_SCORE;
        }
        String lowerName = name.toLowerCase();
        int hit = 0;
        for (char c : lowerName.toCharArray()) {
            if (c != ' ' && text.indexOf(c) >= 0) hit++;
        }
        return (double) hit / lowerName.length();
    }

    // ==================== 小工具 ====================

    private static String findName(List<KnowledgePoint> kps, Long id) {
        if (id == null || kps == null) return null;
        for (KnowledgePoint kp : kps) {
            if (id.equals(kp.getId())) return kp.getPointName();
        }
        return null;
    }

    private static String empty(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
