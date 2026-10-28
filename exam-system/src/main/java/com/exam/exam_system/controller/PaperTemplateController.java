package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.PaperTemplateVO;
import com.exam.exam_system.entity.PaperTemplate;
import com.exam.exam_system.service.PaperTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 组卷模板 Controller
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /api/paper-template/list         分页查询模板列表</li>
 *   <li>GET    /api/paper-template/{id}         获取模板详情（含 templateJson）</li>
 *   <li>POST   /api/paper-template              新增模板</li>
 *   <li>PUT    /api/paper-template              修改模板</li>
 *   <li>DELETE /api/paper-template/{id}         删除模板</li>
 * </ul>
 *
 * <p>权限：
 * <ul>
 *   <li>教师(role=1)：可新增/编辑/删除自己的模板，可查看所有模板</li>
 *   <li>管理员(role=2)：可查看/删除全部模板</li>
 *   <li>学生(role=0)：无访问权限</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/paper-template")
public class PaperTemplateController {

    @Autowired
    private PaperTemplateService templateService;

    /** 教师数据权限：模板需校验所属课程是否归当前教师任教 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /**
     * 分页查询模板列表
     */
    @GetMapping("/list")
    public Result<PageResult<PaperTemplateVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) Long courseId,
            HttpServletRequest request) {

        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }

        Long userId = (Long) request.getAttribute("userId");

        // 教师：只看自己创建的；管理员：看全部
        PageResult<PaperTemplateVO> result = templateService.page(
                pageNum, pageSize, templateName, courseId,
                role == 1 ? userId : null, role);
        return Result.success("查询成功", result);
    }

    /**
     * 获取单个模板详情（含 templateJson，用于前端回填自动组卷表单）
     */
    @GetMapping("/{id}")
    public Result<PaperTemplate> detail(@PathVariable Long id,
                                         HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        PaperTemplate template = templateService.getById(id);
        if (template == null) {
            return Result.error(404, "模板不存在");
        }
        // 权限校验：教师只能查看自己的模板
        Long userId = (Long) request.getAttribute("userId");
        if (role == 1 && !template.getCreatorId().equals(userId)) {
            return Result.error(403, "无权查看该模板");
        }
        return Result.success("查询成功", template);
    }

    /**
     * 新增模板
     */
    @PostMapping
    public Result<Long> add(@RequestBody PaperTemplate template,
                            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            return Result.error(400, "模板名称不能为空");
        }
        normalizePaperType(template);
        Long userId = (Long) request.getAttribute("userId");
        template.setCreatorId(userId);
        // 数据权限：模板关联了课程时，该课程必须由自己任教
        //（未关联课程 = 个人通用模板，靠 creatorId 隔离，不允许别人看到/修改）
        if (template.getCourseId() != null) {
            teacherScope.checkCourse(request, template.getCourseId());
        }
        templateService.add(template);
        return Result.success("新增成功", template.getId());
    }

    /**
     * 修改模板
     */
    @PutMapping
    public Result<Void> update(@RequestBody PaperTemplate template,
                                HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        if (template.getId() == null) {
            return Result.error(400, "模板ID不能为空");
        }
        PaperTemplate existing = templateService.getById(template.getId());
        if (existing == null) {
            return Result.error(404, "模板不存在");
        }
        normalizePaperType(template);
        // 教师只能修改自己的模板
        Long userId = (Long) request.getAttribute("userId");
        if (role == 1 && (existing.getCreatorId() == null || !existing.getCreatorId().equals(userId))) {
            return Result.error(403, "无权修改该模板");
        }
        // 数据权限①：原模板关联的课程必须由自己任教
        if (existing.getCourseId() != null) {
            teacherScope.checkCourse(request, existing.getCourseId());
        }
        // 数据权限②：不允许把模板改挂到别人任教的课程下
        if (template.getCourseId() != null) {
            teacherScope.checkCourse(request, template.getCourseId());
        }
        templateService.update(template);
        return Result.success("修改成功", null);
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                                HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        PaperTemplate existing = templateService.getById(id);
        if (existing == null) {
            return Result.error(404, "模板不存在");
        }
        // 教师只能删除自己的模板
        Long userId = (Long) request.getAttribute("userId");
        if (role == 1 && !existing.getCreatorId().equals(userId)) {
            return Result.error(403, "无权删除该模板");
        }
        templateService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 组卷方式兜底与校验：空值或非法值一律按 2 随机组卷处理
     * （历史模板存的就是随机抽题明细，行为保持不变）
     */
    private void normalizePaperType(PaperTemplate template) {
        Integer type = template.getPaperType();
        if (type == null || type < 1 || type > 3) {
            template.setPaperType(2);
        }
    }
}
