package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.config.AiProperties;
import com.exam.exam_system.dto.AiComposeMeta;
import com.exam.exam_system.dto.AiComposePlan;
import com.exam.exam_system.dto.AiComposeRequest;
import com.exam.exam_system.dto.PaperQuestionSaveRequest;
import com.exam.exam_system.entity.ExamPaper;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.service.AiComposeService;
import com.exam.exam_system.service.ExamPaperService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * AI 智能组卷服务实现
 *
 * <p>整体流程：
 * <ol>
 *     <li>读取试卷所属学科的「题库家底」（题型 × 难度档可用题量）；</li>
 *     <li>生成组卷方案：配置了 ai.api-key 由大模型生成，否则/失败时用本地规则引擎；</li>
 *     <li>方案与题库实际可用量对齐（总量压缩、难度档顺延），全过程记录提示信息；</li>
 *     <li>按方案抽题：重点知识点优先 + 知识点覆盖率最大化择优 + 不去重不重复；</li>
 *     <li>覆盖式写回试卷题目（复用 ExamPaperService.saveQuestions，含发布态/进行中考试保护）。</li>
 * </ol>
 */
@Service
public class AiComposeServiceImpl implements AiComposeService {

    /** 默认目标总分 */
    private static final BigDecimal DEFAULT_TOTAL_SCORE = new BigDecimal("100");
    /** 每题最小分值 */
    private static final BigDecimal MIN_UNIT_SCORE = new BigDecimal("0.5");
    /** 单次组卷题目上限（防止写出超长试卷） */
    private static final int MAX_QUESTIONS = 100;

    /** 组卷系统提示词：约束大模型只输出 JSON 方案 */
    private static final String SYSTEM_PROMPT = """
            你是一名资深的考试命题与组卷专家，负责为教师设计试卷结构（题型配比、难度分布、分值分配）。
            题型编号：1=单选题 2=多选题 3=判断题 4=填空题 5=简答题 6=名词解释。
            难度编号：1=简单 2=中等 3=困难。
            组卷要求（下列条件按优先级从高到低依次满足，前一条优先于后一条）：
             1) 【最高优先级｜目标总分】rules 的 Σ(count × score) 必须精确等于【目标总分】（允许 ±1 分）；
                分值凑不整时优先增减题目数量或微调单题分值，禁止输出与目标总分相差 5 分以上的方案；
             2) 【次优先级｜难度倾向】各难度档的题量占比需贴合【难度倾向】给出的参考占比；
             3) 【再次｜重点知识点】给出了重点考察知识点时，这些知识点的题量可适当提高；
             4) 【最后｜补充要求】教师在补充要求里提到的偏好（章节、题型、难度、避免重复等）尽量兼顾；
             5) 试卷结构符合常见考试习惯：客观题占基础分、主观题占拔高分；
             6) 每题分值取 0.5 的整数倍（如 1、1.5、2、2.5）；
             7) 严格遵守「题库可用题量」限制：
                - 任何规则的题数都不得超过对应题型+难度档的可用题数；
                - 题库可用题量为 0（未列出）的题型，禁止为其生成规则；
             8) difficulty 只能取 1、2、3（禁止用 0 或省略表示"不限难度"），请选择最贴合的难度档；
             9) 同一「题型+难度」只能出现一条规则；
            10) 题目应覆盖尽可能多的知识点，避免集中在单一知识点。
            输出前自检：Σ(count × score) 是否等于目标总分；不等则调整 count 或 score 后重新输出。
            必须严格输出 JSON 对象，不要输出 Markdown 标记或任何额外说明，格式为：
            {"strategy":"一句话说明本次组卷思路","rules":[{"questionType":1,"difficulty":2,"count":10,"score":2}]}
            """;

    /** 本地引擎：常见试卷结构的题型分值占比（按此比例分配目标总分） */
    private static final Map<Integer, Double> TYPE_RATIO = new LinkedHashMap<>();

    static {
        TYPE_RATIO.put(1, 0.40); // 单选
        TYPE_RATIO.put(3, 0.20); // 判断
        TYPE_RATIO.put(5, 0.20); // 简答
        TYPE_RATIO.put(2, 0.10); // 多选
        TYPE_RATIO.put(4, 0.10); // 填空
    }

    /** 本地引擎：各题型常见单题分值（用于反推题数） */
    private static final Map<Integer, BigDecimal> TYPE_UNIT_SCORE = new HashMap<>();

    static {
        TYPE_UNIT_SCORE.put(1, new BigDecimal("2"));
        TYPE_UNIT_SCORE.put(2, new BigDecimal("3"));
        TYPE_UNIT_SCORE.put(3, new BigDecimal("2"));
        TYPE_UNIT_SCORE.put(4, new BigDecimal("3"));
        TYPE_UNIT_SCORE.put(5, new BigDecimal("10"));
        TYPE_UNIT_SCORE.put(6, new BigDecimal("5"));
    }

    /** 难度倾向 → 简单/中等/困难 的题量占比 */
    private static final Map<Integer, double[]> DIFF_RATIO = new HashMap<>();

    static {
        DIFF_RATIO.put(1, new double[]{0.6, 0.3, 0.1}); // 偏基础
        DIFF_RATIO.put(2, new double[]{0.4, 0.4, 0.2}); // 均衡
        DIFF_RATIO.put(3, new double[]{0.2, 0.4, 0.4}); // 偏挑战
    }

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private ExamPaperService examPaperService;

    @Autowired
    private AiProperties aiProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 对外接口 ====================

