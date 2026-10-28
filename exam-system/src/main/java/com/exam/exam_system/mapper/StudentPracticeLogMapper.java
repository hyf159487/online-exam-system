package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.StudentPracticeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 学生作答记录 Mapper（统计「某学科已练习题数」用）
 */
@Mapper
public interface StudentPracticeLogMapper {

    /** 落一条单题作答记录（无论对错都记） */
    @Insert("INSERT INTO student_practice_log(user_id, question_id, course_id, question_type, source, user_answer, is_correct, create_time) " +
            "VALUES(#{userId}, #{questionId}, #{courseId}, #{questionType}, #{source}, #{userAnswer}, #{isCorrect}, NOW())")
    int insert(StudentPracticeLog log);

    /** 统计某学生、某学科下作答过的去重题目数 */
    @Select("SELECT COUNT(DISTINCT question_id) FROM student_practice_log " +
            "WHERE user_id = #{userId} AND course_id = #{courseId}")
    long countDistinctByUserCourse(@Param("userId") Long userId,
                                   @Param("courseId") Long courseId);

    /** 该学生在某道题上的总作答次数 */
    @Select("SELECT COUNT(*) FROM student_practice_log " +
            "WHERE user_id = #{userId} AND question_id = #{questionId}")
    int countByUserQuestion(@Param("userId") Long userId,
                            @Param("questionId") Long questionId);

    /** 该学生在某道题上的答对次数 */
    @Select("SELECT COUNT(*) FROM student_practice_log " +
            "WHERE user_id = #{userId} AND question_id = #{questionId} AND is_correct = 1")
    int countCorrectByUserQuestion(@Param("userId") Long userId,
                                   @Param("questionId") Long questionId);

    /**
     * 某学生、某学科的作答总览（正确率用）。
     *
     * @return 单行 Map：answered=累计作答次数，correct=累计答对次数
     */
    @Select("SELECT COUNT(*) AS answered, " +
            "COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct " +
            "FROM student_practice_log WHERE user_id = #{userId} AND course_id = #{courseId}")
    Map<String, Object> overviewByUserCourse(@Param("userId") Long userId,
                                             @Param("courseId") Long courseId);
}
