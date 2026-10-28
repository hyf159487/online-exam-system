package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.PracticeAttachment;
import com.exam.exam_system.dto.PracticeAttachmentReview;
import com.exam.exam_system.dto.PracticePaperSaveRequest;
import com.exam.exam_system.dto.PracticeReviewItem;
import com.exam.exam_system.dto.RandomComposeRequest;
import com.exam.exam_system.entity.PracticeAnswer;
import com.exam.exam_system.entity.PracticePaper;
import com.exam.exam_system.entity.PracticeRecord;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.PracticePaperService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 练习卷管理（教师端）Controller
 *
 * <p>权限说明：全部接口仅教师（sys_user.role = 1）可访问；管理员端已下线该模块
 * （数据库侧回收 role_id = 3 的菜单授权，见
 * database/migrate_remove_admin_practice_menu.sql）。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /api/practice/paper/list     练习卷分页列表</li>
 *   <li>GET    /api/practice/paper/detail/{id}  练习卷详情（含题目）</li>
 *   <li>POST   /api/practice/paper/save     新增/编辑练习卷（作业内容：题库选题 或 上传作业文件）</li>
 *   <li>POST   /api/practice/paper/upload   上传作业附件文件（文件型作业，返回元数据后随 save 提交）</li>
 *   <li>PUT    /api/practice/paper/publish/{id} 发布/停用（status 1/2）</li>
 *   <li>DELETE /api/practice/paper/delete/{id}  逻辑删除</li>
 *   <li>GET    /api/practice/record/list    练习记录列表</li>
 *   <li>GET    /api/practice/record/detail/{recordId} 答题详情</li>
 *   <li>GET    /api/practice/review/list    批改作业列表（按批改状态筛选）</li>
 *   <li>POST   /api/practice/review/{recordId} 提交批改（主观题逐题给分 + 评语）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/practice")
public class PracticePaperController {

    @Autowired
    private PracticePaperService practicePaperService;

    /** 教师数据权限：练习卷的课程归属校验 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** 上传根目录（与 application.properties 的 upload.dir 一致）：把库里的 /uploads/** 还原成磁盘文件 */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    // ================= 练习卷 =================