    @Override
    public AiComposeMeta meta(Long paperId) {
        ExamPaper paper = examPaperService.getById(paperId);
        if (paper == null) {
            throw new BizException("试卷不存在（id=" + paperId + "）");
        }
        AiComposeMeta meta = new AiComposeMeta();
        meta.setCourseId(paper.getCourseId());
        meta.setCourseName(paper.getCourseName());
        meta.setAiEnabled(aiEnabled());
        meta.setModel(aiEnabled() ? aiProperties.getModel() : null);
        if (paper.getCourseId() == null) {
            return meta;
        }
        // 题型 / 难度档可用题量
        Map<Integer, int[]> capacity = buildCapacity(questionMapper.statByTypeAndLevel(paper.getCourseId()));
        List<AiComposeMeta.TypeStat> typeStats = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Integer, int[]> entry : new TreeMap<>(capacity).entrySet()) {
            int[] cap = entry.getValue();
            int sum = cap[1] + cap[2] + cap[3];
            if (sum <= 0) continue;
            AiComposeMeta.TypeStat ts = new AiComposeMeta.TypeStat();
            ts.setQuestionType(entry.getKey());
            ts.setQuestionTypeText(AiComposePlan.typeText(entry.getKey()));
            ts.setEasy(cap[1]);
            ts.setMedium(cap[2]);
            ts.setHard(cap[3]);
            ts.setTotal(sum);
            typeStats.add(ts);
            total += sum;
        }
        meta.setTypeStats(typeStats);
        meta.setTotalQuestions(total);
        // 知识点可用题量（供「重点知识点」多选）
        meta.setKnowledgeOptions(loadKnowledgeOptions(paper.getCourseId()));
        return meta;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiComposePlan compose(Long paperId, AiComposeRequest request) {
        AiComposeRequest req = request == null ? new AiComposeRequest() : request;
        ExamPaper paper = examPaperService.getById(paperId);
        if (paper == null) {
            throw new BizException("试卷不存在（id=" + paperId + "）");
        }
        if (paper.getCourseId() == null) {
            throw new BizException("试卷未关联学科，请先设置学科后再智能组卷");
        }
        // 已发布 / 有进行中考试占用 → 阻断（与随机组卷一致）
        examPaperService.checkComposable(paperId);

        BigDecimal target = (req.getTotalScore() != null && req.getTotalScore().signum() > 0)
                ? req.getTotalScore().setScale(1, RoundingMode.HALF_UP)
                : DEFAULT_TOTAL_SCORE;
        int preference = (req.getDifficultyPreference() != null
                && req.getDifficultyPreference() >= 1 && req.getDifficultyPreference() <= 3)
                ? req.getDifficultyPreference() : 2;
        List<Long> focusIds = req.getKnowledgeIds() == null ? List.of()
                : req.getKnowledgeIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<String> warnings = new ArrayList<>();

        // ① 题库家底
        Map<Integer, int[]> capacity = buildCapacity(questionMapper.statByTypeAndLevel(paper.getCourseId()));
        int totalAvailable = capacity.values().stream().mapToInt(c -> c[1] + c[2] + c[3]).sum();
        if (totalAvailable == 0) {
            throw new BizException("该学科暂无审核通过的题目，无法智能组卷，请先补充并审核题库");
        }
        List<AiComposeMeta.KnowledgeStat> knowledgeOptions = loadKnowledgeOptions(paper.getCourseId());
        Map<Long, String> knowledgeNames = knowledgeOptions.stream()
                .collect(Collectors.toMap(AiComposeMeta.KnowledgeStat::getKnowledgeId,
                        AiComposeMeta.KnowledgeStat::getKnowledgeName, (a, b) -> a, LinkedHashMap::new));

        // ② 生成组卷方案（大模型优先，失败/不可用 → 本地规则引擎兜底）
        String source = "local";
        Blueprint blueprint = null;
        List<Rule> finalRules = null;
        if (aiEnabled()) {
            try {
                Blueprint llm = llmBlueprint(paper, req, target, preference, capacity, focusIds, knowledgeNames);
                // 先与题库可用量对齐：方案规则全被裁掉时同样视为不可用，降级到本地引擎
                List<String> llmWarnings = new ArrayList<>();
                List<Rule> aligned = balance(llm.getRules(), capacity, req.getQuestionTypes(), llmWarnings);
                if (aligned.isEmpty()) {
                    warnings.add("大模型给出的方案与题库可用题量不匹配，已自动切换为本地规则引擎");
                } else {
                    blueprint = llm;
                    finalRules = aligned;
                    warnings.addAll(llmWarnings);
                    source = "llm";
                }
            } catch (Exception e) {
                warnings.add("大模型方案生成失败（" + brief(e.getMessage()) + "），已自动切换为本地规则引擎");
            }
        }
        if (blueprint == null) {
            blueprint = localBlueprint(req, target, preference, capacity, warnings);
            source = "local";
        }
        // 优先级④「补充要求」：大模型路径已写进提示词；本地引擎只能识别其中明确点名的知识点，其余语义无法理解
        if ("local".equals(source) && req.getRequirement() != null && !req.getRequirement().isBlank()) {
            List<Long> mentioned = focusFromRequirement(req.getRequirement(), knowledgeOptions);
            if (mentioned.isEmpty()) {
                warnings.add("当前为本地规则引擎（未配置 ai.api-key），无法理解补充要求的语义，"
                        + "仅按目标总分、难度倾向与知识点覆盖组卷");
            } else {
                List<Long> merged = new ArrayList<>(focusIds);
                List<String> names = new ArrayList<>();
                for (Long id : mentioned) {
                    if (!merged.contains(id)) merged.add(id);
                    names.add(knowledgeNames.getOrDefault(id, "知识点" + id));
                }
                focusIds = merged;
                warnings.add("本地规则引擎：补充要求中点到「" + String.join("、", names)
                        + "」，已作为重点知识点参与抽题；其余语义要求需启用大模型才能落实");
            }
        }

        // ③ 方案与题库可用量对齐（总量压缩 + 难度档顺延）
        if (finalRules == null) {
            finalRules = balance(blueprint.getRules(), capacity, req.getQuestionTypes(), warnings);
        }
        if (finalRules.isEmpty()) {
            throw new BizException("按当前要求无法抽到题目（题库可用题型不足），请放宽题型范围或补充题库");
        }
        // ④ 优先级①「目标总分」落地：抽题前先把方案计划总分咬到目标分（增减题数 → 微调单题分值）
        alignTotalScore(finalRules, capacity, preference, target, warnings);

        // ⑤ 抽题
        Set<Long> used = new HashSet<>();
        List<AiComposePlan.Item> items = new ArrayList<>();
        List<AiComposePlan.Rule> planRules = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Rule rule : finalRules) {
            BigDecimal unit = unitScoreOf(rule).setScale(1, RoundingMode.HALF_UP);
            List<Question> picked = pickQuestions(paper.getCourseId(), rule, focusIds, used, warnings);
            for (Question q : picked) {
                items.add(AiComposePlan.Item.of(q, unit));
                totalScore = totalScore.add(unit);
            }
            if (!picked.isEmpty()) {
                planRules.add(AiComposePlan.Rule.of(rule.getQuestionType(), rule.getDifficulty(),
                        rule.getCount(), picked.size(), unit));
            }
        }
        if (items.isEmpty()) {
            throw new BizException("题库可用题目不足，未能抽到任何题目，请先补充题库");
        }
        // ⑥ 优先级①兜底：题库题量不足导致抽到的分值低于计划时，按实际题目微调单题分值，保证目标总分命中
        alignItemsToTarget(items, target, warnings);
        totalScore = itemsScore(items);
        syncRuleScore(planRules, items);
        // 按题型 → 难度排序，保存后的题目顺序更符合试卷习惯
        items.sort(Comparator
                .comparingInt((AiComposePlan.Item i) -> i.getQuestionType() == null ? 99 : i.getQuestionType())
                .thenComparingInt(i -> i.getDifficulty() == null ? 2 : i.getDifficulty()));

