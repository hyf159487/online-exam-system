package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.FavoriteAddRequest;
import com.exam.exam_system.dto.FavoriteQuery;
import com.exam.exam_system.entity.QuestionFavorite;
import com.exam.exam_system.service.QuestionFavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 学生端「我的收藏」Controller
 *
 * <p>所有接口都在 /api/student 下，token 校验与角色校验由 AuthInterceptor 统一处理，
 * 当前学生 userId 从 request 属性取，学生只能操作自己的收藏。
 *
 * <p>收藏与错题本是两套独立集合：错题 = 答错的题（系统自动归集），
 * 收藏 = 自己标记的重点题，两者在侧边菜单并列。
 */
@RestController
@RequestMapping("/api/student/favorites")
public class QuestionFavoriteController {

    @Autowired
    private QuestionFavoriteService questionFavoriteService;

    /** 我的收藏列表（分页 + 学科/题型/题干关键词筛选） */
    @GetMapping
    public Result<PageResult<QuestionFavorite>> list(@ModelAttribute FavoriteQuery q, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", questionFavoriteService.page(userId, q));
    }

    /** 收藏总览：收藏总题数 + 各学科分布 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", questionFavoriteService.overview(userId));
    }

    /** 当前学生已收藏的试题 ID 列表（练习页加载一轮题后标记「已收藏」星标） */
    @GetMapping("/ids")
    public Result<List<Long>> ids(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", questionFavoriteService.listQuestionIds(userId));
    }

    /** 收藏一道题（幂等：已收藏则返回 already=true，前端只需提示） */
    @PostMapping
    public Result<Map<String, Object>> add(@RequestBody FavoriteAddRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (req == null || req.getQuestionId() == null) {
            return Result.error(400, "缺少试题 ID");
        }
        boolean created = questionFavoriteService.add(userId, req.getQuestionId());
        Map<String, Object> data = Map.of("questionId", req.getQuestionId(), "already", !created);
        return Result.success(created ? "收藏成功" : "该题已在收藏中", data);
    }

    /** 取消收藏（按收藏记录 ID，列表页「取消收藏」用） */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        questionFavoriteService.removeById(userId, id);
        return Result.success("已取消收藏", null);
    }

    /** 取消收藏（按试题 ID，练习页「取消收藏」按钮用） */
    @DeleteMapping("/question/{questionId}")
    public Result<Integer> removeByQuestion(@PathVariable Long questionId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int removed = questionFavoriteService.removeByQuestion(userId, questionId);
        return Result.success(removed > 0 ? "已取消收藏" : "该题未收藏", removed);
    }

    /** 批量取消收藏（列表页勾选后批量操作） */
    @PostMapping("/batch-remove")
    public Result<Integer> batchRemove(@RequestBody List<Long> ids, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int removed = questionFavoriteService.batchRemove(userId, ids);
        return Result.success("已取消 " + removed + " 道题的收藏", removed);
    }

    /** 导出当前筛选条件下的收藏（xlsx 文件流下载） */
    @GetMapping("/export")
    public void export(@ModelAttribute FavoriteQuery q, HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        Long userId = (Long) request.getAttribute("userId");
        List<QuestionFavorite> list = questionFavoriteService.listForExport(userId, q);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("我的收藏");
            CellStyle head = headerStyle(wb);
            CellStyle data = dataStyle(wb);
            String[] hs = {"序号", "题干", "学科", "题型", "难度", "正确答案", "解析", "收藏时间"};
            Row hr = s.createRow(0);
            for (int i = 0; i < hs.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(hs[i]);
                c.setCellStyle(head);
            }
            int r = 1;
            for (QuestionFavorite f : list) {
                Row row = s.createRow(r);
                setCell(row, 0, String.valueOf(r), data);
                setCell(row, 1, f.getContent(), data);
                setCell(row, 2, f.getCourseName(), data);
                setCell(row, 3, typeText(f.getQuestionType()), data);
                setCell(row, 4, difficultyText(f.getDifficulty()), data);
                setCell(row, 5, f.getAnswer(), data);
                setCell(row, 6, f.getAnalysis(), data);
                setCell(row, 7, time(f.getCreateTime()), data);
                r++;
            }
            int[] ws = {1200, 12000, 3200, 2200, 2000, 3200, 9000, 4600};
            for (int i = 0; i < ws.length; i++) s.setColumnWidth(i, ws[i]);
            String fn = "我的收藏_" + System.currentTimeMillis() + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fn, StandardCharsets.UTF_8));
            try (OutputStream os = response.getOutputStream()) {
                wb.write(os);
                os.flush();
            }
        }
    }

    private static final String[] TYPES = {"", "单选", "多选", "判断", "填空", "简答", "名词解释"};

    private static String typeText(Integer t) {
        return (t == null || t < 1 || t >= TYPES.length) ? "未知" : TYPES[t];
    }

    /** 难度：1-2 简单 / 3 中等 / 4-5 困难（与前端三档展示保持一致） */
    private static String difficultyText(Integer d) {
        if (d == null || d <= 0) return "-";
        if (d <= 2) return "简单";
        if (d == 3) return "中等";
        return "困难";
    }

    private static String time(java.time.LocalDateTime t) {
        return t == null ? "-" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static void setCell(Row row, int idx, String v, CellStyle style) {
        Cell c = row.createCell(idx);
        c.setCellValue(v == null ? "" : v);
        c.setCellStyle(style);
    }

    private static CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private static CellStyle dataStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        return s;
    }
}
