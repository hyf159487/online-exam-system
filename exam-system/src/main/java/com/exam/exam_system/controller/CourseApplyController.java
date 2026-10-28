package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.CourseApply;
import com.exam.exam_system.service.CourseApplyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 教师端：课程申请审核（仅任教教师，AuthInterceptor 已限定 role=1）
 *
 * <p>列表/计数只返回当前教师任教课程的申请；审核时校验该教师确为本课程任教教师。
 */
@RestController
@RequestMapping("/api/teacher/course-apply")
public class CourseApplyController {

    @Autowired
    private CourseApplyService courseApplyService;

    /**
     * 申请列表（仅当前教师任教课程的申请）
     * GET /api/teacher/course-apply/list?status=0   status 可空（不传=全部，0=待审核）
     */
    @GetMapping("/list")
    public Result<List<CourseApply>> list(@RequestParam(required = false) Integer status,
                                          HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.listApply(teacherId, status));
    }

    /**
     * 待审核申请数量（当前教师任教课程，教师端角标/未读提示用）
     * GET /api/teacher/course-apply/pending-count
     */
    @GetMapping("/pending-count")
    public Result<Long> pendingCount(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.countPending(teacherId));
    }

    /**
     * 同意申请
     * POST /api/teacher/course-apply/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        try {
            courseApplyService.approve(id, teacherId);
            return Result.success("已同意，学生成功加入课程", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 驳回申请
     * POST /api/teacher/course-apply/{id}/reject?reason=xxx
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestParam(required = false) String reason,
                               HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        if (reason == null || reason.trim().isEmpty()) {
            return Result.error(400, "请填写驳回理由");
        }
        try {
            courseApplyService.reject(id, teacherId, reason);
            return Result.success("已驳回申请", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 审核页统计：待审核申请数量 + 本月已处理申请数量
     * GET /api/teacher/course-apply/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.auditStats(teacherId));
    }

    /**
     * 申请学生基础信息 + 该生在本人任教课程下的申请记录（点击学生姓名查看）
     * GET /api/teacher/course-apply/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public Result<Map<String, Object>> studentDetail(@PathVariable Long studentId,
                                                     HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        try {
            return Result.success("查询成功", courseApplyService.studentDetail(teacherId, studentId));
        } catch (IllegalArgumentException e) {
            return Result.error(403, e.getMessage());
        }
    }

    /**
     * 批量通过申请
     * POST /api/teacher/course-apply/batch-approve   Body: { "ids": [1, 2, 3] }
     *
     * <p>批量驳回需要逐条填写各自的驳回理由，因此只提供批量通过。
     */
    @PostMapping("/batch-approve")
    public Result<Map<String, Object>> batchApprove(@RequestBody(required = false) Map<String, Object> body,
                                                    HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        List<Long> ids = extractIds(body);
        if (ids.isEmpty()) {
            return Result.error(400, "请先勾选要审核的申请");
        }
        Map<String, Object> result = courseApplyService.batchApprove(ids, teacherId);
        long success = ((Number) result.getOrDefault("successCount", 0)).longValue();
        long skipped = ((Number) result.getOrDefault("skippedCount", 0)).longValue();
        String msg = skipped == 0
                ? "已批量通过 " + success + " 条申请，学生已加入对应课程"
                : "已通过 " + success + " 条，跳过 " + skipped + " 条（已被处理或无权审核）";
        return Result.success(msg, result);
    }

    /**
     * 导出课程申请审核记录（Excel）
     * GET /api/teacher/course-apply/export?status=&courseId=
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) Integer status,
                       @RequestParam(required = false) Long courseId,
                       HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        Long teacherId = (Long) request.getAttribute("userId");
        // 数据权限：只导出自己任教课程的申请
        List<CourseApply> list = courseApplyService.listForExport(teacherId, status, courseId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode("课程申请审核记录_" + LocalDate.now(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName + ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("课程申请审核记录");
            String[] headers = {"序号", "学生姓名", "学号", "班级", "申请课程", "申请类型",
                    "任课教师", "申请时间", "申请状态", "审核时间", "驳回理由"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            int seq = 1;
            for (CourseApply ca : list) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(nullToEmpty(ca.getStudentName()));
                row.createCell(2).setCellValue(nullToEmpty(ca.getStudentUsername()));
                row.createCell(3).setCellValue(nullToEmpty(ca.getClassName()));
                row.createCell(4).setCellValue(nullToEmpty(ca.getCourseName()));
                row.createCell(5).setCellValue(ca.getApplyType() != null && ca.getApplyType() == 1 ? "退出" : "加入");
                row.createCell(6).setCellValue(nullToEmpty(ca.getTeacherNames()));
                row.createCell(7).setCellValue(ca.getApplyTime() == null ? "" : ca.getApplyTime().toString());
                row.createCell(8).setCellValue(statusText(ca.getStatus()));
                row.createCell(9).setCellValue(ca.getAuditTime() == null ? "" : ca.getAuditTime().toString());
                row.createCell(10).setCellValue(nullToEmpty(ca.getRejectReason()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, i == 4 || i == 10 ? 24 * 256 : 16 * 256);
            }
            wb.write(os);
            os.flush();
        }
    }

    /** 从 { "ids": [...] } 中解析 id 列表（兼容字符串数字） */
    private List<Long> extractIds(Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        Object raw = body == null ? null : body.get("ids");
        if (raw instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Number n) {
                    ids.add(n.longValue());
                } else if (item != null) {
                    try {
                        ids.add(Long.valueOf(item.toString().trim()));
                    } catch (NumberFormatException ignored) {
                        // 非法 id 直接忽略
                    }
                }
            }
        }
        return ids;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return status == 0 ? "待审核" : status == 1 ? "已通过" : "已驳回";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
