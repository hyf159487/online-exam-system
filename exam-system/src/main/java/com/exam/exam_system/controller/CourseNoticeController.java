package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.Course;
import com.exam.exam_system.entity.CourseNotice;
import com.exam.exam_system.entity.SysNotice;
import com.exam.exam_system.mapper.CourseMapper;
import com.exam.exam_system.mapper.CourseMemberMapper;
import com.exam.exam_system.mapper.CourseNoticeMapper;
import com.exam.exam_system.mapper.NoticeMapper;
import com.exam.exam_system.mapper.UserNoticeMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程公告：学生端「课程详情 → 课程公告」Tab 的数据来源
 *
 * <p>公告按课程维度组织，由该课程的任课教师（或管理员）发布，仅该课程学生可见：
 * <ul>
 *   <li>教师/管理员：GET /api/course-notice/list、POST /api/course-notice、
 *       PUT /api/course-notice/{id}、DELETE /api/course-notice/{id}（校验任教关系，管理员不限）；</li>
 *   <li>学生：GET /api/student/course/notices?courseId=xx（仅已加入该课程的学生可读）。</li>
 * </ul>
 *
 * <p>发布公告时会同时向该课程已加入学生推送一条系统通知（sys_notice，receive_type=6），
 * 学生端「通知公告」页可同步看到提醒，无需额外轮询。
 */
@RestController
public class CourseNoticeController {

    /** 发布课程公告时写入系统通知的类型标识（与课程申请通知一致，不参与接收范围计算） */
    private static final int NOTICE_TYPE_COURSE = 6;

    @Autowired
    private CourseNoticeMapper courseNoticeMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseMemberMapper courseMemberMapper;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private UserNoticeMapper userNoticeMapper;

    /**
     * 教师端 / 管理端：某课程的公告列表（按发布时间倒序）
     * GET /api/course-notice/list?courseId=xx
     */
    @GetMapping("/api/course-notice/list")
    public Result<List<CourseNotice>> list(@RequestParam Long courseId, HttpServletRequest request) {
        if (!canManage(request, courseId)) {
            return Result.error(403, "您不是该课程的任教教师，无权查看课程公告");
        }
        return Result.success("查询成功", courseNoticeMapper.listByCourse(courseId));
    }

    /**
     * 教师端 / 管理端：发布课程公告
     * POST /api/course-notice  { courseId, title, content }
     */
    @PostMapping("/api/course-notice")
    public Result<CourseNotice> create(@RequestBody CourseNotice body, HttpServletRequest request) {
        if (body == null || body.getCourseId() == null) {
            return Result.error(400, "缺少课程ID");
        }
        if (!canManage(request, body.getCourseId())) {
            return Result.error(403, "您不是该课程的任教教师，无权发布课程公告");
        }
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            return Result.error(400, "公告标题不能为空");
        }
        if (body.getContent() == null || body.getContent().isBlank()) {
            return Result.error(400, "公告内容不能为空");
        }
        Course course = courseMapper.selectById(body.getCourseId());
        if (course == null) {
            return Result.error(400, "课程不存在");
        }

        CourseNotice notice = new CourseNotice();
        notice.setCourseId(body.getCourseId());
        notice.setTitle(body.getTitle().trim());
        notice.setContent(body.getContent().trim());
        notice.setTeacherId((Long) request.getAttribute("userId"));
        notice.setTeacherName(currentUserName(request, "教师"));
        courseNoticeMapper.insert(notice);
        notice.setCourseName(course.getCourseName());

