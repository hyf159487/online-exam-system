package com.exam.exam_system.service.impl;

import com.exam.exam_system.config.AiProperties;
import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.mapper.WrongQuestionMapper;
import com.exam.exam_system.service.AiExplainService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 题目讲解服务实现
 *
 * <p>讲解场景覆盖两类，均围绕「一道题」生成个性化讲解：
 * <ol>
 *     <li>错题讲解：基于错题记录（含学生当时的错误答案 / 做错次数）；</li>
 *     <li>自由刷题「背题模式」讲解：基于题库题目（可无作答，预习/背题场景）。</li>
 * </ol>
 *
 * <p>讲解来源策略：
 * <ol>
 *     <li>已配置 ai.api-key：拼装题目上下文 → 调用 OpenAI 兼容 Chat Completions 接口生成讲解；</li>
 *     <li>未配置 api-key 或调用失败：降级为本地规则解析引擎（按题型模板生成结构化讲解），
 *         保证离线 / 无 key 环境下功能依然可用可演示。</li>
 * </ol>
 */
@Service
public class AiExplainServiceImpl implements AiExplainService {

    /** 系统提示词：约束大模型角色与输出格式（完整讲解 / 追问两种场景通用） */
    private static final String SYSTEM_PROMPT =
            "你是一名经验丰富、耐心细致的课程辅导老师。学生正在学习【题目背景】里的这道题（可能在错题巩固，也可能在背题/预习模式），请始终围绕这道题进行辅导。\n" +
            "1. 当学生请求「完整讲解」这道题时，请依次说明：本题考查的核心知识点；正确的解题思路与完整解析；若提供了学生的作答或错因请结合分析错因，否则补充该题的关键记忆点与常见易错点；最后给出学习建议。请使用 Markdown 格式、中文输出，正文控制在 500 字以内。\n" +
            "2. 当学生是在追问某个选项、某个知识点，要求换个角度讲解或出几道同类题巩固时，请只针对学生当前问题简洁、准确地回答（Markdown、中文），不要再重复整道题的完整讲解。\n" +
            "请结合学生的实际情况耐心作答，语气亲切、条理清晰。";

    /** 一键生成讲解时的默认开场提问 */
    private static final String DEFAULT_FIRST_QUESTION =
            "请帮我讲解这道题：说明考查的核心知识点，给出正确的解题思路与完整解析，结合我提交的错误答案分析错因（若未作答则补充记忆要点与易错点），最后给出易错点与学习建议。";

    /** 本地引擎不支持追问时的引导文案 */
    private static final String LOCAL_FOLLOW_UP_TIP =
            "\n\n---\n> 本地规则引擎只支持一次性讲解，暂不支持追问。配置大模型 API Key（application.properties 的 ai.api-key）后，即可与 AI 就本题连续对话。";

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private AiProperties aiProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 对外入口 ====================

    @Override
    public AiExplainResult explain(Long userId, Long wrongQuestionId) {
        WrongQuestion w = wrongQuestionMapper.findDetailByIdAndUser(wrongQuestionId, userId);
        AiExplainResult result = new AiExplainResult();
        result.setWrongQuestionId(wrongQuestionId);
        if (w == null) {
            result.setSource("local");
            result.setContent("未找到该错题，可能已被移出错题本。");
            return result;
        }
        return chatWithContext(toContext(w), DEFAULT_FIRST_QUESTION, null);
    }

    @Override
    public AiExplainResult chat(Long userId, Long wrongQuestionId,
                                String question, List<AiChatRequest.Message> history) {
        WrongQuestion w = wrongQuestionMapper.findDetailByIdAndUser(wrongQuestionId, userId);
        AiExplainResult result = new AiExplainResult();
        result.setWrongQuestionId(wrongQuestionId);
        if (w == null) {
            result.setSource("local");
            result.setContent("未找到该错题，可能已被移出错题本。");
            return result;
        }
        return chatWithContext(toContext(w), question, history);
    }