        // ⑦ 覆盖式保存到试卷
        PaperQuestionSaveRequest saveReq = new PaperQuestionSaveRequest();
        saveReq.setPaperId(paperId);
        saveReq.setItems(items.stream().map(i -> {
            PaperQuestionSaveRequest.Item it = new PaperQuestionSaveRequest.Item();
            it.setQuestionId(i.getQuestionId());
            it.setScore(i.getScore());
            return it;
        }).collect(Collectors.toList()));
        examPaperService.saveQuestions(saveReq);

        // ⑧ 组装结果（方案说明 + 难度分布 + 题目清单）
        AiComposePlan plan = new AiComposePlan();
        plan.setSource(source);
        plan.setModel("llm".equals(source) ? aiProperties.getModel() : null);
        plan.setTargetScore(target);
        plan.setTotalScore(totalScore);
        plan.setQuestionCount(items.size());
        plan.setRules(planRules);
        plan.setItems(items);
        plan.setWarnings(warnings);

        Map<Integer, Integer> levelCount = new TreeMap<>();
        Map<Integer, BigDecimal> levelScore = new TreeMap<>();
        int focusHit = 0;
        Set<Long> coveredKnowledge = new HashSet<>();
        for (AiComposePlan.Item item : items) {
            int level = levelOf(item.getDifficulty());
            levelCount.merge(level, 1, Integer::sum);
            levelScore.merge(level, item.getScore() == null ? BigDecimal.ZERO : item.getScore(), BigDecimal::add);
            if (item.getKnowledgeId() != null) {
                coveredKnowledge.add(item.getKnowledgeId());
                if (focusIds.contains(item.getKnowledgeId())) focusHit++;
            }
        }
        for (Map.Entry<Integer, Integer> e : levelCount.entrySet()) {
            plan.getDifficultyStats().add(
                    AiComposePlan.DifficultyStat.of(e.getKey(), e.getValue(), levelScore.get(e.getKey())));
        }

        // 优先级②校验：实际难度分布与「难度倾向」偏差过大时说明原因
        checkDifficultyPreference(items, preference, warnings);