        // 同步推送系统通知，学生端「通知公告」页即可看到提醒
        pushStudentNotice(body.getCourseId(), course.getCourseName(), notice.getTitle());
        return Result.success("公告发布成功", notice);
    }

    /**
     * 教师端 / 管理端：修改课程公告
     * PUT /api/course-notice/{id}  { title, content }
     */
    @PutMapping("/api/course-notice/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CourseNotice body,
                               HttpServletRequest request) {
        CourseNotice exist = courseNoticeMapper.selectById(id);
        if (exist == null) {
            return Result.error(404, "公告不存在或已删除");
        }
        if (!canManage(request, exist.getCourseId())) {
            return Result.error(403, "您不是该课程的任教教师，无权修改课程公告");
        }
        if (body == null || body.getTitle() == null || body.getTitle().isBlank()) {
            return Result.error(400, "公告标题不能为空");
        }
        if (body.getContent() == null || body.getContent().isBlank()) {
            return Result.error(400, "公告内容不能为空");
        }
        CourseNotice update = new CourseNotice();
        update.setId(id);
        update.setTitle(body.getTitle().trim());
        update.setContent(body.getContent().trim());
        courseNoticeMapper.update(update);
        return Result.success("公告修改成功", null);
    }

    /**
     * 教师端 / 管理端：删除课程公告（逻辑删除）
     * DELETE /api/course-notice/{id}
     */
    @DeleteMapping("/api/course-notice/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        CourseNotice exist = courseNoticeMapper.selectById(id);
        if (exist == null) {
            return Result.error(404, "公告不存在或已删除");
        }
        if (!canManage(request, exist.getCourseId())) {
            return Result.error(403, "您不是该课程的任教教师，无权删除课程公告");
        }
        courseNoticeMapper.deleteById(id);
        return Result.success("公告已删除", null);
    }

    /**
     * 学生端：某课程的公告列表（课程详情页「课程公告」Tab，按发布时间倒序）
     * GET /api/student/course/notices?courseId=xx
     *
     * <p>仅「已加入该课程」的学生可读；未申请 / 审核中返回 403
     * （前端对应 Tab 也会锁定，此处为服务端兜底校验）。
     */
    @GetMapping("/api/student/course/notices")
    public Result<List<CourseNotice>> studentNotices(@RequestParam Long courseId,
                                                     HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        if (studentId == null) {
            return Result.error(401, "登录状态已失效，请重新登录");
        }
        if (courseId == null) {
            return Result.error(400, "缺少课程ID");
        }
        if (courseMemberMapper.countJoined(studentId, courseId) <= 0) {
            return Result.error(403, "请先加入该课程后再查看课程公告");
        }
        return Result.success("查询成功", courseNoticeMapper.listByCourse(courseId));
    }

    /**
     * 课程公告管理权限：管理员（role=2）可管理全部课程；教师仅能管理自己任教的课程。
     */
    private boolean canManage(HttpServletRequest request, Long courseId) {
        if (courseId == null) {
            return false;
        }
        Integer role = (Integer) request.getAttribute("role");
        if (role != null && role == 2) {
            return true;
        }
        Long teacherId = (Long) request.getAttribute("userId");
        if (teacherId == null) {
            return false;
        }
        List<Long> mine = courseMapper.selectCourseIdsByTeacher(teacherId);
        return mine != null && mine.contains(courseId);
    }

    /** 发布公告后向该课程已加入学生推送系统通知（无学生时静默跳过） */
    private void pushStudentNotice(Long courseId, String courseName, String noticeTitle) {
        List<Long> studentIds = courseMemberMapper.selectStudentIds(courseId);
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }
        SysNotice sysNotice = new SysNotice();
        sysNotice.setTitle("课程公告：" + noticeTitle);
        sysNotice.setContent("课程《" + (courseName == null ? "" : courseName) + "》发布了新公告："
                + noticeTitle + "，可前往该课程详情页查看。");
        sysNotice.setReceiveType(NOTICE_TYPE_COURSE);
        sysNotice.setSendUserId(1L);
        sysNotice.setSendUserName("课程公告");
        noticeMapper.insert(sysNotice);
        userNoticeMapper.batchInsert(sysNotice.getId(), studentIds);
    }

    /** 取当前登录人姓名（优先 realName，其次 userName） */
    private String currentUserName(HttpServletRequest request, String fallback) {
        String realName = (String) request.getAttribute("realName");
        if (realName != null && !realName.isBlank()) {
            return realName;
        }
        String userName = (String) request.getAttribute("userName");
        return (userName != null && !userName.isBlank()) ? userName : fallback;
    }
}
