package com.exam.exam_system.common;

import com.exam.exam_system.mapper.CourseMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 教师数据权限工具 —— 统一实现「教师只能管理自己任教课程的数据」。
 *
 * <p>背景：系统的数据归属有两层
 * <ul>
 *     <li><b>课程归属</b>（主）：数据挂在某门课程下（question.course_id、
 *         exam_paper.course_id、exam.course_id、practice_paper.course_id ...），
 *         而「谁教这门课」由 sys_course_teacher(course_id, teacher_id) 决定</li>
 *     <li><b>创建人归属</b>（辅）：部分表有 creator_id / create_teacher_id，
 *         用于「我创建的数据」这种更细的展示（如试卷"我的试卷"）</li>
 * </ul>
 * 本工具以「课程归属」为准：管理员不受限，教师只能碰自己任教课程下的数据。
 *
 * <p>典型用法（Controller 里）：
 * <pre>
 * // 1) 列表查询：把 courseIds 塞进查询 DTO，Mapper 里拼 AND course_id IN (...)
 * List&lt;Long&gt; courseIds = teacherScope.myCourseIds(request);   // 管理员返回 null = 不限制
 * query.setCourseIds(courseIds);
 *
 * // 2) 单条数据操作：直接校验归属，越权会抛 ForbiddenException（全局转 403）
 * teacherScope.checkCourse(request, question.getCourseId());
 * </pre>
 *
 * <p>设计约定：
 * <ul>
 *     <li>{@link #myCourseIds(HttpServletRequest)} 返回 <b>null</b> 表示「不限制」（管理员），
 *         返回列表表示「只能看这些课程」；</li>
 *     <li>教师一门课都没任教时返回 {@link #NO_COURSE_ID}（-1）占位，
 *         这样 Mapper 里的 <code>IN</code> 条件天然查不到任何数据，
 *         比"空集合要不要拼 SQL"更容易保证安全（不会漏掉条件变成全量查询）。</li>
 * </ul>
 */
@Component
public class TeacherScope {

    /** 角色：学生 */
    public static final int ROLE_STUDENT = 0;
    /** 角色：教师 */
    public static final int ROLE_TEACHER = 1;
    /** 角色：管理员 */
    public static final int ROLE_ADMIN = 2;

    /**
     * 教师没有任何任教课程时的占位课程 id。
     * 课程主键从 1 开始自增，永远不会出现 -1，因此 IN(-1) 必定查不到数据。
     */
    public static final Long NO_COURSE_ID = -1L;

    @Autowired
    private CourseMapper courseMapper;

    // ==================== 当前登录用户 ====================

    /** 当前登录用户 id（AuthInterceptor 已保证登录，正常不会为 null） */
    public Long userId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v instanceof Long ? (Long) v : null;
    }

    /** 当前登录用户角色：0学生 1教师 2管理员 */
    public Integer role(HttpServletRequest request) {
        Object v = request.getAttribute("role");
        return v instanceof Integer ? (Integer) v : null;
    }

    /** 是否管理员（管理员对全站数据可见，不受课程限制） */
    public boolean isAdmin(HttpServletRequest request) {
        Integer role = role(request);
        return role != null && role == ROLE_ADMIN;
    }

    /** 是否教师 */
    public boolean isTeacher(HttpServletRequest request) {
        Integer role = role(request);
        return role != null && role == ROLE_TEACHER;
    }

    // ==================== 任教课程 ====================

    /**
     * 当前登录教师的任教课程 id 列表；管理员返回 <b>null</b>（表示不加限制）。
     *
     * @return null = 不限制；否则为可访问的课程 id 列表（无课程时为 [-1]）
     */
    public List<Long> myCourseIds(HttpServletRequest request) {
        if (isAdmin(request)) {
            return null;
        }
        return courseIdsOf(userId(request));
    }

    /**
     * 查某个教师的任教课程 id 列表。
     *
     * @param teacherId 教师 id，为空时返回 [-1]（查不到任何数据）
     * @return 永不为 null 的列表；无课程时为 [-1]
     */
    public List<Long> courseIdsOf(Long teacherId) {
        if (teacherId == null) {
            return Collections.singletonList(NO_COURSE_ID);
        }
        List<Long> ids = courseMapper.selectCourseIdsByTeacher(teacherId);
        if (ids == null || ids.isEmpty()) {
            return Collections.singletonList(NO_COURSE_ID);
        }
        // 过滤可能存在的 null（历史脏数据），全被过滤掉时同样返回占位
        List<Long> clean = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id != null) {
                clean.add(id);
            }
        }
        return clean.isEmpty() ? Collections.singletonList(NO_COURSE_ID) : clean;
    }

    // ==================== 归属校验 ====================

    /**
     * 教师是否任教该课程（管理员恒 true）。
     *
     * @param courseId 课程 id；为 null 时视为「不属于任何人」，教师返回 false
     */
    public boolean ownsCourse(Integer role, Long courseId, Long teacherId) {
        if (role != null && role == ROLE_ADMIN) {
            return true;
        }
        if (courseId == null || teacherId == null) {
            return false;
        }
        List<Long> teacherIds = courseMapper.selectTeacherIds(courseId);
        return teacherIds != null && teacherIds.contains(teacherId);
    }

    /** 当前登录用户是否可访问该课程的数据（管理员恒 true） */
    public boolean canAccessCourse(HttpServletRequest request, Long courseId) {
        return ownsCourse(role(request), courseId, userId(request));
    }

    /**
     * 校验当前登录教师是否有权操作该课程下的数据，越权抛 {@link ForbiddenException}（→ 403）。
     *
     * @param courseId 数据所属课程 id
     */
    public void checkCourse(HttpServletRequest request, Long courseId) {
        if (!canAccessCourse(request, courseId)) {
            throw new ForbiddenException("无权操作：该数据不属于您任教的课程");
        }
    }

    /**
     * 校验「课程 id 合法且归当前教师任教」，用于新增数据时必须指定课程的场景。
     *
     * <p>比 {@link #checkCourse} 多一层拦截：courseId 为空时直接拒绝，
     * 避免新建出「没有课程归属」的游离数据（那样就没人能管也查不到了）。
     */
    public void checkCourseRequired(HttpServletRequest request, Long courseId) {
        if (courseId == null) {
            throw new BizException("请先选择所属课程");
        }
        checkCourse(request, courseId);
    }

    /**
     * 批量校验：这批课程必须全部归当前教师任教（管理员直接放行）。
     *
     * <p>用于批量删除/批量改题目这类一次传多个 id 的接口：
     * 只要其中有一条数据不属于自己任教的课程，整批拒绝，避免"顺带删掉别人的题"。
     *
     * @param courseIds 这批数据各自所属的课程 id
     */
    public void checkCourses(HttpServletRequest request, Collection<Long> courseIds) {
        if (isAdmin(request)) {
            return;
        }
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        // 只查一次库，避免逐条 selectTeacherIds
        List<Long> mine = courseIdsOf(userId(request));
        for (Long courseId : courseIds) {
            if (courseId == null || !mine.contains(courseId)) {
                throw new ForbiddenException("无权操作：所选数据中包含不属于您任教课程的内容");
            }
        }
    }

    /**
     * 组合校验：课程归属 + 创建人兜底。
     *
     * <p>用于数据本身「可能没有课程 id」的历史表：优先按课程判定；
     * 确实没课程归属时，退化为「必须是我创建的」，既保证安全也不误伤历史数据。
     *
     * @param courseId  数据所属课程（可为 null）
     * @param creatorId 数据创建人（可为 null）
     */
    public void checkCourseOrCreator(HttpServletRequest request, Long courseId, Long creatorId) {
        if (isAdmin(request)) {
            return;
        }
        if (courseId != null) {
            checkCourse(request, courseId);
            return;
        }
        Long userId = userId(request);
        if (creatorId == null || !creatorId.equals(userId)) {
            throw new ForbiddenException("无权操作：该数据不属于您任教的课程");
        }
    }
}
