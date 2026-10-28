package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.PaperDetail;
import com.exam.exam_system.dto.PaperQuestionSaveRequest;
import com.exam.exam_system.dto.RandomComposeRequest;
import com.exam.exam_system.entity.ExamPaper;
import com.exam.exam_system.entity.PaperQuestion;
import com.exam.exam_system.entity.PaperVisibleUser;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.mapper.ExamPaperMapper;
import com.exam.exam_system.mapper.PaperQuestionMapper;
import com.exam.exam_system.mapper.PaperVisibleUserMapper;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.mapper.SysUserMapper;
import com.exam.exam_system.service.ExamPaperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExamPaperServiceImpl implements ExamPaperService {

    @Autowired
    private ExamPaperMapper paperMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PaperVisibleUserMapper visibleUserMapper;

    @Override
    public List<ExamPaper> listRecent(int limit, List<Long> courseIds) {
        return paperMapper.listRecent(limit, courseIds);
    }

    @Override
    public List<ExamPaper> listByCreator(Long creatorId) {
        return paperMapper.listByCreator(creatorId);
    }

    @Override
    public PageResult<ExamPaper> page(Integer pageNum, Integer pageSize, String paperName, Integer status,
                                      Long courseId, Long userId, Integer role, List<Long> courseIds) {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        // 管理员 userId 传 null（不过滤）；教师传自己的 id（只看自己创建 + 被共享）
        Long filterUserId = (role != null && role == 2) ? null : userId;
        // 数据权限：教师只能看到自己任教课程下的试卷（courseIds 由 Controller 按登录教师注入）
        long total = paperMapper.countByQuery(paperName, status, courseId, filterUserId, role, courseIds);
        List<ExamPaper> records = paperMapper.listByQuery((pn - 1) * ps, ps, paperName, status, courseId, filterUserId, role, courseIds);
        fillVisibleNames(records);
        return PageResult.of(records, total, (long) pn, (long) ps);
    }

    @Override
    public ExamPaper getById(Long id) {
        return paperMapper.selectById(id);
    }

    /**
     * 批量填充可见教师姓名（一次性查中间表 + 一次性查教师姓名 → 内存组装）
     */
    private void fillVisibleNames(List<ExamPaper> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 收集试卷ID → 一次性查中间表
        List<Long> paperIds = records.stream().map(ExamPaper::getId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
        if (paperIds.isEmpty()) {
            return;
        }
        List<PaperVisibleUser> rows = visibleUserMapper.listByPaperIds(paperIds);
        if (rows == null || rows.isEmpty()) {
            return; // 无任何共享人
        }
        // 按 paper_id 分组
        Map<Long, List<Long>> paperToUserIds = rows.stream()
                .collect(Collectors.groupingBy(
                        PaperVisibleUser::getPaperId,
                        Collectors.mapping(PaperVisibleUser::getUserId, Collectors.toList())));
        // 一次性查所有教师姓名（role=1）
        Map<Long, String> nameMap = sysUserMapper.listByRole(1).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername()));
        for (ExamPaper p : records) {
            List<Long> userIds = paperToUserIds.get(p.getId());
            if (userIds == null || userIds.isEmpty()) {
                continue;
            }
            String names = userIds.stream()
                    .map(uid -> nameMap.getOrDefault(uid, "未知用户"))
                    .collect(Collectors.joining("、"));
            p.setVisibleNames(names);
        }
    }

    /**
     * 统一及格分口径：满分 × 60%（保留 1 位小数）
     *
     * <p>与班级学情统计的及格线（{@code StatsClassServiceImpl} 满分 × 0.6）保持一致，
     * 避免出现两套标准：统计说及格、成绩单说不及格。教师在界面上不再填写及格分。</p>
     *
     * @param totalScore 试卷满分
     * @return 及格分；满分为空或非正数时返回 0
     */
    private BigDecimal passScoreOf(BigDecimal totalScore) {
        if (totalScore == null || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalScore.multiply(new BigDecimal("0.6")).setScale(1, RoundingMode.HALF_UP);
    }

    /** 把前端的可见人 List 清洗：去空白/去重/剔除创建人自己；空则返回空 List（=仅创建人可见） */
    private List<Long> sanitizeVisibleUserIds(List<Long> ids, Long creatorId) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> creatorId == null || !id.equals(creatorId))
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public PaperDetail detail(Long id) {
        ExamPaper paper = paperMapper.selectById(id);
        if (paper == null) {
            return null;
        }
        PaperDetail detail = new PaperDetail();
        detail.setPaper(paper);
        // 补充题数（selectById 不带子查询）
        paper.setQuestionCount(paperQuestionMapper.countByPaperId(id));
        detail.setQuestions(paperQuestionMapper.listByPaperId(id));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(ExamPaper paper) {
        // ===== 二次校验（前端 vld 已校验，后端兜底） =====
        if (paper.getPaperName() == null || paper.getPaperName().isBlank()) {
            throw new BizException("试卷名称不能为空");
        }
        if (paper.getCourseId() == null) {
            throw new BizException("所属学科不能为空");
        }
        if (paper.getDuration() == null || paper.getDuration() < 10 || paper.getDuration() > 300) {
            throw new BizException("考试时长需在 10–300 分钟之间");
        }
        if (paperMapper.countByName(paper.getPaperName().trim()) > 0) {
            throw new BizException("试卷名称「" + paper.getPaperName().trim() + "」已存在，请换一个");
        }

        if (paper.getStatus() == null) {
            paper.setStatus(0); // 默认草稿；批量新增不允许直接发布，由 Controller 强制
        }
        if (paper.getTotalScore() == null) {
            paper.setTotalScore(BigDecimal.ZERO);
        }
        // 及格分不由教师填写：统一按满分 × 60% 换算（新增时总分为 0，组卷保存后会再次同步）
        paper.setPassScore(passScoreOf(paper.getTotalScore()));
        // 试卷本体先入库（主键回填到 paper.id）
        paperMapper.insert(paper);
        // 写入中间表（清洗可见人 List，剔除创建人自己）
        List<Long> cleanIds = sanitizeVisibleUserIds(paper.getVisibleUserIds(), paper.getCreatorId());
        for (Long uid : cleanIds) {
            visibleUserMapper.insert(paper.getId(), uid);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ExamPaper paper) {
        ExamPaper exist = paperMapper.selectById(paper.getId());
        if (exist == null) {
            throw new BizException("试卷不存在");
        }
        if (exist.getIsDeleted() != null && exist.getIsDeleted() == 1) {
            throw new BizException("试卷已删除，不能编辑");
        }
        if (exist.getStatus() != null && exist.getStatus() == 2) {
            throw new BizException("已归档试卷不能编辑（如需修改请先撤回归档）");
        }
        // 二次校验
        if (paper.getDuration() != null && (paper.getDuration() < 10 || paper.getDuration() > 300)) {
            throw new BizException("考试时长需在 10–300 分钟之间");
        }
        // 及格分不在编辑范围：由满分 × 60% 自动换算（见 passScoreOf），updateById 也不更新该列
        if (paper.getPaperName() != null && !paper.getPaperName().equals(exist.getPaperName())
                && paperMapper.countByName(paper.getPaperName().trim()) > 0) {
            throw new BizException("试卷名称「" + paper.getPaperName().trim() + "」已存在，请换一个");
        }
        paperMapper.updateById(paper);
        // 若传了 visibleUserIds（非 null）→ 全量替换中间表
        if (paper.getVisibleUserIds() != null) {
            visibleUserMapper.deleteByPaperId(paper.getId());
            List<Long> cleanIds = sanitizeVisibleUserIds(paper.getVisibleUserIds(), exist.getCreatorId());
            for (Long uid : cleanIds) {
                visibleUserMapper.insert(paper.getId(), uid);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExamPaper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        if (paper.getIsDeleted() != null && paper.getIsDeleted() == 1) {
            return; // 已删除幂等
        }
        // 进行中考试占用 → 禁删
        if (examMapper.countRunningByPaperId(id) > 0) {
            throw new BizException("该试卷正在被进行中的考试使用，不能删除");
        }
        // 有历史考试记录 → 逻辑删除（保留成绩追溯链路）
        if (paperMapper.countExamsByPaperId(id) > 0) {
            paperMapper.logicDeleteById(id);
            return;
        }
        // 无任何关联考试 → 物理删除
        paperQuestionMapper.deleteByPaperId(id);
        visibleUserMapper.deleteByPaperId(id);
        paperMapper.deleteById(id);
    }

    @Override
    public void publish(Long id) {
        ExamPaper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        if (paper.getIsDeleted() != null && paper.getIsDeleted() == 1) {
            throw new BizException("试卷已删除");
        }
        if (paper.getStatus() != null && paper.getStatus() == 2) {
            throw new BizException("已归档试卷不能再发布，如需修改请先撤回归档");
        }
        // 无题目试卷不能发布（与发布考试"已发布且已组卷"校验形成闭环）
        if (paperQuestionMapper.countByPaperId(id) == 0) {
            throw new BizException("试卷尚未组卷（无题目），请先完成组卷再发布");
        }
        paperMapper.updateStatus(id, 1); // 0草稿 → 1已发布
    }

    @Override
    public void revoke(Long id) {
        ExamPaper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BizException("仅已发布的试卷可撤回");
        }
        paperMapper.updateStatus(id, 0); // 1已发布 → 0草稿
    }

    @Override
    public void archive(Long id) {
        ExamPaper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        if (paper.getIsDeleted() != null && paper.getIsDeleted() == 1) {
            throw new BizException("试卷已删除");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BizException("仅已发布的试卷可归档");
        }
        paperMapper.updateStatus(id, 2); // 1已发布 → 2已归档
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int n = 0;
        for (Long id : ids) {
            ExamPaper p = paperMapper.selectById(id);
            if (p == null || p.getIsDeleted() != null && p.getIsDeleted() == 1) continue;
            if (p.getStatus() != null && p.getStatus() == 1) {
                paperMapper.updateStatus(id, 2);
                n++;
            }
        }
        return n;
    }

    /**
     * 组卷校验：归档/进行中考试占用 → 抛异常阻断
     */
    @Override
    public void checkComposable(Long paperId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        if (paper.getIsDeleted() != null && paper.getIsDeleted() == 1) {
            throw new BizException("试卷已删除，不能组卷");
        }
        if (paper.getStatus() != null && paper.getStatus() == 2) {
            throw new BizException("已归档试卷不能重新组卷");
        }
        if (paper.getStatus() != null && paper.getStatus() == 1) {
            throw new BizException("试卷已发布，不能组卷，请先撤回");
        }
        // ★ 关键：进行中考试占用 → 直接阻断（不仅弹窗）
        if (examMapper.countRunningByPaperId(paperId) > 0) {
            throw new BizException("该试卷存在正在进行的考试，不允许重新组卷");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestions(PaperQuestionSaveRequest request) {
        Long paperId = request.getPaperId();
        // 已归档/进行中考试占用 → 阻断（统一走 checkComposable）
        checkComposable(paperId);

        // 1. 清空旧关联（重新组卷 = 全量覆盖）
        paperQuestionMapper.deleteByPaperId(paperId);

        // 2. 逐条插入新关联，同时累加总分
        BigDecimal totalScore = BigDecimal.ZERO;
        if (request.getItems() != null) {
            int order = 1;
            for (PaperQuestionSaveRequest.Item item : request.getItems()) {
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(paperId);
                pq.setQuestionId(item.getQuestionId());
                pq.setScore(item.getScore() == null ? BigDecimal.ZERO : item.getScore());
                pq.setSortOrder(order++);
                paperQuestionMapper.insert(pq);
                totalScore = totalScore.add(pq.getScore());
            }
        }

        // 3. 把计算出的总分写回试卷
        paperMapper.updateTotalScore(paperId, totalScore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int randomCompose(Long paperId, List<RandomComposeRequest.Rule> rules) {
        checkComposable(paperId);
        ExamPaper paper = paperMapper.selectById(paperId);
        if (rules == null || rules.isEmpty()) {
            throw new BizException("请至少配置一条抽题规则");
        }
        if (paper.getCourseId() == null) {
            throw new BizException("试卷未关联学科，请先为试卷设置学科后再随机组卷");
        }

        // 1. 先校验所有规则的题库数量，一次性收集不足项，
        //    不足时通过 BizException 把结构化数据返回前端，供"自动调整题数/前往添加题目"使用
        List<Map<String, Object>> insufficient = new ArrayList<>();
        for (RandomComposeRequest.Rule rule : rules) {
            Integer questionType = rule.getQuestionType();
            Integer count = rule.getCount();
            if (questionType == null || count == null || count <= 0) {
                throw new BizException("抽题规则必须指定题型且数量大于0");
            }
            long available = questionMapper.countByTypeAndDifficulty(
                    paper.getCourseId(), questionType, rule.getDifficulty());
            if (available < count) {
                Map<String, Object> item = new HashMap<>();
                item.put("questionType", questionType);
                item.put("need", count);
                item.put("available", available);
                item.put("difficulty", rule.getDifficulty());
                insufficient.add(item);
            }
        }
        if (!insufficient.isEmpty()) {
            String detail = insufficient.stream()
                    .map(m -> "题型[" + m.get("questionType") + "]题库不足：需要" + m.get("need")
                            + "题，可用" + m.get("available") + "题")
                    .collect(Collectors.joining("；"));
            throw new BizException(detail, Map.of("insufficient", insufficient));
        }

        // 2. 校验全部通过后，先清空旧关联（覆盖式组卷）
        paperQuestionMapper.deleteByPaperId(paperId);

        // 3. 逐条规则抽题，汇总题目与总分
        int order = 1;
        BigDecimal totalScore = BigDecimal.ZERO;
        for (RandomComposeRequest.Rule rule : rules) {
            List<Question> picked = questionMapper.randomPick(
                    paper.getCourseId(), rule.getQuestionType(), rule.getDifficulty(), rule.getCount());
            for (Question q : picked) {
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(paperId);
                pq.setQuestionId(q.getId());
                // 规则未指定分值则沿用题目自带分值
                BigDecimal score = rule.getScore() == null ? q.getScore() : rule.getScore();
                pq.setScore(score == null ? BigDecimal.ZERO : score);
                pq.setSortOrder(order++);
                paperQuestionMapper.insert(pq);
                totalScore = totalScore.add(pq.getScore());
            }
        }

        // 4. 把计算出的总分写回试卷
        paperMapper.updateTotalScore(paperId, totalScore);
        return order - 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 去重，最多 200 条
        List<Long> unique = ids.stream().filter(java.util.Objects::nonNull).distinct().limit(200).collect(Collectors.toList());
        if (unique.isEmpty()) return 0;
        // 正在被"进行中"考试使用的试卷不能删除（整体拦截并点名试卷）
        List<String> runningNames = examMapper.listRunningPaperNames(unique);
        if (runningNames != null && !runningNames.isEmpty()) {
            throw new BizException("试卷「" + String.join("、", runningNames) + "」正在被进行中的考试使用，不能删除");
        }
        // 区分：有历史考试记录的走逻辑删，无的走物理删
        List<Long> logicDeleteIds = new ArrayList<>();
        List<Long> physicsDeleteIds = new ArrayList<>();
        for (Long id : unique) {
            if (paperMapper.countExamsByPaperId(id) > 0) {
                logicDeleteIds.add(id);
            } else {
                physicsDeleteIds.add(id);
            }
        }
        int n = 0;
        if (!logicDeleteIds.isEmpty()) {
            n += paperMapper.logicDeleteByIds(logicDeleteIds);
        }
        if (!physicsDeleteIds.isEmpty()) {
            // 物理删除前先清题目关联
            paperQuestionMapper.deleteByPaperIds(physicsDeleteIds);
            // 清中间表
            for (Long id : physicsDeleteIds) {
                visibleUserMapper.deleteByPaperId(id);
            }
            n += paperMapper.deleteByIds(physicsDeleteIds);
        }
        return n;
    }

}