    @Override
    public AiExplainResult explainQuestion(Long questionId, String userAnswer) {
        AiContext ctx = buildContextFromQuestion(questionId, userAnswer);
        if (ctx == null) {
            AiExplainResult result = new AiExplainResult();
            result.setSource("local");
            result.setContent("题目不存在或尚未通过审核，暂时无法生成讲解。");
            return result;
        }
        return chatWithContext(ctx, DEFAULT_FIRST_QUESTION, null);
    }

    @Override
    public AiExplainResult chatQuestion(Long questionId, String userAnswer,
                                        String question, List<AiChatRequest.Message> history) {
        AiContext ctx = buildContextFromQuestion(questionId, userAnswer);
        if (ctx == null) {
            AiExplainResult result = new AiExplainResult();
            result.setSource("local");
            result.setContent("题目不存在或尚未通过审核，暂时无法生成讲解。");
            return result;
        }
        return chatWithContext(ctx, question, history);
    }

    // ==================== 题目上下文装配 ====================

    /** 讲解所需的最小题目上下文（错题 detail 与题库题目共用） */
    private static class AiContext {
        final String courseName;
        final Integer questionType;
        final String content;
        final String options;
        final String userAnswer;
        final String correctAnswer;
        final String analysis;
        final Integer wrongCount;

        AiContext(String courseName, Integer questionType, String content, String options,
                  String userAnswer, String correctAnswer, String analysis, Integer wrongCount) {
            this.courseName = courseName;
            this.questionType = questionType;
            this.content = content;
            this.options = options;
            this.userAnswer = userAnswer;
            this.correctAnswer = correctAnswer;
            this.analysis = analysis;
            this.wrongCount = wrongCount;
        }
    }

    /** 错题记录 → 题目上下文 */
    private AiContext toContext(WrongQuestion w) {
        return new AiContext(w.getCourseName(), w.getQuestionType(), w.getContent(), w.getOptions(),
                w.getUserAnswer(), w.getCorrectAnswer(), w.getAnalysis(), w.getWrongCount());
    }

    /** 题库题目 → 题目上下文（校验题目存在且已审核通过） */
    private AiContext buildContextFromQuestion(Long questionId, String userAnswer) {
        if (questionId == null) return null;
        Question q = questionMapper.selectById(questionId);
        if (q == null || q.getAuditStatus() == null || q.getAuditStatus() != 1) return null;
        return new AiContext(q.getCourseName(), q.getQuestionType(), q.getContent(), q.getOptions(),
                userAnswer, q.getAnswer(), q.getAnalysis(), null);
    }

    // ==================== 核心讲解流程 ====================

