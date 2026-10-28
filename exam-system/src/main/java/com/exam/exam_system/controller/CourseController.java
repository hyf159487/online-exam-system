package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.Course;
import com.exam.exam_system.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 课程（学科）Controller
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET    /api/course/list        查全部学科（题库筛选、新增题目下拉框用）</li>
 *     <li>POST   /api/course             新增课程（仅管理员）</li>
 *     <li>PUT    /api/course/{id}        更新课程（仅管理员）</li>
 *     <li>DELETE /api/course/{id}        删除课程（仅管理员，有引用时拒绝）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

    /** 教师数据权限：下拉框只展示「我任教的课程」，与列表/详情的数据隔离保持一致 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /**
     * 查学科列表
     * GET /api/course/list
     *
     * <p>数据权限：教师只返回自己任教的课程（下拉筛选/新增时不会出现别人的学科），
     * 管理员与学生不受限制。
     */
    @GetMapping("/list")
    public Result<List<Course>> list(HttpServletRequest request) {
        List<Course> courses = filterByScope(courseService.listAll(), request);
        return Result.success("查询成功", courses);
    }

    /**
     * 关键字模糊查询学科（下拉远端搜索用）
     * GET /api/course/search?keyword=xx&limit=50
     * keyword 为空时返回前 limit 条；按课程名称/编码 LIKE 匹配
     *
     * <p>数据权限同 {@link #list(HttpServletRequest)}：教师只能搜到自己任教的课程。
     */
    @GetMapping("/search")
    public Result<List<Course>> search(@RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "50") int limit,
                                       HttpServletRequest request) {
        List<Course> courses = filterByScope(courseService.search(keyword, limit), request);
        return Result.success("查询成功", courses);
    }

    /**
     * 按数据权限过滤课程列表：教师仅保留自己任教的课程，其余角色（管理员/学生）原样返回。
     *
     * <p>注意只对教师生效：学生端下拉走的是「我已加入的课程」，若在这里按"任教课程"过滤会全被清空。
     */
    private List<Course> filterByScope(List<Course> courses, HttpServletRequest request) {
        if (!teacherScope.isTeacher(request) || courses == null || courses.isEmpty()) {
            return courses;
        }
        List<Long> mine = teacherScope.myCourseIds(request);
        List<Course> result = new java.util.ArrayList<>(courses.size());
        for (Course c : courses) {
            if (c != null && c.getId() != null && mine.contains(c.getId())) {
                result.add(c);
            }
        }
        return result;
    }

    /** 新增课程 POST /api/course（仅管理员） */
    @PostMapping
    public Result<Course> create(@RequestBody Course course, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        return Result.success("新增成功", courseService.create(course));
    }

    /** 更新课程 PUT /api/course/{id}（仅管理员） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Course course,
                               HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        course.setId(id);
        courseService.update(course);
        return Result.success("更新成功", null);
    }

    /**
     * 删除课程 DELETE /api/course/{id}?force=false（仅管理员）
     * force=false（默认）：有试题/试卷/考试/知识点引用时拒绝
     * force=true：仅剩知识点引用时自动连带清理知识点后删除（页面"一键清理并删除"用）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean force,
                               HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        courseService.delete(id, force);
        return Result.success("删除成功", null);
    }

    /**
     * 课程详情 GET /api/course/{id}
     * 基础信息 + 任课教师列表 + 试题/试卷/考试/知识点数量统计（教师/管理员可查看）
     */
    @GetMapping("/{id}")
    public Result<java.util.Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role < 1) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        return Result.success("查询成功", courseService.getDetail(id));
    }

    /**
     * Excel 批量导入课程（仅管理员）
     * POST /api/course/importExcel  form-data: file=xxx.xlsx
     * 列：课程名称 | 课程编码 | 课程描述（第一行表头跳过）
     */
    @PostMapping("/importExcel")
    public Result<java.util.Map<String, Object>> importExcel(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择 Excel 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls"))) {
            return Result.error(400, "请上传 .xlsx 格式的 Excel 文件");
        }
        try {
            java.util.Map<String, Object> result = courseService.importExcel(file);
            int success = (int) result.get("success");
            int failed = (int) result.get("failed");
            String msg = "导入完成：成功 " + success + " 门";
            if (failed > 0) {
                msg += "，失败 " + failed + " 门";
            }
            return Result.success(msg, result);
        } catch (Exception e) {
            return Result.error(500, "导入失败：" + e.getMessage());
        }
    }

    /**
     * 上传课程封面图片（教师 / 管理员）
     * POST /api/course/cover  form-data: file=xxx.png
     *
     * <p>返回可访问路径（形如 /uploads/course-cover/202609/xxx.png），
     * 前端把它放进课程新增/编辑表单的 cover 字段一起提交。
     *
     * <p>教师端「我的课程 → 课程信息」也需要换封面，因此这里放开到教师（role≥1）；
     * 上传只落盘图片、不写业务数据，课程归属校验在保存课程信息时进行。
     */
    @PostMapping("/cover")
    public Result<String> uploadCover(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role < 1) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择要上传的封面图片");
        }
        try {
            return Result.success("上传成功", courseService.uploadCover(file));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "封面上传失败：" + e.getMessage());
        }
    }

    /**
     * 批量删除课程（仅管理员，部分失败策略）
     * DELETE /api/course/batch?ids=1,2,3
     * 被引用的课程逐条跳过并报告原因；无引用的真删
     */
    @DeleteMapping("/batch")
    public Result<java.util.Map<String, Object>> deleteBatch(@RequestParam String ids,
                                                              HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        java.util.List<Long> idList = new java.util.ArrayList<>();
        for (String s : ids.split(",")) {
            if (!s.trim().isEmpty()) {
                try { idList.add(Long.valueOf(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        if (idList.isEmpty()) {
            return Result.error(400, "请选择要删除的课程");
        }
        java.util.Map<String, Object> result = courseService.deleteBatch(idList);
        int success = (int) result.get("success");
        int failed = (int) result.get("failed");
        String msg = "批量删除完成：成功 " + success + " 门";
        if (failed > 0) msg += "，跳过 " + failed + " 门（被业务数据引用）";
        return Result.success(msg, result);
    }

    /**
     * 一键随机分配任课教师（仅管理员）
     * POST /api/course/assign-teachers
     * body: { courseIds?: [..] 为空=全部课程, minCount?: 2, maxCount?: 3, skipBound?: true }
     * 从全部启用教师中随机为每门课程分配 minCount~maxCount 名教师
     */
    @PostMapping("/assign-teachers")
    public Result<java.util.Map<String, Object>> assignTeachers(
            @RequestBody(required = false) com.exam.exam_system.dto.AssignTeacherRequest req,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        if (req == null) {
            req = new com.exam.exam_system.dto.AssignTeacherRequest();
        }
        int min = (req.getMinCount() == null) ? 2 : req.getMinCount();
        int max = (req.getMaxCount() == null) ? 3 : req.getMaxCount();
        boolean skipBound = (req.getSkipBound() == null) || req.getSkipBound();
        try {
            java.util.Map<String, Object> result = courseService.assignTeachersRandomly(
                    req.getCourseIds(), min, max, skipBound);
            int success = (int) result.get("success");
            int skipped = (int) result.get("skipped");
            String msg = "分配完成：成功 " + success + " 门";
            if (skipped > 0) {
                msg += "，跳过 " + skipped + " 门（已绑定教师）";
            }
            return Result.success(msg, result);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
