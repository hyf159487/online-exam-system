package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.PracticePaperQuestion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 练习卷-试题关联 Mapper
 */
@Mapper
public interface PracticePaperQuestionMapper {

    /** 批量插入关联 */
    @Insert("<script>" +
            "INSERT INTO practice_paper_question(practice_paper_id, question_id, sort_no, score) VALUES " +
            "<foreach collection='list' item='q' separator=','>" +
            "(#{practicePaperId}, #{q.questionId}, #{q.sortNo}, #{q.score})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("practicePaperId") Long practicePaperId,
                    @Param("list") List<PracticePaperQuestion> list);

    /** 删除练习卷的所有关联 */
    @Delete("DELETE FROM practice_paper_question WHERE practice_paper_id = #{practicePaperId}")
    int deleteByPaperId(@Param("practicePaperId") Long practicePaperId);

    /** 查练习卷所有题目（含题干/题型/选项等） */
    @Select("SELECT ppq.id, ppq.practice_paper_id AS practicePaperId, ppq.question_id AS questionId, " +
            "ppq.sort_no AS sortNo, ppq.score, " +
            "q.content AS questionContent, q.question_type AS questionType, q.difficulty, " +
            "q.answer AS correctAnswer, q.analysis, q.options " +
            "FROM practice_paper_question ppq " +
            "JOIN question q ON ppq.question_id = q.id " +
            "WHERE ppq.practice_paper_id = #{practicePaperId} " +
            "ORDER BY ppq.sort_no ASC")
    List<PracticePaperQuestion> listByPaperId(@Param("practicePaperId") Long practicePaperId);
}
