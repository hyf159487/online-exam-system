package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.ExamAnswer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 答题明细 Mapper，对应 exam_answer 表
 */
@Mapper
public interface ExamAnswerMapper {

    /** 插入一条答题明细（交卷时逐题写入 / 进度保存时首次写入） */
    @Insert("INSERT INTO exam_answer(record_id, question_id, user_answer, is_correct, get_score, " +
            "is_objective, review_status) " +
            "VALUES(#{recordId}, #{questionId}, #{userAnswer}, #{isCorrect}, #{getScore}, " +
            "#{isObjective}, #{reviewStatus})")
    int insert(ExamAnswer a);

    /** 按考试记录统计答题条数（校验用） */
    @Select("SELECT COUNT(*) FROM exam_answer WHERE record_id = #{recordId}")
    int countByRecordId(@Param("recordId") Long recordId);

    /** 查询某次考试记录的全部答题明细（用于恢复答题进度） */
    @Select("SELECT * FROM exam_answer WHERE record_id = #{recordId} ORDER BY id")
    List<ExamAnswer> findByRecordId(@Param("recordId") Long recordId);

    /** 查询某道题已保存的作答（进度保存时判断 插入 or 更新） */
    @Select("SELECT * FROM exam_answer WHERE record_id = #{recordId} AND question_id = #{questionId} LIMIT 1")
    ExamAnswer findByRecordAndQuestion(@Param("recordId") Long recordId,
                                       @Param("questionId") Long questionId);

    /** 更新某道题的作答内容（进度保存） */
    @Update("UPDATE exam_answer SET user_answer = #{userAnswer} WHERE id = #{id}")
    int updateAnswer(@Param("id") Long id, @Param("userAnswer") String userAnswer);

    /** 教师阅卷：更新简答题的得分、对错标记与评语（旧方法，保留兼容） */
    @Update("UPDATE exam_answer SET is_correct = #{isCorrect}, get_score = #{getScore}, " +
            "teacher_comment = #{teacherComment} WHERE id = #{id}")
    int updateReview(ExamAnswer a);

    /** 教师阅卷：使用新字段更新 */
    @Update("UPDATE exam_answer SET is_correct = #{isCorrect}, get_score = #{getScore}, " +
            "teacher_comment = #{teacherComment}, " +
            "review_score = #{getScore}, review_opinion = #{teacherComment}, review_status = 1 " +
            "WHERE id = #{id}")
    int updateReviewV2(ExamAnswer a);

    /** 清空某次考试记录的全部答题明细（交卷时重写，避免与进度保存的行冲突） */
    @Delete("DELETE FROM exam_answer WHERE record_id = #{recordId}")
    int deleteByRecordId(@Param("recordId") Long recordId);

    /**
     * 删除某场考试的全部答题明细（删除考试时级联清理）
     * exam_answer 只挂 record_id，需经 exam_record 定位到该考试
     */
    @Delete("DELETE ea FROM exam_answer ea " +
            "INNER JOIN exam_record r ON ea.record_id = r.id " +
            "WHERE r.exam_id = #{examId}")
    int deleteByExamId(@Param("examId") Long examId);
}
