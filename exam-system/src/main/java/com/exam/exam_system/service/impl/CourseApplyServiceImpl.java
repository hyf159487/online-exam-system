package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.entity.Course;
import com.exam.exam_system.entity.CourseApply;
import com.exam.exam_system.entity.SysNotice;
import com.exam.exam_system.mapper.CourseApplyMapper;
import com.exam.exam_system.mapper.CourseMapper;
import com.exam.exam_system.mapper.CourseMemberMapper;
import com.exam.exam_system.mapper.NoticeMapper;
import com.exam.exam_system.mapper.UserNoticeMapper;
import com.exam.exam_system.service.CourseApplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生加入课程申请 Service 实现
 */
@Service
public class CourseApplyServiceImpl implements CourseApplyService {

    @Autowired
    private CourseApplyMapper courseApplyMapper;

    @Autowired
    private CourseMemberMapper courseMemberMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private UserNoticeMapper userNoticeMapper;

    /** 课程申请/课程学习的通知类型标识（sys_notice.receive_type，不参与现有学生/班级/考试范围计算） */
    private static final int NOTICE_TYPE_COURSE = 6;

    @Override
    public PageResult<Map<String, Object>> listOpenCourses(Long studentId, String statusFilter,
                                                           String courseName, String teacherName,
                                                           String courseType,
                                                           int pageNum, int pageSize) {
        List<Course> courses = courseMapper.selectAllWithTeachers();

        // 当前学生对各课程的申请记录：courseId -> applyType -> status（各类型取最新一条）
        List<CourseApply> mine = courseApplyMapper.selectList(null, studentId);
        Map<Long, Integer> joinStatusMap = new LinkedHashMap<>();
        // 退出申请是否处于待审核：courseId -> true
        Map<Long, Boolean> quitPendingMap = new LinkedHashMap<>();
        for (CourseApply ca : mine) {
            if (ca.getApplyType() != null && ca.getApplyType() == 1) {
                // 退出申请：记录是否待审核
                quitPendingMap.putIfAbsent(ca.getCourseId(), ca.getStatus() != null && ca.getStatus() == 0);
            } else {
                // 加入申请：记录最新状态
                joinStatusMap.putIfAbsent(ca.getCourseId(), ca.getStatus());
            }
        }

        // 多条件筛选 + 组装视图对象
        List<Map<String, Object>> filtered = new ArrayList<>();
        String cn = (courseName == null) ? null : courseName.trim();
        String tn = (teacherName == null) ? null : teacherName.trim();
        String ct = (courseType == null) ? null : courseType.trim();
        String sf = (statusFilter == null) ? null : statusFilter.trim();
        for (Course c : courses) {
            if (cn != null && !cn.isEmpty()
                    && !(c.getCourseName() != null && c.getCourseName().contains(cn))) {
                continue;
            }
            if (tn != null && !tn.isEmpty()
                    && !(c.getTeacherNames() != null && c.getTeacherNames().contains(tn))) {
                continue;
            }
            // 课程类型：下拉选中的是完整类型名，精确匹配（忽略首尾空格）
            if (ct != null && !ct.isEmpty()
                    && !(c.getCourseType() != null && c.getCourseType().trim().equals(ct))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", c.getId());
            item.put("courseName", c.getCourseName());
            item.put("courseCode", c.getCourseCode());
            item.put("courseType", c.getCourseType());
            item.put("description", c.getDescription());
            // 课程封面（课程管理上传）与已加入人数：学生端卡片视图展示用
            item.put("cover", c.getCover());
            item.put("teacherNames", c.getTeacherNames());
            item.put("studentCount", c.getStudentCount());
            item.put("startTime", c.getStartTime());
            item.put("enrollDeadline", c.getEnrollDeadline());
            // 已过报名截止时间（enrollDeadline 非空且早于当前时间）
            boolean closed = c.getEnrollDeadline() != null
                    && LocalDateTime.now().isAfter(c.getEnrollDeadline());
            // 学生视角加入状态：null=未申请 0=待审核 1=已加入 2=已驳回
            Integer st = joinStatusMap.get(c.getId());
            boolean joined = st != null && st == 1;
            boolean inPending = st != null && st == 0;
            boolean quitPending = Boolean.TRUE.equals(quitPendingMap.get(c.getId()));
            item.put("applyStatus", st);
            item.put("applyClosed", closed);
            // 未报名截止时，才可直接申请（未申请 或 上次被驳回）
            item.put("canApply", !closed && (st == null || st == 2));
            item.put("inPending", inPending);
            item.put("joined", joined);
            // 退出：已加入且无待审退出，才可发起退出
            item.put("quitPending", quitPending);
            item.put("canQuit", joined && !quitPending);

            // 加入状态筛选：joined=已加入 notJoined=未加入 pending=审核中
            if (sf != null && !sf.isEmpty()) {
                if ("joined".equals(sf) && !joined) {
                    continue;
                }
                if ("notJoined".equals(sf) && (joined || inPending)) {
                    continue;
                }
                if ("pending".equals(sf) && !(inPending || quitPending)) {
                    continue;
                }
            }
            filtered.add(item);
        }

        // 内存分页（课程数据量级较小，无分页插件）
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 10;
        int total = filtered.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> records = filtered.subList(from, to);
        return PageResult.of(records, (long) total, (long) pageNum, (long) pageSize);
    }

    @Override
    public List<CourseApply> listMine(Long studentId) {
        return courseApplyMapper.selectList(null, studentId);
    }

    @Override
    public List<Course> listJoined(Long studentId, String keyword) {
        List<Long> courseIds = courseMemberMapper.selectJoinedCourseIds(studentId);
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        List<Course> courses = courseMapper.selectByIds(courseIds);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            courses = courses.stream()
                    .filter(c -> (c.getCourseName() != null && c.getCourseName().toLowerCase().contains(kw))
                            || (c.getCourseCode() != null && c.getCourseCode().toLowerCase().contains(kw)))
                    .collect(java.util.stream.Collectors.toList());
        }
        return courses;
    }

