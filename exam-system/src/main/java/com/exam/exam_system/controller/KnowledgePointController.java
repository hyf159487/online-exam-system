package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.KnowledgePointQuery;
import com.exam.exam_system.entity.KnowledgePoint;
import com.exam.exam_system.service.KnowledgePointService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 知识点 Controller（教师/管理员可读，管理操作仅管理员——拦截器按路径前缀控制）
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /api/knowledge-point/page            分页查询（管理员维护页）</li>
 *   <li>GET    /api/knowledge-point/list            按课程查知识点（题目录入下拉 / 只读查看）</li>
 *   <li>POST   /api/knowledge-point                 新增知识点</li>
 *   <li>PUT    /api/knowledge-point                 更新知识点</li>
 *   <li>DELETE /api/knowledge-point/{id}            删除知识点（被试题引用则拒绝）</li>
 *   <li>POST   /api/knowledge-point/batchDelete     批量删除知识点</li>
 *   <li>POST   /api/knowledge-point/importExcel     按课程 Excel 批量导入</li>
 *   <li>GET    /api/knowledge-point/template/excel  下载导入模板</li>
 *   <li>GET    /api/knowledge-point/export          导出当前课程全部知识点为 Excel</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/knowledge-point")
public class KnowledgePointController {

    @Autowired
    private KnowledgePointService knowledgePointService;

    /** 教师数据权限：知识点按「任教课程」隔离（写操作仍为管理员专用，由拦截器控制） */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** 分页查询（管理员知识点管理页） */
    @GetMapping("/page")
    public Result<PageResult<KnowledgePoint>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            jakarta.servlet.http.HttpServletRequest request) {
        KnowledgePointQuery query = new KnowledgePointQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setCourseId(courseId);
        query.setKeyword(keyword);
        // 数据权限：教师只能看自己任教课程下的知识点（管理员为 null，不限制）
        query.setCourseIds(teacherScope.myCourseIds(request));
        return Result.success("查询成功", knowledgePointService.page(query));
    }

    /** 按课程查知识点（无分页，题目录入下拉用） */
    @GetMapping("/list")
    public Result<List<KnowledgePoint>> list(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            jakarta.servlet.http.HttpServletRequest request) {
        // 数据权限：教师必须指定自己任教的课程；不指定则不返回任何知识点（避免越权浏览）
        if (!teacherScope.isAdmin(request)) {
            if (courseId == null) {
                return Result.success("查询成功", List.of());
            }
            teacherScope.checkCourse(request, courseId);
        }
        return Result.success("查询成功", knowledgePointService.listByCourse(courseId, keyword));
    }

    /** 新增知识点 */
    @PostMapping
    @com.exam.exam_system.annotation.OperationLog(
            module = "基础数据", type = "新增知识点",
            description = "新增知识点：课程id=#{#kp.courseId}，名称=#{#kp.pointName}")
    public Result<Long> add(@RequestBody KnowledgePoint kp) {
        try {
            return Result.success("新增成功", knowledgePointService.add(kp));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 更新知识点 */
    @PutMapping
    @com.exam.exam_system.annotation.OperationLog(
            module = "基础数据", type = "修改知识点",
            description = "修改知识点 id=#{#kp.id}，名称=#{#kp.pointName}")
    public Result<Void> update(@RequestBody KnowledgePoint kp) {
        try {
            knowledgePointService.update(kp);
            return Result.success("修改成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 删除知识点（被试题引用时禁止删除） */
    @DeleteMapping("/{id}")
    @com.exam.exam_system.annotation.OperationLog(
            module = "基础数据", type = "删除知识点",
            description = "删除知识点 id=#{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            knowledgePointService.deleteById(id);
            return Result.success("删除成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 批量删除知识点 */
    @PostMapping("/batchDelete")
    @com.exam.exam_system.annotation.OperationLog(
            module = "基础数据", type = "批量删除知识点",
            description = "批量删除知识点 ids=#{#ids}")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        try {
            knowledgePointService.deleteBatch(ids);
            return Result.success("删除成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 按课程 Excel 批量导入知识点 */
    @PostMapping("/importExcel")
    @com.exam.exam_system.annotation.OperationLog(
            module = "基础数据", type = "导入知识点",
            description = "导入知识点文件：#{#file?.originalFilename}，课程id=#{#courseId}")
    public Result<Integer> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "courseId", required = false) Long courseId) {
        try {
            int count = knowledgePointService.importExcel(file, courseId);
            return Result.success("导入成功，共新增 " + count + " 条知识点", count);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "导入异常：" + e.getMessage());
        }
    }

    /** 下载导入模板（.xlsx） */
    @GetMapping("/template/excel")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        String filename = "知识点导入模板_" + timestamp() + ".xlsx";
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("知识点导入模板");
            String[] headers = {"知识点名称", "知识点编码", "排序号", "备注"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            String[][] examples = {
                    {"函数的概念及常见函数", "FUNC-001", "1", "第一讲：函数定义、定义域值域"},
                    {"矩阵及其运算", "LA-MAT-01", "1", ""}
            };
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    row.createCell(c).setCellValue(examples[r][c]);
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 9000);
            }
            setFileResponse(response, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            try (OutputStream os = response.getOutputStream()) {
                workbook.write(os);
            }
        }
    }

    /** 导出当前课程（可带关键词）全部知识点 */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) Long courseId,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response,
                       jakarta.servlet.http.HttpServletRequest request) throws IOException {
        // 数据权限：教师必须按自己任教的课程导出，不指定课程直接拒绝（避免导出全库知识点）
        if (!teacherScope.isAdmin(request)) {
            if (courseId == null) {
                throw new com.exam.exam_system.common.BizException("请先选择要导出的课程");
            }
            teacherScope.checkCourse(request, courseId);
        }
        List<KnowledgePoint> list = knowledgePointService.listByCourse(courseId, keyword);
        String filename = "知识点导出_" + timestamp() + ".xlsx";
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("知识点");
            String[] headers = {"序号", "知识点名称", "知识点编码", "排序号", "备注", "创建时间"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (KnowledgePoint kp : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(r - 1);
                row.createCell(1).setCellValue(kp.getPointName() == null ? "" : kp.getPointName());
                row.createCell(2).setCellValue(kp.getKnowledgeCode() == null ? "" : kp.getKnowledgeCode());
                row.createCell(3).setCellValue(kp.getSortOrder() == null ? 0 : kp.getSortOrder());
                row.createCell(4).setCellValue(kp.getRemark() == null ? "" : kp.getRemark());
                row.createCell(5).setCellValue(kp.getCreateTime() == null ? "" :
                        kp.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 9000);
            }
            setFileResponse(response, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            try (OutputStream os = response.getOutputStream()) {
                workbook.write(os);
            }
        }
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private void setFileResponse(HttpServletResponse response, String filename, String contentType) throws IOException {
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
    }
}
