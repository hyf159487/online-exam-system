package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;
import com.exam.exam_system.dto.WrongQuery;
import com.exam.exam_system.dto.WrongRedoRequest;
import com.exam.exam_system.dto.WrongRedoResult;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.service.AiExplainService;
import com.exam.exam_system.service.WrongQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 学生端错题本 Controller
 *
 * <p>所有接口都在 /api/student 下，token 校验由 AuthInterceptor 统一处理，
 * 当前学生 userId 从 request 属性取，学生只能操作自己的错题本。
 */
@RestController
@RequestMapping("/api/student")
public class WrongQuestionController {

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private AiExplainService aiExplainService;

    /** 错题本列表（分页 + 按学科筛选） */
    @GetMapping("/wrong-questions")
    public Result<PageResult<WrongQuestion>> list(@ModelAttribute WrongQuery q, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", wrongQuestionService.page(userId, q));
    }

    /** 按学科统计错题数量 */
    @GetMapping("/wrong-questions/stats")
    public Result<List<Map<String, Object>>> stats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", wrongQuestionService.stats(userId));
    }

    /** 错题本总览：错题总数 / 已做对题数 / 累计答错次数 / 各学科分布 */
    @GetMapping("/wrong-questions/overview")
    public Result<Map<String, Object>> overview(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", wrongQuestionService.overview(userId));
    }

    /** 重做正确后，从错题本移除一条 */
    @DeleteMapping("/wrong-questions/{id}")
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        wrongQuestionService.remove(userId, id);
        return Result.success("已从错题本移除", null);
    }

    /** 批量移除错题（错题本勾选后批量操作） */
    @PostMapping("/wrong-questions/batch-remove")
    public Result<Integer> batchRemove(@RequestBody List<Long> ids, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int removed = wrongQuestionService.batchRemove(userId, ids);
        return Result.success("已移除 " + removed + " 道错题", removed);
    }

    /** 错题本重做：提交答案，按学生策略判定是否移出 */
    @PostMapping("/wrong-questions/{id}/redo")
    public Result<WrongRedoResult> redo(@PathVariable Long id, @RequestBody WrongRedoRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("提交成功", wrongQuestionService.redo(userId, id, req));
    }

    /** 导出当前筛选条件下的错题（xlsx 文件流下载） */
    @GetMapping("/wrong-questions/export")
    public void export(@ModelAttribute WrongQuery q, HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        Long userId = (Long) request.getAttribute("userId");
        List<WrongQuestion> list = wrongQuestionService.listForExport(userId, q);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("我的错题");
            CellStyle head = headerStyle(wb);
            CellStyle data = dataStyle(wb);
            String[] hs = {"序号", "题干", "学科", "题型", "我的答案", "正确答案",
                    "错误次数", "连续答对次数", "最近错误时间", "上次作答时间"};
            Row hr = s.createRow(0);
            for (int i = 0; i < hs.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(hs[i]);
                c.setCellStyle(head);
            }
            int r = 1;
            for (WrongQuestion w : list) {
                Row row = s.createRow(r);
                setCell(row, 0, String.valueOf(r), data);
                setCell(row, 1, w.getContent(), data);
                setCell(row, 2, w.getCourseName(), data);
                setCell(row, 3, typeText(w.getQuestionType()), data);
                setCell(row, 4, w.getUserAnswer(), data);
                setCell(row, 5, w.getCorrectAnswer(), data);
                setCell(row, 6, str(w.getWrongCount()), data);
                setCell(row, 7, str(w.getCorrectCount()), data);
                setCell(row, 8, time(w.getLastWrongTime()), data);
                setCell(row, 9, time(w.getLastAnswerTime()), data);
                r++;
            }
            int[] ws = {1400, 11000, 3200, 2200, 2600, 2600, 2200, 3000, 4600, 4600};
            for (int i = 0; i < ws.length; i++) s.setColumnWidth(i, ws[i]);
            String fn = "我的错题_" + System.currentTimeMillis() + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fn, StandardCharsets.UTF_8));
            try (OutputStream os = response.getOutputStream()) {
                wb.write(os);
                os.flush();
            }
        }
    }
    private static String str(Integer v) {
        return v == null ? "0" : String.valueOf(v);
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
        s.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        return s;
    }
    private static CellStyle dataStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
        s.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        s.setWrapText(true);
        return s;
    }
    private static final String[] TYPES = {"", "单选", "多选", "判断", "填空", "简答", "名词解释"};

    private static String typeText(Integer t) {
        return (t == null || t < 1 || t >= TYPES.length) ? "未知" : TYPES[t];
    }

    /** AI 错题讲解：根据题干/答案/学科生成个性化解析（大模型或本地规则降级） */
    @PostMapping("/wrong-questions/{id}/explain")
    public Result<AiExplainResult> explain(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("生成成功", aiExplainService.explain(userId, id));
    }

    /** AI 错题多轮对话：携带历史上下文继续追问（大模型支持；本地规则引擎仅首轮可用） */
    @PostMapping("/wrong-questions/{id}/chat")
    public Result<AiExplainResult> chat(@PathVariable Long id,
                                        @RequestBody AiChatRequest req,
                                        HttpServletRequest request) {
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            return Result.error(400, "请输入你的问题");
        }
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("ok", aiExplainService.chat(userId, id, req.getQuestion(), req.getHistory()));
    }
}