    /** 统一的讲解/对话核心：错题与背题模式共用 */
    private AiExplainResult chatWithContext(AiContext ctx, String question,
                                            List<AiChatRequest.Message> history) {
        AiExplainResult result = new AiExplainResult();
        String realQuestion = (question == null || question.isBlank())
                ? DEFAULT_FIRST_QUESTION : question.trim();
        boolean followUp = history != null && !history.isEmpty();
        boolean configured = aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank();

        if (!configured) {
            // 未配置大模型：本地规则只支持一次性讲解
            result.setSource("local");
            result.setContent(buildLocalExplain(ctx) + (followUp ? LOCAL_FOLLOW_UP_TIP : ""));
            return result;
        }

        // 组装对话：system(角色+题目背景) + 历史(截取最近若干轮) + 本次问题
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system",
                    "content", SYSTEM_PROMPT + "\n\n【题目背景】\n" + buildContext(ctx)));
            if (followUp) {
                // 防止上下文超长：只保留最近 12 条历史消息（约 6 轮问答）
                int from = Math.max(0, history.size() - 12);
                for (AiChatRequest.Message m : history.subList(from, history.size())) {
                    String role = m.getRole();
                    String content = m.getContent();
                    if (role == null || content == null || content.isBlank()) continue;
                    if (!"user".equals(role) && !"assistant".equals(role)) continue;
                    messages.add(Map.of("role", role, "content", content));
                }
            }
            messages.add(Map.of("role", "user", "content", realQuestion));

            result.setSource("llm");
            result.setModel(aiProperties.getModel());
            result.setContent(callLlm(messages));
            return result;
        } catch (Exception e) {
            // 大模型调用失败（超时/网络/鉴权）时降级，不让功能中断
            result.setSource("local");
            result.setContent(buildLocalExplain(ctx) + (followUp ? LOCAL_FOLLOW_UP_TIP : ""));
            return result;
        }
    }

    // ==================== Prompt 拼装 ====================

    /** 把题目上下文拼装成 system 消息里的题目背景（每次对话始终携带，让模型始终围绕本题） */
    private String buildContext(AiContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("【学科】").append(empty(ctx.courseName)).append("\n");
        sb.append("【题型】").append(typeText(ctx.questionType)).append("\n");
        sb.append("【题干】").append(empty(ctx.content)).append("\n");
        if (ctx.options != null && !ctx.options.isBlank()) {
            sb.append("【选项】").append(ctx.options).append("\n");
        }
        sb.append("【学生的作答/错误答案】").append(empty(ctx.userAnswer)).append("\n");
        sb.append("【正确答案】").append(empty(ctx.correctAnswer)).append("\n");
        if (ctx.analysis != null && !ctx.analysis.isBlank()) {
            sb.append("【标准解析（供参考）】").append(ctx.analysis).append("\n");
        }
        if (ctx.wrongCount != null) {
            sb.append("【学生已连续做错次数】").append(ctx.wrongCount).append("\n");
        }
        return sb.toString();
    }

    // ==================== 大模型调用 ====================

    /** 调用 OpenAI 兼容 Chat Completions 接口，返回 choices[0].message.content */
    private String callLlm(List<Map<String, String>> messages) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("model", aiProperties.getModel());
        message.put("messages", messages);
        message.put("temperature", 0.7);
        message.put("max_tokens", 1500);

        String body = objectMapper.writeValueAsString(message);
        String url = aiProperties.getBaseUrl();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
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

    // ==================== 本地规则降级 ====================

    /** 本地规则解析引擎：按题型模板生成结构化讲解（不依赖外网） */
    private String buildLocalExplain(AiContext ctx) {
        String type = typeText(ctx.questionType);
        String correct = empty(ctx.correctAnswer);
        String userAnswer = ctx.userAnswer;
        boolean hasAnswer = userAnswer != null && !userAnswer.isBlank();

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(type).append("题讲解\n\n");

        // 第一步：题目回顾
        sb.append("**题目**：").append(empty(ctx.content)).append("\n\n");

        // 第二步：正确答案
        sb.append("**正确答案**：`").append(correct).append("`");
        if (hasAnswer) {
            sb.append("  \n你提交的答案：`").append(userAnswer).append("`");
        } else {
            sb.append("  \n（当前为背题/预习，暂无作答记录）");
        }
        sb.append("\n\n");

        // 第三步：按题型的解析模板
        switch (ctx.questionType == null ? 0 : ctx.questionType) {
            case 1:
                sb.append("### 解题思路\n")
                  .append("1. 单选题需先理解题干考查的知识点，逐项分析每个选项，排除明显错误的干扰项；\n")
                  .append("2. 若选项涉及概念辨析，回到教材定义，抓住关键词（如“总是”“一定”等绝对化表述通常是陷阱）；\n")
                  .append("3. 不确定时可先用排除法缩小范围，再对比剩余选项的差异。\n\n");
                break;
            case 2:
                sb.append("### 解题思路\n")
                  .append("1. 多选题要求“全选对”，缺选、多选都不得分，务必逐项独立判断，不要凭整体感觉；\n")
                  .append("2. 可先把有把握正确的选项确定下来，再逐个核对剩余选项；\n")
                  .append("3. 注意答案可能存在多个正确项，避免“只选一个最有把握的”这种漏选失分。\n\n");
                break;
            case 3:
                sb.append("### 解题思路\n")
                  .append("1. 判断题重点抓命题中的绝对化词语（一定、必然、全部等），多数与教材表述不一致；\n")
                  .append("2. 将题目表述与教材原话逐句对照，注意偷换概念、以偏概全、颠倒因果等常见陷阱；\n")
                  .append("3. 标准答案 `T` 表示正确、`F` 表示错误。\n\n");
                break;
            case 4:
                sb.append("### 解题思路\n")
                  .append("1. 填空题考查对知识点的精确记忆，作答时注意专业术语的准确写法、单位与大小写；\n")
                  .append("2. 结合题干上下文推断所填内容，答案通常来自教材中的核心概念或结论；\n")
                  .append("3. 若为计算题，务必检查计算过程和结果精度。\n\n");
                break;
            case 5:
                sb.append("### 解题思路\n")
                  .append("1. 简答题需要分点作答、条理清晰，先答结论再展开依据；\n")
                  .append("2. 结合题干关键词回顾相关知识点，尽量使用教材中的专业表述；\n")
                  .append("3. 该题为人工阅卷题，注意回答要点完整、逻辑通顺。\n\n");
                break;
            case 6:
                sb.append("### 解题思路\n")
                  .append("1. 名词解释需先给出该概念的本质定义（属+种差），再适当补充内涵与外延；\n")
                  .append("2. 回答时抓住核心特征词，避免口语化，尽量使用教材中的规范表述；\n")
                  .append("3. 该题为人工阅卷题，注意要点完整、条理清晰。\n\n");
                break;
            default:
                sb.append("### 解题思路\n")
                  .append("1. 先理解题目考查的核心知识点；\n")
                  .append("2. 结合教材内容逐步推导出正确答案；\n")
                  .append("3. 比对错因，针对性巩固。\n\n");
        }

        // 第四步：错因分析 / 背题引导
        sb.append("### 针对你的情况\n");
        if (!hasAnswer) {
            sb.append("你还没有作答，正处于背题/预习阶段。建议：先通读题干与选项 → 记住正确答案与解析要点 → ")
              .append("然后遮住答案，尝试自己复述或默写，检验是否真正掌握，再进入下一题。\n\n");
        } else if (correct.equalsIgnoreCase(userAnswer)) {
            sb.append("你提交的答案与正确答案一致，可能属于答题规范问题或系统判分差异，建议确认答案格式后重做一次。\n\n");
        } else {
            sb.append("你的答案与正确答案不一致，常见原因：知识点掌握不牢固、审题不清、");
            if (ctx.wrongCount != null && ctx.wrongCount >= 2) {
                sb.append("以及这道题多次出错（已错 ").append(ctx.wrongCount).append(" 次），建议重点标记并反复巩固。");
            } else {
                sb.append("建议对照教材重点复习该知识点。");
            }
            sb.append("\n\n");
        }

        // 第五步：标准解析（如有）
        if (ctx.analysis != null && !ctx.analysis.isBlank()) {
            sb.append("### 参考解析\n").append(ctx.analysis).append("\n\n");
        }

        // 第六步：学习建议
        sb.append("### 学习建议\n")
          .append("- 将本题加入重点复习清单，隔天重做一次检验是否真正掌握；\n")
          .append("- 举一反三：围绕同一知识点换不同说法再练 1~2 道同类型题；\n")
          .append("- 若多次出错，可请教老师或在教材中找出对应章节精读。");
        return sb.toString();
    }

    // ==================== 小工具 ====================

    private String typeText(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "单选";
            case 2: return "多选";
            case 3: return "判断";
            case 4: return "填空";
            case 5: return "简答";
            case 6: return "名词解释";
            default: return "未知";
        }
    }

    private String empty(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