    @Override
    public List<Map<String, Object>> listMyJoinedCourses(Long studentId, String keyword) {
        String kw = (keyword == null) ? null : keyword.trim();
        return courseMemberMapper.selectMyJoinedCourses(studentId, (kw == null || kw.isEmpty()) ? null : kw);
    }

    /**
     * 学生端「课程详情页」：课程完整信息 + 当前学生关系状态
     * <p>状态计算口径与 listOpenCourses 完全一致，保证卡片上的状态与详情页一致。
     */
    @Override
    public Map<String, Object> getCourseDetail(Long studentId, Long courseId) {
        Course course = courseMapper.selectByIdWithTeachers(courseId);
        if (course == null) {
            throw new IllegalArgumentException("课程不存在或已被删除");
        }
        course.setStudentCount((int) courseMemberMapper.countByCourse(courseId));

        // 当前学生对该课程的申请记录：加入取最新一条状态，退出取是否待审核
        List<CourseApply> mine = courseApplyMapper.selectList(null, studentId);
        Integer joinStatus = null;
        boolean quitPending = false;
        for (CourseApply ca : mine) {
            if (ca.getCourseId() == null || !ca.getCourseId().equals(courseId)) {
                continue;
            }
            if (ca.getApplyType() != null && ca.getApplyType() == 1) {
                quitPending = ca.getStatus() != null && ca.getStatus() == 0;
            } else if (joinStatus == null) {
                joinStatus = ca.getStatus();
            }
        }
        boolean closed = course.getEnrollDeadline() != null
                && LocalDateTime.now().isAfter(course.getEnrollDeadline());
        boolean joined = joinStatus != null && joinStatus == 1;
        boolean inPending = joinStatus != null && joinStatus == 0;

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("applyStatus", joinStatus);
        state.put("applyClosed", closed);
        state.put("canApply", !closed && (joinStatus == null || joinStatus == 2));
        state.put("inPending", inPending);
        state.put("joined", joined);
        state.put("quitPending", quitPending);
        state.put("canQuit", joined && !quitPending);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("course", course);
        detail.put("teachers", courseMapper.selectTeachers(courseId));
        detail.put("state", state);
        return detail;
    }