        // 方案说明：按「目标总分 > 难度倾向 > 重点知识点 > 补充要求」的口径汇报条件命中情况
        StringBuilder strategy = new StringBuilder("条件优先级：目标总分 > 难度倾向 > 重点知识点 > 补充要求。");
        strategy.append(blueprint.getStrategy() == null ? "智能组卷方案" : blueprint.getStrategy());
        strategy.append("；目标总分 ").append(plain(target)).append(" 分，实际 ").append(plain(totalScore))
                .append(" 分（偏差 ").append(plain(target.subtract(totalScore).abs())).append(" 分）");
        strategy.append("；难度分布 ");
        for (int level = 1; level <= 3; level++) {
            if (level > 1) strategy.append('、');
            int count = levelCount.getOrDefault(level, 0);
            strategy.append(AiComposePlan.difficultyText(level)).append(' ')
                    .append(items.isEmpty() ? 0 : Math.round(count * 100.0 / items.size())).append('%');
        }
        strategy.append("（倾向：").append(preferenceText(preference)).append("）");
        if (!focusIds.isEmpty()) {
            long percent = Math.round(focusHit * 100.0 / items.size());
            strategy.append("；重点知识点命中 ").append(focusHit).append(" 题（").append(percent).append("%）");
            if (percent < 50) {
                warnings.add("【重点知识点】重点知识点仅命中 " + focusHit + "/" + items.size() + " 题（" + percent
                        + "%），该知识点在题库中的可用题量不足，已用其他知识点题目补齐");
            }
        }
        if (coveredKnowledge.isEmpty()) {
            strategy.append("；该学科题目暂未关联知识点，已按题型与难度均衡抽题");
        } else {
            strategy.append("；共覆盖 ").append(coveredKnowledge.size()).append(" 个知识点");
        }
        plan.setStrategy(strategy.toString());
        return plan;
    }

    // ==================== 方案生成：大模型 / 本地规则 ====================

    /** 大模型组卷方案 */
    private Blueprint llmBlueprint(ExamPaper paper, AiComposeRequest req, BigDecimal target, int preference,
                                   Map<Integer, int[]> capacity, List<Long> focusIds,
                                   Map<Long, String> knowledgeNames) throws Exception {
        String userPrompt = buildComposePrompt(paper, req, target, preference, capacity, focusIds, knowledgeNames);
        String raw = callLlm(userPrompt);
        JsonNode json = parseJsonSafely(raw);
        Blueprint bp = new Blueprint();
        bp.setStrategy(json.path("strategy").asText("大模型综合题型、难度与知识点覆盖生成组卷方案"));
        List<Rule> rules = new ArrayList<>();
        for (JsonNode n : json.path("rules")) {
            int type = n.path("questionType").asInt(0);
            int level = n.path("difficulty").asInt(0);
            int count = n.path("count").asInt(0);
            BigDecimal score = n.path("score").isNumber() ? n.path("score").decimalValue() : null;
            if (type < 1 || type > 6 || count <= 0) continue;
            // 单题分值统一到 0.5 的整数倍（大模型偶尔给出 1.75、0 这类不好用的分值）
            if (score != null) {
                score = roundToHalf(score);
                if (score.compareTo(MIN_UNIT_SCORE) < 0) score = MIN_UNIT_SCORE;
            }
            if (level >= 1 && level <= 3) {
                rules.add(new Rule(type, level, count, score));
            } else {
                // 大模型用 0/缺省表示「不限难度」：按教师的难度倾向拆分到三档，避免退化成全中等题
                int[] perLevel = splitByRatio(count, DIFF_RATIO.getOrDefault(preference, DIFF_RATIO.get(2)));
                for (int l = 1; l <= 3; l++) {
                    if (perLevel[l] > 0) rules.add(new Rule(type, l, perLevel[l], score));
                }
            }
        }
        if (rules.isEmpty()) {
            throw new RuntimeException("大模型未返回有效的抽题规则");
        }
        bp.setRules(rules);
        return bp;
    }

    /**
     * 本地规则引擎组卷方案：
     * 按常见试卷结构（单选 40% / 判断 20% / 简答 20% / 多选 10% / 填空 10%）分配目标总分，
     * 按「题型默认单题分值」反推题数，再按难度倾向拆分为简单/中等/困难三档规则；
     * 题型范围可由教师指定，默认只挑题库里确实有题的题型。
     */
    private Blueprint localBlueprint(AiComposeRequest req, BigDecimal target, int preference,
                                     Map<Integer, int[]> capacity, List<String> warnings) {
        List<Integer> types = new ArrayList<>();
        if (req.getQuestionTypes() != null && !req.getQuestionTypes().isEmpty()) {
            types.addAll(req.getQuestionTypes().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        } else {
            for (Integer t : TYPE_RATIO.keySet()) {
                int[] cap = capacity.get(t);
                if (cap != null && cap[1] + cap[2] + cap[3] > 0) types.add(t);
            }
            // 比例表之外的题型（如名词解释）题库有题时也纳入，权重按剩余比例处理
            for (Map.Entry<Integer, int[]> e : new TreeMap<>(capacity).entrySet()) {
                if (!types.contains(e.getKey())) {
                    int[] cap = e.getValue();
                    if (cap[1] + cap[2] + cap[3] > 0) types.add(e.getKey());
                }
            }
        }
        if (types.isEmpty()) {
            throw new BizException("该学科题库无可用题型，无法智能组卷");
        }
        // 归一化题型权重
        double weightSum = types.stream()
                .mapToDouble(t -> TYPE_RATIO.getOrDefault(t, 0.08))
                .sum();
        List<Rule> rules = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        double[] diffRatio = DIFF_RATIO.getOrDefault(preference, DIFF_RATIO.get(2));
        for (int i = 0; i < types.size(); i++) {
            Integer type = types.get(i);
            double weight = TYPE_RATIO.getOrDefault(type, 0.08);
            // 最后一个题型用「目标总分 - 已分配」兜底，消除逐项四舍五入造成的偏差
            BigDecimal typeScore = (i == types.size() - 1)
                    ? target.subtract(allocated)
                    : target.multiply(BigDecimal.valueOf(weight / weightSum)).setScale(1, RoundingMode.HALF_UP);
            if (typeScore.signum() <= 0) continue;
            allocated = allocated.add(typeScore);

            BigDecimal unitScore = TYPE_UNIT_SCORE.getOrDefault(type, new BigDecimal("3"));
            int count = Math.max(1, typeScore.divide(unitScore, 0, RoundingMode.HALF_UP).intValue());
            BigDecimal score = typeScore.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
            if (score.compareTo(MIN_UNIT_SCORE) < 0) score = MIN_UNIT_SCORE;

            int[] perLevel = splitByRatio(count, diffRatio);
            for (int level = 1; level <= 3; level++) {
                if (perLevel[level] > 0) {
                    rules.add(new Rule(type, level, perLevel[level], score));
                }
            }
        }
        String prefText = preference == 1 ? "偏基础" : preference == 3 ? "偏挑战" : "均衡";
        String strategy = "本地规则引擎：按常见试卷结构分配题型分值（" + types.stream()
                .map(t -> AiComposePlan.typeText(t))
                .collect(Collectors.joining("、")) + "），难度按「" + prefText + "」分布（简单/中等/困难 ≈ "
                + Math.round(diffRatio[0] * 100) + "%/" + Math.round(diffRatio[1] * 100) + "%/"
                + Math.round(diffRatio[2] * 100) + "%），并结合题库可用量自动裁剪";
        return new Blueprint(strategy, rules);
    }

    /**
     * 把方案规则与题库实际可用量对齐：
     * ① 同一题型计划总量超过题库可用量 → 按比例压缩；
     * ② 某一难度档不足 → 顺延到相邻难度档补足，仍不足则减少题量并给出提示。
     */
    private List<Rule> balance(List<Rule> input, Map<Integer, int[]> capacity,
                               List<Integer> allowedTypes, List<String> warnings) {
        Map<Integer, List<Rule>> byType = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) return new ArrayList<>();
        for (Rule r : input) {
            if (r.getQuestionType() == null || r.getCount() == null || r.getCount() <= 0) continue;
            if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(r.getQuestionType())) continue;
            byType.computeIfAbsent(r.getQuestionType(), k -> new ArrayList<>()).add(r);
        }
        List<Rule> result = new ArrayList<>();
        int questionBudget = 0;
        for (Map.Entry<Integer, List<Rule>> entry : byType.entrySet()) {
            Integer type = entry.getKey();
            List<Rule> typeRules = entry.getValue();
            int[] cap = capacity.getOrDefault(type, new int[4]);
            int capTotal = cap[1] + cap[2] + cap[3];
            if (capTotal == 0) {
                warnings.add("「" + AiComposePlan.typeText(type) + "」题库暂无审核通过的题目，已跳过该题型");
                continue;
            }
            BigDecimal typeScore = typeRules.stream()
                    .map(Rule::getScore).filter(Objects::nonNull).findFirst().orElse(null);

            int[] plan = new int[4];
            for (Rule r : typeRules) {
                plan[clampLevel(r.getDifficulty())] += r.getCount();
            }
            int planTotal = plan[1] + plan[2] + plan[3];
            // ① 总量压缩
            if (planTotal > capTotal) {
                double factor = capTotal / (double) planTotal;
                int[] scaled = new int[4];
                for (int i = 1; i <= 3; i++) scaled[i] = (int) Math.floor(plan[i] * factor);
                int left = capTotal - (scaled[1] + scaled[2] + scaled[3]);
                int[] order = {2, 1, 3};
                int idx = 0;
                int guard = 0;
                while (left > 0 && guard++ < 3 * capTotal + 10) {
                    int i = order[idx++ % 3];
                    if (scaled[i] < cap[i]) {
                        scaled[i]++;
                        left--;
                    }
                }
                plan = scaled;
                warnings.add("「" + AiComposePlan.typeText(type) + "」计划 " + planTotal + " 题，题库可用 "
                        + capTotal + " 题，已自动下调为 " + (plan[1] + plan[2] + plan[3]) + " 题");
            }
            // ② 难度档顺延
            for (int level = 1; level <= 3; level++) {
                int over = plan[level] - cap[level];
                if (over <= 0) continue;
                plan[level] = cap[level];
                for (int dist = 1; dist <= 2 && over > 0; dist++) {
                    for (int other : new int[]{level - dist, level + dist}) {
                        if (other < 1 || other > 3) continue;
                        int spare = cap[other] - plan[other];
                        if (spare <= 0) continue;
                        int take = Math.min(spare, over);
                        plan[other] += take;
                        over -= take;
                        warnings.add("「" + AiComposePlan.typeText(type) + "·" + AiComposePlan.difficultyText(level)
                                + "」题目不足，已从「" + AiComposePlan.difficultyText(other) + "」补充 " + take + " 题");
                    }
                }
                if (over > 0) {
                    warnings.add("「" + AiComposePlan.typeText(type) + "·" + AiComposePlan.difficultyText(level)
                            + "」题库不足，已减少 " + over + " 题（建议补充该难度题目）");
                }
            }
            for (int level = 1; level <= 3; level++) {
                if (plan[level] <= 0) continue;
                if (questionBudget + plan[level] > MAX_QUESTIONS) {
                    int take = MAX_QUESTIONS - questionBudget;
                    if (take <= 0) {
                        warnings.add("单张试卷题目数量上限为 " + MAX_QUESTIONS + " 题，超出的规则已忽略");
                        break;
                    }
                    warnings.add("单张试卷题目数量上限为 " + MAX_QUESTIONS + " 题，该规则题数已由 "
                            + plan[level] + " 调整为 " + take);
                    plan[level] = take;
                }
                questionBudget += plan[level];
                result.add(new Rule(type, level, plan[level], typeScore));
            }
        }
        return result;
    }

    // ==================== 优先级①：目标总分必须命中 ====================

    /** 目标总分容差：1% 且不低于 1 分（0.5 分粒度下的客观误差） */
    private static BigDecimal scoreTolerance(BigDecimal target) {
        return target.multiply(new BigDecimal("0.01")).max(new BigDecimal("1")).setScale(1, RoundingMode.HALF_UP);
    }

    /** 规则的单题分值（方案未给分值或给出非法值时，用题型默认分值兜底） */
    private static BigDecimal unitScoreOf(Rule rule) {
        BigDecimal score = rule.getScore();
        if (score == null || score.signum() <= 0) {
            return TYPE_UNIT_SCORE.getOrDefault(rule.getQuestionType(), new BigDecimal("2"));
        }
        return score;
    }

    /** 方案的计划总分 */
    private static BigDecimal plannedScore(List<Rule> rules) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Rule rule : rules) {
            sum = sum.add(unitScoreOf(rule).multiply(BigDecimal.valueOf(rule.getCount())));
        }
        return sum.setScale(1, RoundingMode.HALF_UP);
    }

    /** 方案计划题目总数 */
    private static int plannedCount(List<Rule> rules) {
        int sum = 0;
        for (Rule rule : rules) {
            sum += rule.getCount() == null ? 0 : rule.getCount();
        }
        return sum;
    }

    /** 某题型的计划题量 */
    private static int countOfType(List<Rule> rules, Integer type) {
        int sum = 0;
        for (Rule rule : rules) {
            if (type.equals(rule.getQuestionType())) sum += rule.getCount() == null ? 0 : rule.getCount();
        }
        return sum;
    }

    /** 某题型的规则条数（减题时每条至少保留 1 题） */
    private static int countRulesOfType(List<Rule> rules, Integer type) {
        int n = 0;
        for (Rule rule : rules) {
            if (type.equals(rule.getQuestionType())) n++;
        }
        return n;
    }

    /** 分值取到 0.5 的整数倍 */
    private static BigDecimal roundToHalf(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal itemsScore(List<AiComposePlan.Item> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (AiComposePlan.Item item : items) {
            if (item.getScore() != null) sum = sum.add(item.getScore());
        }
        return sum.setScale(1, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String preferenceText(int preference) {
        return preference == 1 ? "偏基础" : preference == 3 ? "偏挑战" : "均衡";
    }

    /**
     * 优先级①「目标总分」：抽题前先把方案的计划总分咬到教师设定的目标分。
     * <ol>
     *     <li>先按题型增减题数（单题分值小的题型优先参与配平，受题库可用量与 100 题上限约束，
     *         加题落到难度倾向对应的档）；</li>
     *     <li>残差再用「微调单题分值（0.5 的整数倍、不低于 0.5 分）」吸收；</li>
     *     <li>仍配不平则明确说明差在哪、差多少，而不是笼统提示"相差较大"让教师自己改目标分。</li>
     * </ol>
     */
    private void alignTotalScore(List<Rule> rules, Map<Integer, int[]> capacity, int preference,
                                 BigDecimal target, List<String> warnings) {
        if (rules == null || rules.isEmpty()) return;
        BigDecimal diff = target.subtract(plannedScore(rules));
        if (diff.abs().compareTo(scoreTolerance(target)) <= 0) return;

        List<String> notes = new ArrayList<>();
        diff = adjustByCount(rules, capacity, preference, diff, notes);
        if (diff.abs().compareTo(new BigDecimal("0.25")) > 0) {
            adjustByUnitScore(rules, diff, notes);
        }
        if (!notes.isEmpty()) {
            warnings.add("【目标总分优先】为命中目标总分 " + plain(target) + " 分，AI 已自动调整方案："
                    + String.join("；", notes));
        }
        BigDecimal remain = target.subtract(plannedScore(rules));
        if (remain.abs().compareTo(scoreTolerance(target)) > 0) {
            warnings.add("【目标总分优先】受题库可用题量与 0.5 分粒度限制，方案计划总分 "
                    + plain(plannedScore(rules)) + " 分与目标 " + plain(target) + " 分仍相差 "
                    + plain(remain.abs()) + " 分，建议补充题库后重试");
        }
    }

    /** 按题型增减题数逼近目标分，返回仍有待配平的差额 */
    private BigDecimal adjustByCount(List<Rule> rules, Map<Integer, int[]> capacity, int preference,
                                     BigDecimal diff, List<String> notes) {
        Map<Integer, BigDecimal> unitByType = new LinkedHashMap<>();
        for (Rule rule : rules) {
            unitByType.putIfAbsent(rule.getQuestionType(), unitScoreOf(rule));
        }
        List<Integer> ordered = new ArrayList<>(unitByType.keySet());
        // 单题分值小的题型先参与配平，总分更容易精确命中
        ordered.sort(Comparator.comparing(unitByType::get));
        int budget = MAX_QUESTIONS - plannedCount(rules);

        for (Integer type : ordered) {
            if (diff.signum() == 0 || budget <= 0) break;
            BigDecimal unit = unitByType.get(type);
            int[] cap = capacity.getOrDefault(type, new int[4]);
            int delta;
            if (diff.signum() > 0) {
                int canAdd = Math.min(Math.max(cap[1] + cap[2] + cap[3] - countOfType(rules, type), 0), budget);
                delta = Math.min(diff.divide(unit, 0, RoundingMode.DOWN).intValue(), canAdd);
                if (delta <= 0) continue;
            } else {
                int removable = Math.max(countOfType(rules, type) - countRulesOfType(rules, type), 0);
                delta = Math.max(diff.divide(unit, 0, RoundingMode.CEILING).intValue(), -removable);
                if (delta >= 0) continue;
            }
            applyCountDelta(rules, type, delta, preference, cap);
            budget -= delta;
            diff = diff.subtract(unit.multiply(BigDecimal.valueOf(delta)));
            notes.add("「" + AiComposePlan.typeText(type) + "」"
                    + (delta > 0 ? "增加 " + delta : "减少 " + (-delta)) + " 题");
        }
        return diff;
    }

    /**
     * 把 delta 题落到某题型的规则上：
     * 加题 → 落到「难度倾向」对应且题库仍有余量的难度档；减题 → 从题量最多的规则减起（每条至少留 1 题）。
     */
    private void applyCountDelta(List<Rule> rules, Integer type, int delta, int preference, int[] cap) {
        List<Rule> typeRules = rules.stream()
                .filter(r -> type.equals(r.getQuestionType()))
                .collect(Collectors.toList());
        if (typeRules.isEmpty()) return;
        if (delta > 0) {
            int preferLevel = clampLevel(preference);
            Map<Integer, Integer> planned = new HashMap<>();
            for (Rule rule : typeRules) {
                planned.merge(clampLevel(rule.getDifficulty()), rule.getCount(), Integer::sum);
            }
            Rule target = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Rule rule : typeRules) {
                int level = clampLevel(rule.getDifficulty());
                if (cap[level] - planned.getOrDefault(level, 0) <= 0) continue;
                int distance = Math.abs(level - preferLevel);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    target = rule;
                }
            }
            if (target == null) {
                target = typeRules.stream()
                        .min(Comparator.comparingInt(r -> Math.abs(clampLevel(r.getDifficulty()) - preferLevel)))
                        .orElse(typeRules.get(0));
            }
            target.setCount(target.getCount() + delta);
        } else {
            int over = -delta;
            List<Rule> ordered = new ArrayList<>(typeRules);
            ordered.sort(Comparator.comparingInt(Rule::getCount).reversed());
            for (Rule rule : ordered) {
                if (over <= 0) break;
                int cut = Math.min(Math.max(rule.getCount() - 1, 0), over);
                rule.setCount(rule.getCount() - cut);
                over -= cut;
            }
        }
    }

    /** 残差配平：微调单题分值（0.5 的整数倍、不低于 0.5 分），优选题量最多的规则，单题分值跳变最小 */
    private void adjustByUnitScore(List<Rule> rules, BigDecimal diff, List<String> notes) {
        List<Rule> ordered = new ArrayList<>(rules);
        ordered.sort(Comparator.comparingInt(Rule::getCount).reversed());
        for (Rule rule : ordered) {
            if (diff.abs().compareTo(new BigDecimal("0.25")) <= 0) break;
            BigDecimal unit = unitScoreOf(rule);
            BigDecimal newUnit = roundToHalf(unit.add(
                    diff.divide(BigDecimal.valueOf(rule.getCount()), 2, RoundingMode.HALF_UP)));
            if (newUnit.compareTo(MIN_UNIT_SCORE) < 0) newUnit = MIN_UNIT_SCORE;
            if (newUnit.compareTo(unit) == 0) continue;
            diff = diff.subtract(newUnit.subtract(unit).multiply(BigDecimal.valueOf(rule.getCount())));
            notes.add("「" + AiComposePlan.typeText(rule.getQuestionType()) + "·"
                    + AiComposePlan.difficultyText(rule.getDifficulty()) + "」每题分值由 " + plain(unit)
                    + " 分改为 " + plain(newUnit) + " 分");
            rule.setScore(newUnit);
        }
    }

    /**
     * 优先级①「目标总分」兜底：题库题量不足导致实际抽到的分值低于计划时，
     * 按实际抽到的题目分题型微调单题分值（0.5 的整数倍），让目标总分真正命中。
     */
    private void alignItemsToTarget(List<AiComposePlan.Item> items, BigDecimal target, List<String> warnings) {
        BigDecimal diff = target.subtract(itemsScore(items));
        if (diff.abs().compareTo(scoreTolerance(target)) <= 0) return;
        Map<Integer, List<AiComposePlan.Item>> groups = new LinkedHashMap<>();
        for (AiComposePlan.Item item : items) {
            groups.computeIfAbsent(item.getQuestionType(), k -> new ArrayList<>()).add(item);
        }
        List<List<AiComposePlan.Item>> ordered = new ArrayList<>(groups.values());
        // 题量多的题型先调，单题分值跳变最小
        ordered.sort(Comparator.comparingInt((List<AiComposePlan.Item> g) -> g.size()).reversed());
        List<String> notes = new ArrayList<>();
        for (List<AiComposePlan.Item> group : ordered) {
            if (diff.abs().compareTo(scoreTolerance(target)) <= 0) break;
            BigDecimal unit = group.get(0).getScore() == null ? BigDecimal.ZERO : group.get(0).getScore();
            boolean uniform = group.stream().allMatch(i -> unit.compareTo(
                    i.getScore() == null ? BigDecimal.ZERO : i.getScore()) == 0);
            if (!uniform) continue;
            BigDecimal newUnit = roundToHalf(unit.add(
                    diff.divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP)));
            if (newUnit.compareTo(MIN_UNIT_SCORE) < 0) newUnit = MIN_UNIT_SCORE;
            if (newUnit.compareTo(unit) == 0) continue;
            diff = diff.subtract(newUnit.subtract(unit).multiply(BigDecimal.valueOf(group.size())));
            for (AiComposePlan.Item item : group) item.setScore(newUnit);
            notes.add("「" + AiComposePlan.typeText(group.get(0).getQuestionType()) + "」每题 "
                    + plain(unit) + " 分 → " + plain(newUnit) + " 分");
        }
        if (!notes.isEmpty()) {
            warnings.add("【目标总分优先】题库题量不足，已按实际抽到的题目微调单题分值以贴合目标总分 "
                    + plain(target) + " 分：" + String.join("；", notes));
        }
        BigDecimal remain = target.subtract(itemsScore(items));
        if (remain.abs().compareTo(scoreTolerance(target)) > 0) {
            warnings.add("受题库可用题量与 0.5 分粒度限制，实际总分 " + plain(itemsScore(items)) + " 分与目标 "
                    + plain(target) + " 分仍相差 " + plain(remain.abs()) + " 分");
        }
    }

    /** 报告里规则的单题分值同步为配平后的最终值，保证「小计」与总分一致 */
    private void syncRuleScore(List<AiComposePlan.Rule> rules, List<AiComposePlan.Item> items) {
        for (AiComposePlan.Rule rule : rules) {
            for (AiComposePlan.Item item : items) {
                if (Objects.equals(item.getQuestionType(), rule.getQuestionType())
                        && levelOf(item.getDifficulty()) == clampLevel(rule.getDifficulty())) {
                    rule.setScore(item.getScore());
                    break;
                }
            }
        }
    }

    /** 优先级②校验：实际难度分布与「难度倾向」偏差过大时说明原因 */
    private void checkDifficultyPreference(List<AiComposePlan.Item> items, int preference, List<String> warnings) {
        if (items.isEmpty()) return;
        double[] ratio = DIFF_RATIO.getOrDefault(preference, DIFF_RATIO.get(2));
        int[] count = new int[4];
        for (AiComposePlan.Item item : items) {
            count[levelOf(item.getDifficulty())]++;
        }
        StringBuilder actual = new StringBuilder();
        double maxGap = 0;
        for (int level = 1; level <= 3; level++) {
            double r = count[level] * 1.0 / items.size();
            maxGap = Math.max(maxGap, Math.abs(r - ratio[level - 1]));
            if (level > 1) actual.append('、');
            actual.append(AiComposePlan.difficultyText(level)).append(' ').append(Math.round(r * 100)).append('%');
        }
        if (maxGap > 0.2) {
            warnings.add("【难度倾向】实际难度分布 " + actual + " 与「" + preferenceText(preference) + "」倾向（"
                    + prefRatioText(preference) + "）偏差较大，主要受题库各难度档可用题量限制");
        }
    }

    /**
     * 优先级④：本地规则引擎下从「补充要求」里提取明确点到的知识点作为重点；
     * 其余语义要求本地引擎无法理解，由调用方给出提示。
     */
    private List<Long> focusFromRequirement(String requirement, List<AiComposeMeta.KnowledgeStat> options) {
        List<Long> ids = new ArrayList<>();
        if (requirement == null || requirement.isBlank() || options == null) return ids;
        String text = requirement.trim();
        for (AiComposeMeta.KnowledgeStat option : options) {
            String name = option.getKnowledgeName();
            if (name == null || name.isBlank() || option.getKnowledgeId() == null) continue;
            if (text.contains(name) && !ids.contains(option.getKnowledgeId())) {
                ids.add(option.getKnowledgeId());
            }
        }
        return ids;
    }

    // ==================== 抽题：重点优先 + 知识点覆盖率择优 ====================

    /**
     * 按单条规则抽题：
     * ① 有重点知识点时，约一半题量优先从重点知识点中抽；
     * ② 其余题量在全知识点范围内抽，并按「知识点覆盖率最大化」轮转择优；
     * ③ 该难度档不够时放宽难度兜底，全过程记录提示。
     */
    private List<Question> pickQuestions(Long courseId, Rule rule, List<Long> focusIds,
                                         Set<Long> used, List<String> warnings) {
        int count = rule.getCount();
        List<Question> picked = new ArrayList<>();
        boolean relaxed = false;

        // ① 重点知识点优先（约一半）
        if (!focusIds.isEmpty()) {
            int focusNeed = Math.max(1, count / 2);
            List<Question> candidates = questionMapper.listComposeCandidates(courseId, rule.getQuestionType(),
                    minDifficulty(rule.getDifficulty()), maxDifficulty(rule.getDifficulty()),
                    focusIds, Math.min(focusNeed * 5, 200));
            picked.addAll(diversify(filterUnused(candidates, used, picked), focusNeed));
        }

        // ② 全知识点范围补齐（覆盖率择优）
        int need = count - picked.size();
        if (need > 0) {
            List<Question> candidates = questionMapper.listComposeCandidates(courseId, rule.getQuestionType(),
                    minDifficulty(rule.getDifficulty()), maxDifficulty(rule.getDifficulty()),
                    null, Math.min(need * 6, 300));
            List<Question> extra = diversify(filterUnused(candidates, used, picked), need);
            if (extra.size() < need && !picked.isEmpty() && !focusIds.isEmpty()) {
                warnings.add("重点知识点题目不足，「" + AiComposePlan.typeText(rule.getQuestionType()) + "·"
                        + AiComposePlan.difficultyText(rule.getDifficulty()) + "」已从其他知识点补齐 "
                        + extra.size() + " 题");
            }
            picked.addAll(extra);
        }

        // ③ 该难度档不够 → 放宽难度兜底
        need = count - picked.size();
        if (need > 0) {
            List<Question> candidates = questionMapper.listComposeCandidates(courseId, rule.getQuestionType(),
                    null, null, null, Math.min(need * 6, 300));
            List<Question> extra = diversify(filterUnused(candidates, used, picked), need);
            if (!extra.isEmpty()) {
                relaxed = true;
                warnings.add("「" + AiComposePlan.typeText(rule.getQuestionType()) + "·"
                        + AiComposePlan.difficultyText(rule.getDifficulty()) + "」题目不足，已从其他难度补充 "
                        + extra.size() + " 题");
            }
            picked.addAll(extra);
        }
        if (picked.size() < count) {
            warnings.add("「" + AiComposePlan.typeText(rule.getQuestionType()) + "·"
                    + AiComposePlan.difficultyText(rule.getDifficulty()) + "」实际仅抽到 " + picked.size()
                    + " 题（题库可用题目不足）");
        }
        // 记录已用题目，避免整张试卷重复出题
        picked.forEach(q -> {
            if (q.getId() != null) used.add(q.getId());
        });
        if (relaxed) {
            picked.sort(Comparator.comparingInt((Question q) -> q.getDifficulty() == null ? 2 : q.getDifficulty()));
        }
        return picked;
    }

    /**
     * 知识点覆盖率择优：先按知识点分组，题量少的知识点优先出题（轮转取题），
     * 让一张试卷尽可能覆盖更多知识点，而不是集中在某一两个知识点上。
     */
    private List<Question> diversify(List<Question> candidates, int need) {
        List<Question> result = new ArrayList<>();
        if (candidates == null || candidates.isEmpty() || need <= 0) return result;
        List<Question> pool = new ArrayList<>(candidates);
        Collections.shuffle(pool);
        Map<Long, List<Question>> groups = new LinkedHashMap<>();
        for (Question q : pool) {
            Long key = q.getKnowledgeId() == null ? 0L : q.getKnowledgeId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }
        List<List<Question>> buckets = new ArrayList<>(groups.values());
        buckets.sort(Comparator.comparingInt(List::size));
        int round = 0;
        while (result.size() < need) {
            boolean moved = false;
            for (List<Question> bucket : buckets) {
                if (round < bucket.size()) {
                    result.add(bucket.get(round));
                    moved = true;
                    if (result.size() >= need) break;
                }
            }
            if (!moved) break;
            round++;
        }
        return result;
    }

    /** 去掉已出现在本试卷中的题目，并按 id 去重 */
    private List<Question> filterUnused(List<Question> candidates, Set<Long> used, List<Question> current) {
        Set<Long> taken = new HashSet<>(used);
        current.forEach(q -> {
            if (q.getId() != null) taken.add(q.getId());
        });
        List<Question> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Question q : candidates == null ? List.<Question>of() : candidates) {
            if (q.getId() == null || taken.contains(q.getId()) || !seen.add(q.getId())) continue;
            result.add(q);
        }
        return result;
    }

    // ==================== 大模型调用 ====================

    private String buildComposePrompt(ExamPaper paper, AiComposeRequest req, BigDecimal target, int preference,
                                      Map<Integer, int[]> capacity, List<Long> focusIds,
                                      Map<Long, String> knowledgeNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为下面这场考试设计组卷方案（只输出 JSON）：\n");
        sb.append("【条件优先级（从高到低，前一条必须优先于后一条）】目标总分 > 难度倾向 > 重点知识点 > 补充要求\n");
        sb.append("【学科】").append(paper.getCourseName() == null ? "-" : paper.getCourseName()).append('\n');
        if (paper.getPaperName() != null) sb.append("【试卷名称】").append(paper.getPaperName()).append('\n');
        sb.append("【目标总分】").append(target.stripTrailingZeros().toPlainString())
                .append(" 分（最高优先级：rules 的 Σ(count × score) 必须等于该分数，允许 ±1 分）\n");
        sb.append("【难度倾向】").append(preferenceText(preference))
                .append("（次优先，参考占比：简单/中等/困难 ≈ ").append(prefRatioText(preference)).append("）\n");
        if (!focusIds.isEmpty()) {
            String names = focusIds.stream().map(id -> knowledgeNames.getOrDefault(id, "知识点" + id))
                    .collect(Collectors.joining("、"));
            sb.append("【重点考察知识点】").append(names)
                    .append("（优先级低于目标总分与难度倾向，这些知识点的题目占比可适当提高）\n");
        }
        if (req.getRequirement() != null && !req.getRequirement().isBlank()) {
            sb.append("【教师补充要求】").append(req.getRequirement().trim())
                    .append("（优先级最低，在不违反前面条件的前提下尽量兼顾）\n");
        }
        sb.append("【题库可用题量（仅审核通过的题目，任何规则不得超过）】\n");
        for (Map.Entry<Integer, int[]> entry : new TreeMap<>(capacity).entrySet()) {
            int[] cap = entry.getValue();
            int sum = cap[1] + cap[2] + cap[3];
            if (sum <= 0) continue;
            sb.append("  ").append(AiComposePlan.typeText(entry.getKey())).append("：共 ").append(sum)
                    .append(" 题（简单 ").append(cap[1]).append(" / 中等 ").append(cap[2])
                    .append(" / 困难 ").append(cap[3]).append("）\n");
        }
        sb.append("注意：题目总数不要超过 100 题；rules 的分值合计必须等于目标总分 ")
                .append(target.stripTrailingZeros().toPlainString())
                .append(" 分（允许 ±1 分），请在输出前自行核对，不要用 strategy 解释分数偏差。");
        return sb.toString();
    }

    private String callLlm(String userPrompt) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 1200);
        body.put("response_format", Map.of("type", "json_object"));

        String url = aiProperties.getBaseUrl();
        if (!url.endsWith("/")) url = url + "/";
        url = url + "chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
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

    /** 解析模型输出（可能被 ```json ``` 包裹，或前后带解释文字） */
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

    // ==================== 工具方法 ====================

    private boolean aiEnabled() {
        return aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank();
    }

    /** 把「题型 × 难度档」统计行组装成 type → [_, 简单, 中等, 困难] 的容量数组 */
    private Map<Integer, int[]> buildCapacity(List<Map<String, Object>> rows) {
        Map<Integer, int[]> capacity = new HashMap<>();
        if (rows == null) return capacity;
        for (Map<String, Object> row : rows) {
            Integer type = toIntObj(row.get("questionType"));
            Integer level = toIntObj(row.get("difficultyLevel"));
            int cnt = toInt(row.get("cnt"));
            if (type == null || level == null) continue;
            int[] arr = capacity.computeIfAbsent(type, k -> new int[4]);
            arr[clampLevel(level)] += cnt;
        }
        return capacity;
    }

    /** 知识点可用题量（按题量倒序） */
    private List<AiComposeMeta.KnowledgeStat> loadKnowledgeOptions(Long courseId) {
        List<AiComposeMeta.KnowledgeStat> list = new ArrayList<>();
        for (Map<String, Object> row : questionMapper.statByKnowledge(courseId)) {
            Long id = toLongObj(row.get("knowledgeId"));
            if (id == null) continue;
            AiComposeMeta.KnowledgeStat ks = new AiComposeMeta.KnowledgeStat();
            ks.setKnowledgeId(id);
            ks.setKnowledgeName(row.get("knowledgeName") == null ? "未命名知识点" : row.get("knowledgeName").toString());
            ks.setQuestionCount(toInt(row.get("cnt")));
            list.add(ks);
        }
        return list;
    }

    /** 难度档 → 题库难度下限（1简单档含 difficulty 为空的题目） */
    private static Integer minDifficulty(Integer level) {
        int l = clampLevel(level);
        return l == 1 ? null : l;
    }

    /** 难度档 → 题库难度上限 */
    private static Integer maxDifficulty(Integer level) {
        return clampLevel(level) == 3 ? null : clampLevel(level);
    }

    /** 题库难度（可能 1~5）归一化为 1/2/3 三档 */
    private static int levelOf(Integer difficulty) {
        if (difficulty == null || difficulty <= 1) return 1;
        return difficulty == 2 ? 2 : 3;
    }

    private static int clampLevel(Integer level) {
        if (level == null) return 2;
        if (level < 1) return 1;
        return level > 3 ? 3 : level;
    }

    /** 按难度占比拆分题数，舍入误差补到「中等」档 */
    private static int[] splitByRatio(int total, double[] ratio) {
        int[] result = new int[4];
        int used = 0;
        for (int i = 0; i < 3; i++) {
            result[i + 1] = (int) Math.round(total * ratio[i]);
            used += result[i + 1];
        }
        int diff = total - used;
        result[2] += diff;
        if (result[2] < 0) {
            int[] fallback = new int[4];
            fallback[2] = total;
            return fallback;
        }
        return result;
    }

    private static String prefRatioText(int preference) {
        double[] r = DIFF_RATIO.getOrDefault(preference, DIFF_RATIO.get(2));
        return Math.round(r[0] * 100) + "%/" + Math.round(r[1] * 100) + "%/" + Math.round(r[2] * 100) + "%";
    }

    private static String brief(String msg) {
        if (msg == null) return "未知原因";
        return msg.length() > 60 ? msg.substring(0, 60) + "…" : msg;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static Integer toIntObj(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return null;
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLongObj(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return null;
        try {
            return Long.parseLong(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 内部模型 ====================

    /** 组卷方案（大模型或本地引擎产出） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Blueprint {
        private String strategy;
        private List<Rule> rules;
    }

    /** 抽取规则：题型 + 难度档 + 题数 + 每题分值 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Rule {
        private Integer questionType;
        private Integer difficulty;
        private Integer count;
        private BigDecimal score;
    }
}
