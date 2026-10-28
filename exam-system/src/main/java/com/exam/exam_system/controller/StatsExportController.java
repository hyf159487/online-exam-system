package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.ReportPreviewVO;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.service.StatsExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 学情报表导出 Controller
 */
@RestController
@RequestMapping("/api/stats/export")
public class StatsExportController {

    @Autowired
    private StatsExportService statsExportService;

    @Autowired
    private ExamMapper examMapper;

    /** 教师数据权限：报表导出按考试的任教课程隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /**
     * 学情报表导出（Excel 文件流下载）
     *
     * @param examId    考试 ID（必选）
     * @param classId   班级 ID（可选）
     * @param reportType 报表类型：1 成绩 / 2 班级学情 / 3 试题质量
     */
    @GetMapping
    public void export(@RequestParam Long examId,
                       @RequestParam(required = false) Long classId,
                       @RequestParam Integer reportType,
                       HttpServletRequest request,
                       HttpServletResponse response) {

        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        if (role == null || (role != 1 && role != 2)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":403,\"msg\":\"仅教师或管理员可访问\"}");
            } catch (Exception ignored) {}
            return;
        }

        if (examId == null || reportType == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":400,\"msg\":\"缺少必要参数\"}");
            } catch (Exception ignored) {}
            return;
        }

        // 权限校验：教师只能导出自己发布的考试
        if (role == 1) {
            Exam exam = examMapper.selectById(examId);
            if (exam == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":404,\"msg\":\"考试不存在\"}");
                } catch (Exception ignored) {}
                return;
            }
            // 数据权限：自己创建的历史数据 或 属于自己任教课程的考试，都允许导出
            boolean selfCreated = exam.getCreatorId() != null && exam.getCreatorId().equals(userId);
            boolean ownCourse = teacherScope.canAccessCourse(request, exam.getCourseId());
            if (!selfCreated && !ownCourse) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":403,\"msg\":\"无权导出该考试的报表\"}");
                } catch (Exception ignored) {}
                return;
            }
        }

        try {
            switch (reportType) {
                case 1:
                    statsExportService.exportStudentScoreReport(examId, classId, userId, role, response);
                    break;
                case 2:
                    statsExportService.exportClassStatsReport(examId, classId, userId, role, response);
                    break;
                case 3:
                    statsExportService.exportQuestionQualityReport(examId, userId, role, response);
                    break;
                default:
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":400,\"msg\":\"不支持的报表类型\"}");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":500,\"msg\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ignored) {}
        }
    }

    /**
     * 报表预览（JSON）：选择筛选条件后即时查看报表内容，与导出的 Excel 完全一致。
     *
     * <p>权限口径与 export 保持一致：教师只能预览自己创建或任教课程下的考试，
     * 管理员不受限制。</p>
     *
     * @param examId     考试 ID（必选）
     * @param classId    班级 ID（可选，仅成绩报表 / 班级统计报表使用）
     * @param reportType 报表类型：1 成绩 / 2 班级学情 / 3 试题质量
     */
    @GetMapping("/preview")
    public Result<ReportPreviewVO> preview(@RequestParam Long examId,
                                          @RequestParam(required = false) Long classId,
                                          @RequestParam Integer reportType,
                                          HttpServletRequest request) {

        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        if (examId == null || reportType == null) {
            return Result.error(400, "缺少必要参数");
        }

        if (role == 1) {
            Exam exam = examMapper.selectById(examId);
            if (exam == null) {
                return Result.error(404, "考试不存在");
            }
            boolean selfCreated = exam.getCreatorId() != null && exam.getCreatorId().equals(userId);
            boolean ownCourse = teacherScope.canAccessCourse(request, exam.getCourseId());
            if (!selfCreated && !ownCourse) {
                return Result.error(403, "无权查看该考试的报表");
            }
        }

        try {
            return Result.success("查询成功", statsExportService.previewReport(examId, classId, reportType));
        } catch (Exception e) {
            return Result.error(500, "预览失败：" + e.getMessage());
        }
    }
}
