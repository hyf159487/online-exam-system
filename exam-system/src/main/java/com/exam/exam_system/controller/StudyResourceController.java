package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.StudyResourceQuery;
import com.exam.exam_system.entity.StudyResource;
import com.exam.exam_system.service.StudyResourceService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学习资料管理接口（管理员/教师端）
 *
 * <p>写操作要求当前登录角色 role>=1（教师=1、管理员=2），学生仅能走 /api/student/study-resource 查看下载。
 */
@RestController
@RequestMapping("/api/study-resource")
public class StudyResourceController {

    @Autowired
    private StudyResourceService studyResourceService;

    /** 教师数据权限：学习资料按「任教课程」隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** 上传根目录（与 WebConfig 静态映射 /uploads/** 保持一致） */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    /** 分页查询学习资料 */
    @GetMapping("/list")
    public Result<PageResult<StudyResource>> list(StudyResourceQuery query, jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        // 数据权限：教师只能看到自己任教课程下的资料（管理员为 null，不限制）
        query.setCourseIds(teacherScope.myCourseIds(request));
        return Result.success("查询成功", studyResourceService.page(query));
    }

    /** 查询单个资料详情 */
    @GetMapping("/{id}")
    public Result<StudyResource> detail(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            return Result.error(404, "资料不存在或已删除");
        }
        // 数据权限：不能查看别人任教课程下的资料
        teacherScope.checkCourse(request, r.getCourseId());
        return Result.success("查询成功", r);
    }

    /** 上传资料（multipart：courseId + file + resourceName? + description?） */
    @PostMapping("/upload")
    public Result<StudyResource> upload(
            @RequestParam("courseId") Long courseId,
            @RequestParam(value = "resourceName", required = false) String resourceName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        // 数据权限：只能往自己任教的课程上传资料
        teacherScope.checkCourseRequired(request, courseId);
        try {
            Long userId = (Long) request.getAttribute("userId");
            Object nameAttr = request.getAttribute("realName");
            String teacherName = nameAttr == null ? null : nameAttr.toString();
            StudyResource r = studyResourceService.upload(courseId, resourceName, description, file, userId, teacherName);
            return Result.success("上传成功", r);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(500, "文件保存失败：" + e.getMessage());
        }
    }

    /**
     * 新增外部链接资料（第三方教学视频等站外资源，无需上传文件）
     * POST /api/study-resource/link  body: { courseId, resourceName, linkUrl, description? }
     */
    @PostMapping("/link")
    public Result<StudyResource> addLink(@RequestBody Map<String, Object> body,
                                         jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        Long courseId = body.get("courseId") == null ? null : Long.valueOf(body.get("courseId").toString());
        // 数据权限：只能往自己任教的课程添加资料
        if (courseId != null) {
            teacherScope.checkCourseRequired(request, courseId);
        }
        try {
            Long userId = (Long) request.getAttribute("userId");
            Object nameAttr = request.getAttribute("realName");
            String teacherName = nameAttr == null ? null : nameAttr.toString();
            StudyResource r = studyResourceService.addLink(courseId, str(body.get("resourceName")),
                    str(body.get("linkUrl")), str(body.get("description")), userId, teacherName);
            return Result.success("添加成功", r);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 更新资料（名称/备注/可选换课程；外部链接资料可同时修改链接地址） */
    @PutMapping("/{id}")
    public Result<Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                 jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        // 数据权限①：原资料必须属于自己任教的课程
        checkResourceScope(id, request);
        try {
            String resourceName = str(body.get("resourceName"));
            String description = str(body.get("description"));
            Long courseId = body.get("courseId") == null ? null : Long.valueOf(body.get("courseId").toString());
            // 数据权限②：换课程时，目标课程必须也是自己任教的
            if (courseId != null) {
                teacherScope.checkCourse(request, courseId);
            }
            studyResourceService.update(id, resourceName, description, courseId);
            // 外部链接资料允许同时修改链接地址（非链接资料传了也会被 Service 拒绝）
            String linkUrl = str(body.get("linkUrl"));
            if (linkUrl != null && !linkUrl.isBlank()) {
                studyResourceService.updateLinkUrl(id, linkUrl);
            }
            return Result.success("修改成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 删除单个资料 */
    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        // 数据权限：只能删自己任教课程下的资料
        checkResourceScope(id, request);
        try {
            studyResourceService.delete(id);
            return Result.success("删除成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 批量删除资料 */
    @DeleteMapping("/batch")
    public Result<Map<String, Object>> deleteBatch(@RequestParam("ids") List<Long> ids,
                                                   jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        // 数据权限：整批资料必须都属于自己任教的课程（一条越权则整批拒绝）
        if (!teacherScope.isAdmin(request) && ids != null && !ids.isEmpty()) {
            List<Long> courseIds = new java.util.ArrayList<>(ids.size());
            for (Long rid : ids) {
                StudyResource r = studyResourceService.getById(rid);
                if (r != null) {
                    courseIds.add(r.getCourseId());
                }
            }
            teacherScope.checkCourses(request, courseIds);
        }
        Map<String, Object> result = studyResourceService.deleteBatch(ids);
        return Result.success("批量删除完成", result);
    }

    /** 下载资料 */
    @GetMapping("/download/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id,
                                                       jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return ResponseEntity.status(403).build();
        }
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        // 数据权限：教师只能下载自己任教课程下的资料
        if (!teacherScope.canAccessCourse(request, r.getCourseId())) {
            return ResponseEntity.status(403).build();
        }
        File target = locateFile(r.getFilePath());
        if (!target.exists()) {
            return ResponseEntity.notFound().build();
        }
        // 下载成功才累计下载次数
        studyResourceService.incrementDownloadCount(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(r.getResourceName(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(new FileSystemResource(target));
    }

    /** 在线预览资料（当前仅 PDF：inline + application/pdf；其它类型返回 400） */
    @GetMapping("/preview/{id}")
    public ResponseEntity<FileSystemResource> preview(@PathVariable Long id,
                                                      jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return ResponseEntity.status(403).build();
        }
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        // 数据权限：教师只能预览自己任教课程下的资料
        if (!teacherScope.canAccessCourse(request, r.getCourseId())) {
            return ResponseEntity.status(403).build();
        }
        if (!"pdf".equals(r.getFileType())) {
            return ResponseEntity.badRequest().build();
        }
        File target = locateFile(r.getFilePath());
        if (!target.exists()) {
            return ResponseEntity.notFound().build();
        }
        // 通过本接口预览时累计浏览次数
        studyResourceService.incrementViewCount(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(r.getResourceName(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(new FileSystemResource(target));
    }

    /**
     * 记录一次浏览：前端以静态地址（/uploads/...）打开 PDF 预览，
     * 不再经过本 preview 接口，因此在打开前先调用本接口累计浏览次数。
     */
    @PostMapping("/view/{id}")
    public Result<Void> recordView(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        if (!canManage(request)) {
            return Result.error(403, "无权限");
        }
        // 数据权限：教师只能对自任教课程下的资料计浏览
        checkResourceScope(id, request);
        studyResourceService.incrementViewCount(id);
        return Result.success();
    }

    /** 导出资料清单（Excel） */
    @GetMapping("/export")
    public void export(StudyResourceQuery query, jakarta.servlet.http.HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        if (!canManage(request)) {
            response.sendError(403);
            return;
        }
        // 数据权限：教师只能导出自己任教课程下的资料
        query.setCourseIds(teacherScope.myCourseIds(request));
        List<StudyResource> list = studyResourceService.listForExport(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode("学习资料清单_" + LocalDate.now(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName + ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("学习资料清单");
            String[] headers = {"序号", "资料名称", "所属课程", "文件类型", "大小", "上传人", "上传时间", "备注"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
            }
            int rowIdx = 1;
            int seq = 1;
            for (StudyResource r : list) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(nullToEmpty(r.getResourceName()));
                row.createCell(2).setCellValue(nullToEmpty(r.getCourseName()));
                row.createCell(3).setCellValue(fileTypeText(r.getFileType()));
                row.createCell(4).setCellValue(nullToEmpty(r.getFileSizeText()));
                row.createCell(5).setCellValue(nullToEmpty(r.getUploadTeacherName()));
                row.createCell(6).setCellValue(r.getCreateTime() == null ? "" : r.getCreateTime().toString());
                row.createCell(7).setCellValue(nullToEmpty(r.getDescription()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, i == 1 ? 30 * 256 : 18 * 256);
            }
            wb.write(os);
        }
    }

    private boolean canManage(jakarta.servlet.http.HttpServletRequest request) {
        Object roleAttr = request.getAttribute("role");
        if (roleAttr == null) {
            return false;
        }
        return Integer.parseInt(roleAttr.toString()) >= 1;
    }

    /**
     * 数据权限：教师只能操作自己任教课程下的资料（管理员放行）。
     *
     * <p>越权时抛 {@code ForbiddenException}，由全局异常处理器统一转成 code=403。
     */
    private void checkResourceScope(Long id, jakarta.servlet.http.HttpServletRequest request) {
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            throw new com.exam.exam_system.common.BizException("资料不存在或已删除");
        }
        teacherScope.checkCourse(request, r.getCourseId());
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 资料类型中文名（导出清单用，link=外部链接） */
    private String fileTypeText(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return "";
        }
        return switch (fileType) {
            case "pdf" -> "PDF";
            case "word" -> "Word";
            case "ppt" -> "PPT";
            case "excel" -> "Excel";
            case "text" -> "文本";
            case "compress" -> "压缩包";
            case "image" -> "图片";
            case "video" -> "视频";
            case "audio" -> "音频";
            case "link" -> "外部链接";
            default -> fileType;
        };
    }

    private File locateFile(String filePath) {
        String rel = filePath.startsWith("/uploads/") ? filePath.substring("/uploads/".length()) : filePath;
        return new File(uploadDir, rel);
    }
}
