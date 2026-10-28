package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.KnowledgePointQuery;
import com.exam.exam_system.entity.KnowledgePoint;
import com.exam.exam_system.mapper.KnowledgePointMapper;
import com.exam.exam_system.service.KnowledgePointService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识点 Service 实现
 *
 * <p>删除 / 批量删除前会逐个校验试题引用（question.knowledge_id），
 * 引用中的知识点禁止删除，保证不产生悬空引用。
 */
@Service
public class KnowledgePointServiceImpl implements KnowledgePointService {

    /** 被试题引用时的统一提示文案 */
    private static final String REF_ERROR = "存在试题正在使用该知识点，无法删除，请先解除试题绑定";

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Override
    public PageResult<KnowledgePoint> page(KnowledgePointQuery query) {
        if (query == null) {
            query = new KnowledgePointQuery();
        }
        List<KnowledgePoint> list = knowledgePointMapper.listByQuery(query);
        long total = knowledgePointMapper.countByQuery(query);
        return PageResult.of(list, total,
                query.getPageNum() == null ? 1L : query.getPageNum().longValue(),
                query.getPageSize() == null ? 10L : query.getPageSize().longValue());
    }

    @Override
    public List<KnowledgePoint> listByCourse(Long courseId, String keyword) {
        return knowledgePointMapper.listByCourse(courseId, keyword);
    }

    @Override
    public KnowledgePoint getById(Long id) {
        return knowledgePointMapper.selectById(id);
    }

    @Override
    public Long add(KnowledgePoint kp) {
        fillDefault(kp);
        knowledgePointMapper.insert(kp);
        return kp.getId();
    }

    @Override
    public void update(KnowledgePoint kp) {
        if (kp.getId() == null) {
            throw new IllegalArgumentException("知识点ID不能为空");
        }
        fillDefault(kp);
        knowledgePointMapper.updateById(kp);
    }

    @Override
    public void deleteById(Long id) {
        KnowledgePoint exist = knowledgePointMapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("知识点不存在或已被删除");
        }
        if (knowledgePointMapper.countQuestionRef(id) > 0) {
            throw new IllegalArgumentException(REF_ERROR);
        }
        knowledgePointMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的知识点");
        }
        // 先整体校验引用：任一被引用则全部拒绝，避免批量删一半
        List<String> usedNames = new ArrayList<>();
        for (Long id : ids) {
            KnowledgePoint kp = knowledgePointMapper.selectById(id);
            if (kp != null && knowledgePointMapper.countQuestionRef(id) > 0) {
                usedNames.add(kp.getPointName());
            }
        }
        if (!usedNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "存在试题正在使用知识点【" + String.join("、", usedNames) + "】，无法删除，请先解除试题绑定");
        }
        knowledgePointMapper.batchDeleteByIds(ids);
    }

    @Override
    public int importExcel(MultipartFile file, Long courseId) throws IllegalArgumentException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 Excel 文件");
        }
        if (courseId == null) {
            throw new IllegalArgumentException("请先选择要导入的课程");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx / .xls 格式文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("文件过大（>10MB），请压缩后再上传");
        }

        List<String> errors = new ArrayList<>();
        List<KnowledgePoint> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        // .xls 用 HSSFWorkbook、.xlsx 用 XSSFWorkbook，统一按 Workbook 接口处理
        boolean oldXls = filename.toLowerCase().endsWith(".xls")
                && !filename.toLowerCase().endsWith(".xlsx");
        try (InputStream in = file.getInputStream();
             org.apache.poi.ss.usermodel.Workbook workbook = oldXls
                     ? new org.apache.poi.hssf.usermodel.HSSFWorkbook(in)
                     : new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                throw new IllegalArgumentException("Excel 中没有数据行（首行应为表头）");
            }
            // 从第 2 行开始（跳过表头）
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String name = cellText(row.getCell(0), formatter);
                if (name == null || name.isBlank()) {
                    // 名称是必填：整行为空说明数据结束，跳过；有内容但没名称则记错误
                    boolean rowEmpty = row.getLastCellNum() <= 1;
                    if (rowEmpty) {
                        continue;
                    }
                    errors.add("第 " + (r + 1) + " 行：知识点名称不能为空");
                    continue;
                }
                KnowledgePoint kp = new KnowledgePoint();
                kp.setCourseId(courseId);
                kp.setPointName(name.trim());
                kp.setKnowledgeCode(trimToNull(cellText(row.getCell(1), formatter)));
                String sortText = cellText(row.getCell(2), formatter);
                if (sortText != null && !sortText.isBlank()) {
                    try {
                        kp.setSortOrder(Integer.parseInt(sortText.trim()));
                    } catch (NumberFormatException e) {
                        errors.add("第 " + (r + 1) + " 行：排序号必须是数字");
                    }
                }
                kp.setRemark(trimToNull(cellText(row.getCell(3), formatter)));
                fillDefault(kp);
                rows.add(kp);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("文件解析失败：" + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("文件解析失败，请确认为标准 Excel 文件");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("导入失败：\n" + String.join("\n", errors));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("未在文件中识别到有效知识点数据，请检查文件格式");
        }
        knowledgePointMapper.batchInsert(rows);
        return rows.size();
    }

    /** 补全默认值：parentId 空置 0、排序空置 0、名称去空格 */
    private void fillDefault(KnowledgePoint kp) {
        if (kp.getCourseId() == null) {
            throw new IllegalArgumentException("请选择归属课程");
        }
        if (kp.getPointName() == null || kp.getPointName().isBlank()) {
            throw new IllegalArgumentException("知识点名称不能为空");
        }
        kp.setPointName(kp.getPointName().trim());
        if (kp.getParentId() == null) {
            kp.setParentId(0L);
        }
        if (kp.getSortOrder() == null) {
            kp.setSortOrder(0);
        }
        if (kp.getKnowledgeCode() != null) {
            kp.setKnowledgeCode(kp.getKnowledgeCode().trim());
        }
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // 数字单元格统一去掉 .0 尾巴（排序号/编码可能是数字型）
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
        }
        return trimToNull(formatter.formatCellValue(cell));
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
