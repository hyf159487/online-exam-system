package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.PracticeAnswer;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 练习答题明细 Mapper
 */
@Mapper
public interface PracticeAnswerMapper {

    /** 批量插入 */
    @Insert("<script>" +
            "INSERT INTO practice_answer(practice_record_id, practice_paper_id, question_id, sort_no, " +
            "question_type, is_objective, score, get_score, user_answer, correct_answer, is_correct, review_status) VALUES " +
            "<foreach collection='list' item='a' separator=','>" +
            "(#{a.practiceRecordId}, #{a.practicePaperId}, #{a.questionId}, #{a.sortNo}, " +
            "#{a.questionType}, #{a.isObjective}, #{a.score}, #{a.getScore}, #{a.userAnswer}, #{a.correctAnswer}, " +
            "#{a.isCorrect}, #{a.reviewStatus})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<PracticeAnswer> list);

    /** 查询某条练习记录的所有答题明细（含题干/选项/解析/批改信息） */
    @Select("SELECT pa.id, pa.practice_record_id AS practiceRecordId, " +
            "pa.practice_paper_id AS practicePaperId, pa.question_id AS questionId, " +
            "pa.sort_no AS sortNo, pa.question_type AS questionType, " +
            "pa.is_objective AS isObjective, pa.score, pa.get_score AS getScore, " +
            "pa.review_score AS reviewScore, pa.review_status AS reviewStatus, " +
            "pa.review_opinion AS reviewOpinion, pa.review_user_id AS reviewUserId, pa.review_time AS reviewTime, " +
            "pa.user_answer AS userAnswer, " +
            "pa.correct_answer AS correctAnswer, pa.is_correct AS isCorrect, " +
            "q.content AS questionContent, q.options, q.analysis " +
            "FROM practice_answer pa " +
            "JOIN question q ON pa.question_id = q.id " +
            "WHERE pa.practice_record_id = #{recordId} " +
            "ORDER BY pa.sort_no ASC")
    List<PracticeAnswer> listByRecordId(@Param("recordId") Long recordId);

    /** 教师批改单道主观题：写入得分/评语，并把该题得分同步进 get_score / is_correct */
    @Update("UPDATE practice_answer SET review_score = #{reviewScore}, review_status = 1, " +
            "review_opinion = #{reviewOpinion}, review_user_id = #{teacherId}, review_time = NOW(), " +
            "get_score = #{reviewScore}, is_correct = IF(#{reviewScore} > 0, 1, 0) " +
            "WHERE id = #{id}")
    int updateReview(@Param("id") Long id,
                     @Param("reviewScore") Integer reviewScore,
                     @Param("reviewOpinion") String reviewOpinion,
                     @Param("teacherId") Long teacherId);
}