    /**
     * 练习卷分页列表
     *
     * <p>角色：教师（只看自己发布的）/ 管理员（只读，看全部）。
     * 管理员端没有作业管理菜单，但「课程管理 → 课程信息 → 课程作业」需要只读展示该课程的作业，
     * 因此这里放开只读；新增/编辑/发布/删除/详情等写与明细接口仍仅教师可用。
     */
    @GetMapping("/paper/list")
    public Result<PageResult<PracticePaper>> listPaper(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role < 1) {
            return Result.error(403, "仅教师/管理员可访问该功能");
        }
        PageResult<PracticePaper> result = practicePaperService.listPaper(
                pageNum, pageSize, userId, role, keyword, courseId, status, null);
        return Result.success("查询成功", result);
    }

    @GetMapping("/paper/detail/{id}")
    public Result<PracticePaper> detail(@PathVariable Long id,
                                        HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        PracticePaper paper = practicePaperService.detail(id);
        if (paper == null) {
            return Result.error(404, "练习卷不存在或已删除");
        }
        if (role == 1 && !paper.getCreateTeacherId().equals(userId)) {
            return Result.error(403, "无权查看他人创建的练习卷");
        }
        return Result.success("查询成功", paper);
    }

    /**
     * 随机选题：按「题型 + 难度 + 数量」规则从题库随机抽题，返回题目列表（不落库），供教师新增作业选题使用
     * POST /api/practice/paper/random-pick
     * 请求体（JSON）：
     * {
     *   "courseId": 1,
     *   "rules": [
     *     {"questionType": 1, "difficulty": 2, "count": 10, "score": 2.0},
     *     {"questionType": 5, "difficulty": null, "count": 2, "score": 10.0}
     *   ]
     * }
     */
    @PostMapping("/paper/random-pick")
    public Result<List<Question>> randomPick(@RequestBody RandomPickReq req,
                                             HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        // 数据权限：只能从自己任教课程的题库里抽题
        teacherScope.checkCourseRequired(request, req.getCourseId());
        List<Question> list = practicePaperService.pickRandom(req.getCourseId(), req.getRules());
        return Result.success("抽取成功，共 " + list.size() + " 题", list);
    }

    @PostMapping("/paper/save")
    public Result<Long> save(@RequestBody PracticePaperSaveRequest req,
                             HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        // 数据权限：练习卷必须挂在自己任教的课程下（编辑时前端会带上原 courseId）
        if (req.getCourseId() != null) {
            teacherScope.checkCourse(request, req.getCourseId());
        }
        try {
            Long id = practicePaperService.savePaper(req, userId, null, role);
            return Result.success("保存成功", id);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 上传作业附件（文件型作业）
     * POST /api/practice/paper/upload
     *
     * <p>作业内容可以是「从题库选题」，也可以是「上传一份作业文件」：
     * 本接口只负责把文件落盘（uploads/homework/yyyyMM/）并返回元数据，
     * 前端拿到 filePath 后在 /paper/save 时一并提交，由后端写入 practice_paper。
     *
     * @param file 作业文件（docx/xlsx/xls/pdf/ppt/图片/压缩包等）
     */
    @PostMapping("/paper/upload")
    public Result<PracticeAttachment> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        try {
            return Result.success("上传成功", practicePaperService.uploadAttachment(file));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    @PutMapping("/paper/publish/{id}")
    public Result<?> publish(@PathVariable Long id,
                             @RequestParam Integer status,
                             HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        try {
            practicePaperService.publish(id, status, userId, role);
            String msg = (status == 1) ? "发布成功" : "停用成功";
            return Result.success(msg);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/paper/delete/{id}")
    public Result<?> delete(@PathVariable Long id,
                            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        try {
            practicePaperService.deletePaper(id, userId, role);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ================= 练习记录 =================

    @GetMapping("/record/list")
    public Result<PageResult<PracticeRecord>> listRecord(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        PageResult<PracticeRecord> result = practicePaperService.listRecord(
                pageNum, pageSize, userId, role, paperId, studentId, keyword);
        return Result.success("查询成功", result);
    }

    @GetMapping("/record/detail/{recordId}")
    public Result<List<PracticeAnswer>> recordDetail(@PathVariable Long recordId,
                                                     HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        try {
            List<PracticeAnswer> list = practicePaperService.recordDetail(recordId, userId, role);
            return Result.success("查询成功", list);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ================= 批改作业 =================

    /**
     * 批改作业列表：教师只能看到自己发布作业的提交记录，reviewStatus 支持 1待批改 / 2已批改 / 0无需批改
     */
    @GetMapping("/review/list")
    public Result<PageResult<PracticeRecord>> listReview(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer reviewStatus,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        PageResult<PracticeRecord> result = practicePaperService.listReview(
                pageNum, pageSize, userId, role, paperId, keyword, reviewStatus);
        return Result.success("查询成功", result);
    }

    /**
     * 提交批改：请求体为主观题批改项数组
     * [{"answerId": 1, "reviewScore": 8, "reviewOpinion": "思路正确，注意书写规范"}]
     */
    @PostMapping("/review/{recordId}")
    public Result<?> submitReview(@PathVariable Long recordId,
                                  @RequestBody List<PracticeReviewItem> items,
                                  HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        try {
            practicePaperService.submitReview(recordId, items, userId, role);
            return Result.success("批改成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 批改文件型作业提交（学生上传的作业文件，列表里「提交文件」有链接的记录）
     * POST /api/practice/review/attachment/{recordId}
     * body: {"score": 88, "opinion": "步骤完整，注意排版"}
     */
    @PostMapping("/review/attachment/{recordId}")
    public Result<?> reviewAttachmentSubmit(@PathVariable Long recordId,
                                            @RequestBody PracticeAttachmentReview body,
                                            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return Result.error(403, "仅教师可访问该功能");
        }
        try {
            practicePaperService.reviewAttachmentSubmit(recordId,
                    body == null ? null : body.getScore(),
                    body == null ? null : body.getOpinion(),
                    userId, role);
            return Result.success("批改成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 下载学生提交的作业文件（批改作业列表 / 批改弹窗里的「下载」按钮）
     * GET /api/practice/review/attachment/{recordId}/download
     *
     * <p>以学生上传时的原始文件名返回（Content-Disposition: attachment），
     * 前端拿到 blob 后触发浏览器下载；教师只能下载自己发布作业的提交。</p>
     */
    @GetMapping("/review/attachment/{recordId}/download")
    public ResponseEntity<FileSystemResource> downloadRecordAttachment(@PathVariable Long recordId,
                                                                      HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || role != 1) {
            return ResponseEntity.status(403).build();
        }
        PracticeRecord record;
        try {
            record = practicePaperService.loadSubmittedAttachment(recordId, userId, role);
        } catch (Exception e) {
            // 记录不存在 / 不是文件型提交 / 无权下载：统一按无权限处理，避免暴露他人数据
            return ResponseEntity.status(403).build();
        }
        File target = locateUploadFile(record.getAttachmentPath());
        if (target == null || !target.isFile()) {
            return ResponseEntity.notFound().build();
        }
        String fileName = (record.getAttachmentName() == null || record.getAttachmentName().isBlank())
                ? target.getName() : record.getAttachmentName();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(target));
    }

    /**
     * 把库里存的访问路径（/uploads/homework/...）解析成磁盘文件。
     * 只接受 /uploads/ 前缀，并强制解析结果落在上传目录内，防止 ../ 目录穿越。
     */
    private File locateUploadFile(String filePath) {
        if (filePath == null || filePath.isBlank() || !filePath.startsWith("/uploads/")) {
            return null;
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(filePath.substring("/uploads/".length())).normalize();
        if (!target.startsWith(base)) {
            return null;
        }
        return target.toFile();
    }

    /** 练习卷随机选题请求体 */
    @Data
    public static class RandomPickReq {
        private Long courseId;
        private List<RandomComposeRequest.Rule> rules;
    }
}
