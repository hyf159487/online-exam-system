package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.StudyResource;
import com.exam.exam_system.service.StudyResourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 学习资料查询/下载接口（学生端）
 *
 * <p>走 /api/student/**，由拦截器限制仅学生（role=0）可访问；学生仅能查看+下载，不能增删改。
 */
@RestController
@RequestMapping("/api/student/study-resource")
public class StudentStudyResourceController {

    @Autowired
    private StudyResourceService studyResourceService;

    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    /** 学生已加入课程的学习资料（courseId 可空=全部已加入课程；填了则只看该课程） */
    @GetMapping("/list")
    public Result<List<StudyResource>> list(@RequestParam(required = false) Long courseId,
                                            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", studyResourceService.listByStudentJoined(studentId, courseId));
    }

    /** 下载资料 */
    @GetMapping("/download/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        String rel = r.getFilePath().startsWith("/uploads/")
                ? r.getFilePath().substring("/uploads/".length()) : r.getFilePath();
        File target = new File(uploadDir, rel);
        if (!target.exists()) {
            return ResponseEntity.notFound().build();
        }
        // 学生下载资料累计下载次数
        studyResourceService.incrementDownloadCount(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(r.getResourceName(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(new FileSystemResource(target));
    }

    /** 在线预览资料（仅 PDF） */
    @GetMapping("/preview/{id}")
    public ResponseEntity<FileSystemResource> preview(@PathVariable Long id) {
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"pdf".equals(r.getFileType())) {
            return ResponseEntity.badRequest().build();
        }
        String rel = r.getFilePath().startsWith("/uploads/")
                ? r.getFilePath().substring("/uploads/".length()) : r.getFilePath();
        File target = new File(uploadDir, rel);
        if (!target.exists()) {
            return ResponseEntity.notFound().build();
        }
        // 学生在线预览累计浏览次数
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
     * 记录一次浏览：外部链接资料点击「去学习」时调用，累计浏览次数。
     * <p>PDF 在线预览由 preview 接口内部计数，不需要调用本接口。
     */
    @PostMapping("/view/{id}")
    public Result<Void> recordView(@PathVariable Long id, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        StudyResource r = studyResourceService.getById(id);
        if (r == null) {
            return Result.error(404, "资料不存在或已删除");
        }
        // 只能对「自己已加入课程」的资料计浏览（未加入课程时列表本就查不到）
        if (studyResourceService.listByStudentJoined(studentId, r.getCourseId()).isEmpty()) {
            return Result.error(403, "无权限");
        }
        studyResourceService.incrementViewCount(id);
        return Result.success();
    }
}