    @Override
    public List<String> listCourseTypes() {
        return courseMapper.selectDistinctCourseTypes();
    }

    @Override
    public Map<String, List<String>> filterOptions() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("courseNames", courseMapper.selectDistinctCourseNames());
        options.put("teacherNames", courseMapper.selectDistinctTeacherNames());
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(Long studentId, Long courseId, String studentName) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("课程不存在");
        }
        // 报名截止校验：非空且已过截止时间则不允许再申请
        if (course.getEnrollDeadline() != null
                && LocalDateTime.now().isAfter(course.getEnrollDeadline())) {
            throw new IllegalArgumentException("该课程已截止报名，无法申请加入");
        }
        if (courseMemberMapper.countJoined(studentId, courseId) > 0) {
            throw new IllegalArgumentException("您已加入该课程，无需重复申请");
        }
        if (courseApplyMapper.countPending(studentId, courseId, 0) > 0) {
            throw new IllegalArgumentException("您已提交申请，请等待教师审核");
        }

        CourseApply apply = new CourseApply();
        apply.setStudentId(studentId);
        apply.setCourseId(courseId);
        apply.setApplyType(0);
        courseApplyMapper.insert(apply);

        // 新申请通知该课程全部任课教师
        List<Long> teacherIds = courseMapper.selectTeacherIds(courseId);
        if (teacherIds != null && !teacherIds.isEmpty()) {
            String displayName = (studentName != null && !studentName.isBlank()) ? studentName : "学生";
            notifyUsers(teacherIds, "新的课程加入申请",
                    displayName + " 申请加入课程《" + course.getCourseName() + "》，请及时审核。");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyQuit(Long studentId, Long courseId, String studentName) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("课程不存在");
        }
        if (courseMemberMapper.countJoined(studentId, courseId) <= 0) {
            throw new IllegalArgumentException("您尚未加入该课程，无法退出");
        }
        if (courseApplyMapper.countPending(studentId, courseId, 1) > 0) {
            throw new IllegalArgumentException("您已提交退出申请，请等待教师审核");
        }

        CourseApply apply = new CourseApply();
        apply.setStudentId(studentId);
        apply.setCourseId(courseId);
        apply.setApplyType(1);
        courseApplyMapper.insert(apply);

        // 退出申请通知该课程全部任课教师
        List<Long> teacherIds = courseMapper.selectTeacherIds(courseId);
        if (teacherIds != null && !teacherIds.isEmpty()) {
            String displayName = (studentName != null && !studentName.isBlank()) ? studentName : "学生";
            notifyUsers(teacherIds, "新的退课申请",
                    displayName + " 申请退出课程《" + course.getCourseName() + "》，请及时审核。");
        }
    }

    @Override
    public List<CourseApply> listApply(Long teacherId, Integer status) {
        List<Long> courseIds = courseMapper.selectCourseIdsByTeacher(teacherId);
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return courseApplyMapper.selectListByCourses(status, courseIds);
    }

    @Override
    public long countPending(Long teacherId) {
        List<Long> courseIds = courseMapper.selectCourseIdsByTeacher(teacherId);
        if (courseIds == null || courseIds.isEmpty()) {
            return 0L;
        }
        return courseApplyMapper.countPendingByCourses(courseIds);
    }

    @Override
    public Map<String, Object> auditStats(Long teacherId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Long> courseIds = courseMapper.selectCourseIdsByTeacher(teacherId);
        long pending = 0L;
        long handledThisMonth = 0L;
        if (courseIds != null && !courseIds.isEmpty()) {
            pending = courseApplyMapper.countPendingByCourses(courseIds);
            handledThisMonth = courseApplyMapper.countHandledThisMonthByCourses(courseIds);
        }
        stats.put("pendingCount", pending);
        stats.put("handledThisMonth", handledThisMonth);
        return stats;
    }

    @Override
    public List<CourseApply> listForExport(Long teacherId, Integer status, Long courseId) {
        // listApply 已按「任教课程」限定数据范围，课程筛选在内存里补一层即可
        List<CourseApply> list = listApply(teacherId, status);
        if (courseId == null) {
            return list;
        }
        List<CourseApply> filtered = new ArrayList<>();
        for (CourseApply ca : list) {
            if (courseId.equals(ca.getCourseId())) {
                filtered.add(ca);
            }
        }
        return filtered;
    }

    @Override
    public Map<String, Object> studentDetail(Long teacherId, Long studentId) {
        List<Long> courseIds = courseMapper.selectCourseIdsByTeacher(teacherId);
        if (courseIds == null || courseIds.isEmpty()) {
            throw new IllegalArgumentException("您当前没有任教的课程");
        }
        List<CourseApply> applies = courseApplyMapper.selectByStudentInCourses(studentId, courseIds);
        if (applies == null || applies.isEmpty()) {
            throw new IllegalArgumentException("该学生没有申请过您任教的课程");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> info = courseApplyMapper.selectStudentInfo(studentId);
        if (info != null) {
            detail.putAll(info);
        }
        detail.put("joinedCourseCount", courseApplyMapper.countJoinedCourses(studentId));
        detail.put("applies", applies);
        return detail;
    }

    @Override
    public Map<String, Object> batchApprove(List<Long> applyIds, Long teacherId) {
        int successCount = 0;
        int skippedCount = 0;
        if (applyIds != null) {
            for (Long applyId : applyIds) {
                if (applyId == null) {
                    continue;
                }
                try {
                    approveOne(applyId, teacherId);
                    successCount++;
                } catch (RuntimeException e) {
                    // 已处理 / 非任教课程等：跳过该条，不阻断其余申请
                    skippedCount++;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("skippedCount", skippedCount);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applyId, Long teacherId) {
        approveOne(applyId, teacherId);
    }

    /**
     * 单条审核通过（单条审核与批量通过共用）
     * <p>不标 @Transactional：批量时逐条执行，某条失败不影响其它已通过的申请。
     */
    private void approveOne(Long applyId, Long teacherId) {
        CourseApply apply = courseApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new IllegalArgumentException("申请记录不存在");
        }
        if (apply.getStatus() != null && apply.getStatus() != 0) {
            throw new IllegalArgumentException("该申请已处理");
        }
        checkTeacherOwnsCourse(apply, teacherId);
        courseApplyMapper.updateAudit(applyId, 1, null, teacherId);
        Course course = courseMapper.selectById(apply.getCourseId());
        String courseName = course != null ? course.getCourseName() : "";

        // 按申请类型分流：加入申请写成员，退出申请删成员
        boolean isQuit = apply.getApplyType() != null && apply.getApplyType() == 1;
        if (isQuit) {
            if (courseMemberMapper.countJoined(apply.getStudentId(), apply.getCourseId()) > 0) {
                courseMemberMapper.delete(apply.getStudentId(), apply.getCourseId());
            }
            notifyUsers(List.of(apply.getStudentId()), "退课成功",
                    "您已成功退出课程《" + courseName + "》，将无法再查看该课程的学习资料。");
        } else {
            courseMemberMapper.insert(apply.getStudentId(), apply.getCourseId());
            notifyUsers(List.of(apply.getStudentId()), "课程加入成功",
                    "恭喜！您已成功加入课程《" + courseName + "》，现在可以在「课程资料」中查看、下载该课程的学习资料。");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long applyId, Long teacherId, String reason) {
        CourseApply apply = courseApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new IllegalArgumentException("申请记录不存在");
        }
        if (apply.getStatus() != null && apply.getStatus() != 0) {
            throw new IllegalArgumentException("该申请已处理");
        }
        checkTeacherOwnsCourse(apply, teacherId);
        String finalReason = reason == null ? "" : reason.trim();
        courseApplyMapper.updateAudit(applyId, 2, finalReason, teacherId);

        Course course = courseMapper.selectById(apply.getCourseId());
        String courseName = course != null ? course.getCourseName() : "";
        boolean isQuit = apply.getApplyType() != null && apply.getApplyType() == 1;
        String content;
        String title;
        if (isQuit) {
            title = "退课申请被驳回";
            content = "很抱歉，您申请退出课程《" + courseName + "》被驳回，您仍保留该课程的学习权限。";
        } else {
            title = "课程申请被驳回";
            content = "很抱歉，您申请加入课程《" + courseName + "》被驳回。";
        }
        if (!finalReason.isBlank()) {
            content += "\n驳回理由：" + finalReason;
        }
        content += isQuit ? "" : "\n您可修改后再次申请。";
        notifyUsers(List.of(apply.getStudentId()), title, content);
    }

    /**
     * 校验当前操作教师是否为该课程任教教师（只有任教老师可以审核）
     */
    private void checkTeacherOwnsCourse(CourseApply apply, Long teacherId) {
        checkTeacherOwnsCourse(apply.getCourseId(), teacherId);
    }

    /** 校验当前教师是否任教指定课程（我的课程-学生名单/移除学生用） */
    private void checkTeacherOwnsCourse(Long courseId, Long teacherId) {
        List<Long> teacherIds = courseMapper.selectTeacherIds(courseId);
        if (teacherIds == null || teacherIds.isEmpty() || !teacherIds.contains(teacherId)) {
            throw new IllegalArgumentException("您不是该课程的任教教师，无权操作");
        }
    }

    @Override
    public List<Map<String, Object>> listMyCourses(Long teacherId, String keyword, String courseType) {
        String kw = (keyword == null) ? null : keyword.trim();
        String type = (courseType == null) ? null : courseType.trim();
        return courseMapper.selectMyCourses(teacherId,
                (kw == null || kw.isEmpty()) ? null : kw,
                (type == null || type.isEmpty()) ? null : type);
    }

    @Override
    public List<Map<String, Object>> listCourseStudents(Long teacherId, Long courseId, String keyword) {
        checkTeacherOwnsCourse(courseId, teacherId);
        String kw = (keyword == null) ? null : keyword.trim();
        return courseMemberMapper.selectStudentsByCourse(courseId, kw == null || kw.isEmpty() ? null : kw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long teacherId, Long courseId, Long studentId) {
        checkTeacherOwnsCourse(courseId, teacherId);
        if (courseMemberMapper.countJoined(studentId, courseId) <= 0) {
            throw new IllegalArgumentException("该学生不在本课程中");
        }
        courseMemberMapper.delete(studentId, courseId);

        Course course = courseMapper.selectById(courseId);
        String courseName = course != null ? course.getCourseName() : "";
        notifyUsers(List.of(studentId), "课程移出通知",
                "您已被教师移出课程《" + courseName + "》，将无法再查看该课程的学习资料。");
    }

    /**
     * 发送系统通知（直接写 sys_notice + sys_user_notice）
     * receive_type 用课程学习标识（6），不参与现有范围计算。
     */
    private void notifyUsers(List<Long> userIds, String title, String content) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        SysNotice notice = new SysNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setReceiveType(NOTICE_TYPE_COURSE);
        notice.setSendUserId(1L);
        notice.setSendUserName("系统");
        noticeMapper.insert(notice);
        userNoticeMapper.batchInsert(notice.getId(), userIds);
    }
}
