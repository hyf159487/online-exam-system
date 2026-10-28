package com.exam.exam_system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 某学科练习画像（学生选中学科后展示）。
 *
 * <p>分三块：① 我的练习进度（已练/覆盖率/正确率/错题数）；
 * ② 题库家底（题型分布 / 难度分布 / 知识点题量 Top）；
 * ③ 供前端画环形图与条形图的结构化数据，占比在后端算好，避免各端口径不一致。</p>
 */
@Data
public class CoursePracticeStats {

    /** 该学科题库题目总数（审核通过的） */
    private Integer totalQuestion;

    /** 该学生在该学科已作答过的去重题目数 */
    private Integer practicedQuestion;

    /** 题库覆盖率(%)：已练去重题数 / 题库题数 */
    private Integer coverageRate;

    /** 我在该学科的累计作答次数（含重复作答） */
    private Integer myAnswered;

    /** 我在该学科的累计答对次数 */
    private Integer myCorrect;

    /** 我在该学科的正确率(%)，未作答时为 null（前端显示「--」） */
    private Integer myAccuracy;

    /** 该学科已收录进错题本的题目数 */
    private Integer wrongQuestionCount;

    /** 题型分布（按题量倒序，含占比） */
    private List<TypeStat> typeStats = new ArrayList<>();

    /** 难度分布（简单/中等/困难，与刷题筛选的三档口径一致） */
    private List<LevelStat> levelStats = new ArrayList<>();

    /** 知识点题量 Top（按题量倒序，最多 5 条；该学科未维护知识点时为空） */
    private List<KnowledgeStat> knowledgeStats = new ArrayList<>();

    /** 单一题型的题量与占比 */
    @Data
    public static class TypeStat {
        private Integer questionType;
        private String questionTypeText;
        private Integer count;
        /** 占题库比例(%)，四舍五入到整数 */
        private Integer ratio;
    }

    /** 单一难度档的题量与占比 */
    @Data
    public static class LevelStat {
        /** 1=简单 2=中等 3=困难 */
        private Integer level;
        private String levelText;
        private Integer count;
        private Integer ratio;
    }

    /** 知识点题量 */
    @Data
    public static class KnowledgeStat {
        private Long knowledgeId;
        private String knowledgeName;
        private Integer count;
    }
}
