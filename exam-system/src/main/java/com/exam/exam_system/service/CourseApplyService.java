package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.entity.Course;
import com.exam.exam_system.entity.CourseApply;

import java.util.List;
import java.util.Map;

/**
 * 学生加入课程申请 Service
 */
public interface CourseApplyService {

    /**
     * 学生端：开放课程列表（含当前学生对每门课的状态），分页返回
     * <p>每条记录字段：courseId/courseName/courseCode/courseType/description/teacherNames/
     * startTime/enrollDeadline/applyStatus/canApply/inPending/joined/applyClosed/
     * quitPending/canQuit
     * <p>applyStatus：null=未申请 / 0=待审核 / 1=已加入 / 2=已驳回；
     * applyClosed=true 表示已过报名截止时间，前端显示【报名截止】并禁用。
     * quitPending=true 表示存在「退出课程」待审核申请；canQuit=true 表示可发起退出。
     *
     * @param statusFilter 加入状态筛选：null/空=全部，joined=已加入，notJoined=未加入，pending=审核中
     * @param courseName   课程名称模糊筛选（可空）
     * @param teacherName  授课教师模糊筛选（可空）
     * @param courseType   课程类型精确筛选（可空）
     * @param pageNum      页码（从 1 开始）
     * @param pageSize     每页大小
     */
    PageResult<Map<String, Object>> listOpenCourses(Long studentId, String statusFilter,
                                                    String courseName, String teacherName,
                                                    String courseType,
                                                    int pageNum, int pageSize);

    /** 学生端：课程类型下拉选项（课程表中已有类型去重，用于「课程类型」筛选） */
    List<String> listCourseTypes();

    /**
     * 学生端：课程名称 / 授课教师筛选下拉候选（去重，供筛选框下拉选择 + 输入关键字模糊查询）
     * <p>返回 key：courseNames=课程名称列表，teacherNames=实际任课教师姓名列表
     */
    Map<String, List<String>> filterOptions();

    /** 学生端：我的申请记录 */
    List<CourseApply> listMine(Long studentId);

    /**
     * 学生端：我已加入的课程列表（自主练习按学科筛选用）
     * @param keyword 课程名称/编码关键字模糊过滤（可空）
     */
    List<Course> listJoined(Long studentId, String keyword);

    /**
     * 学生端「个人中心 → 我的课程」：我已加入的课程信息
     * <p>比 listJoined 多带任课教师、参加人数、本人加入时间、学习资料数与退出审核状态，供卡片展示
     * @param keyword 课程名称 / 编码 / 任课教师姓名 模糊过滤（可空）
     */
    List<Map<String, Object>> listMyJoinedCourses(Long studentId, String keyword);

    /**
     * 学生端「课程详情页」：单门课程的完整信息 + 当前学生与该课程的关系状态
     *
     * <p>返回 key：
     * <ul>
     *   <li>course：课程实体（含 intro 详细介绍、learningObjectives 学习目标、targetAudience 适用人群、
     *       syllabus 课程大纲、cover、startTime、enrollDeadline、teacherNames、studentCount）；</li>
     *   <li>teachers：任课教师列表（id / realName / phone / email，供详情页教师信息展示）；</li>
     *   <li>state：applyStatus / canApply / inPending / joined / applyClosed / quitPending / canQuit，
     *       与「加入课程」列表口径一致，前端据此锁定 Tab 与切换底部按钮。</li>
     * </ul>
     *
     * <p>课程不存在时抛 IllegalArgumentException（前端提示「课程不存在」）。
     */
    Map<String, Object> getCourseDetail(Long studentId, Long courseId);

    /** 学生申请加入课程（生成待审核记录 + 通知任课教师） */
    void apply(Long studentId, Long courseId, String studentName);

    /** 学生申请退出课程（生成退出待审核记录 + 通知任课教师；审核通过后移除课程成员） */
    void applyQuit(Long studentId, Long courseId, String studentName);

    /** 教师：我任课课程的申请审核列表（teacherId 用于限定任教课程，status 可空=全部，0=待审核） */
    List<CourseApply> listApply(Long teacherId, Integer status);

    /** 教师：我任课课程的待审核申请数 */
    long countPending(Long teacherId);

    /**
     * 教师：审核页统计卡片数据
     * <p>返回 key：pendingCount=待审核数量，handledThisMonth=本月已处理（通过+驳回）数量
     */
    Map<String, Object> auditStats(Long teacherId);

    /**
     * 教师：申请记录导出用列表（仅任教课程）
     * @param status   可空（null=全部，0待审核 1已通过 2已驳回）
     * @param courseId 可空（非空则只看该课程）
     */
    List<CourseApply> listForExport(Long teacherId, Integer status, Long courseId);

    /**
     * 教师：申请学生基础信息 + 该生在本人任教课程下的申请记录（详情弹窗）
     * <p>返回 key：id/username/realName/className/phone/email/status/registerTime/
     * joinedCourseCount（已加入课程数）/applies（申请记录列表）
     */
    Map<String, Object> studentDetail(Long teacherId, Long studentId);

    /**
     * 教师：批量通过申请（逐条执行，单条失败不影响其余）
     * <p>批量驳回需逐条填写各自理由，不做批量；返回 key：successCount/skippedCount
     */
    Map<String, Object> batchApprove(List<Long> applyIds, Long teacherId);

    /** 审核通过：写入课程成员 + 通知学生（仅任教该课程的教师可操作） */
    void approve(Long applyId, Long teacherId);

    /** 审核驳回：填写理由 + 通知学生（仅任教该课程的教师可操作） */
    void reject(Long applyId, Long teacherId, String reason);

    /**
     * 教师端「我的课程」：仅返回当前教师任教的课程列表（含已加入学生数）
     * @param keyword    课程名称/编码模糊过滤（可空）
     * @param courseType 课程类型模糊过滤（可空）
     */
    List<Map<String, Object>> listMyCourses(Long teacherId, String keyword, String courseType);

    /**
     * 教师端「我的课程-查看学生」：某课程已加入学生名单（校验该教师确实任教此课程）
     * @param keyword 学生姓名/学号模糊过滤（可空）
     */
    List<Map<String, Object>> listCourseStudents(Long teacherId, Long courseId, String keyword);

    /** 教师端：将某学生移出自己任教的课程（校验任教关系，并通知学生） */
    void removeStudent(Long teacherId, Long courseId, Long studentId);
}
