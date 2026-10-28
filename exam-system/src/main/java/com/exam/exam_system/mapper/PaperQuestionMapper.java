package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.PaperQuestionItem;
import com.exam.exam_system.dto.StudentRecordDetail;
import com.exam.exam_system.entity.PaperQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 试卷-题目关联 Mapper，对应 paper_question 表
 */
@Mapper
public interface PaperQuestionMapper {

    /** 新增一条关联 */
    @Insert("INSERT INTO paper_question(paper_id, question_id, score, sort_order) " +
            "VALUES(#{paperId}, #{questionId}, #{score}, #{sortOrder})")
    int insert(PaperQuestion pq);

    /** 删除一张试卷的全部题目关联（重新组卷前先清空） */
    @Delete("DELETE FROM paper_question WHERE paper_id = #{paperId}")
    int deleteByPaperId(@Param("paperId") Long paperId);

    /** 联表查试卷的题目列表（组卷页回显用），按 sort_order 排序 */
    @Select("SELECT pq.question_id AS questionId, q.content, q.question_type AS questionType, " +
            "q.course_id AS courseId, q.options, q.answer, pq.score, pq.sort_order AS sortOrder " +
            "FROM paper_question pq JOIN question q ON pq.question_id = q.id " +
            "WHERE pq.paper_id = #{paperId} ORDER BY pq.sort_order")
    List<PaperQuestionItem> listByPaperId(@Param("paperId") Long paperId);

    /**
     * 学生回看试卷：查题目明细（含参考答案与解析），按 sort_order 排序
     *
     * <p>参考答案与解析在这里统一查出，是否真正下发给学生由 Service 按
     * 「考试是否结束」决定（见 {@code StudentRecordDetail#answerVisible}）。</p>
     */
    @Select("SELECT pq.question_id AS questionId, q.content, q.question_type AS questionType, " +
            "q.options, q.answer AS correctAnswer, q.analysis, pq.score, pq.sort_order AS sortOrder " +
            "FROM paper_question pq JOIN question q ON pq.question_id = q.id " +
            "WHERE pq.paper_id = #{paperId} ORDER BY pq.sort_order")
    List<StudentRecordDetail.QuestionItem> listForRecordDetail(@Param("paperId") Long paperId);

    /** 统计一张试卷的题目数 */
    @Select("SELECT COUNT(*) FROM paper_question WHERE paper_id = #{paperId}")
    int countByPaperId(@Param("paperId") Long paperId);

    /** 批量删除多张试卷的题目关联（批量删除试卷前用） */
    @Delete("<script>" +
            "DELETE FROM paper_question WHERE paper_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteByPaperIds(@Param("ids") List<Long> paperIds);

}
