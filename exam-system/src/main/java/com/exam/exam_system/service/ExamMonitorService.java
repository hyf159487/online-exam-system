package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.MonitorStudentVO;
import com.exam.exam_system.dto.RunningExamVO;
import com.exam.exam_system.dto.StudentAnswerDetailVO;

import java.util.List;

/**
 * 考试监控 Service
 */
public interface ExamMonitorService {

    /**
     * 获取当前正在进行中的考试列表
     *
     * @param courseIds 数据权限：教师只能看自己任教课程下的考试；null = 不限制（管理员）
     */
    List<RunningExamVO> listRunningExams(List<Long> courseIds);

    /**
     * 获取某场考试的学生监控列表（分页）
     */
    PageResult<MonitorStudentVO> listStudents(Long examId, Long classId, Integer status,
                                                String keyword, Long pageNum, Long pageSize);

    /**
     * 查看某个学生的答题详情
     */
    List<StudentAnswerDetailVO> listStudentAnswers(Long examId, Long studentId);

    /**
     * 强制交卷：保存现有答案、生成成绩、更新状态
     */
    void forceSubmit(Long examId, Long studentId, Long operatorId, Integer operatorRole);
}
