package com.exam.exam_system.service.impl;

import com.exam.exam_system.entity.SysClass;
import com.exam.exam_system.mapper.SysClassMapper;
import com.exam.exam_system.service.RedisCacheService;
import com.exam.exam_system.service.SysClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * 班级管理 Service 实现
 */
@Service
public class SysClassServiceImpl implements SysClassService {

    @Autowired
    private SysClassMapper sysClassMapper;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    /** 班级列表缓存 key（高频读取、低频写入） */
    private static final String CLASS_LIST_KEY = "cache:class:list";

    /** 班级列表缓存 TTL（秒）：10 分钟 */
    private static final long CLASS_LIST_TTL = 600L;

    @Override
    public List<SysClass> listAllWithCount() {
        // 优先读缓存
        if (redisCacheService != null) {
            List<SysClass> cached = redisCacheService.getObject(
                    CLASS_LIST_KEY, new TypeReference<List<SysClass>>() {});
            if (cached != null) {
                return cached;
            }
        }
        // 缓存未命中 → 查库并回填
        List<SysClass> list = sysClassMapper.selectAllWithCount();
        if (redisCacheService != null) {
            redisCacheService.setObject(CLASS_LIST_KEY, list, CLASS_LIST_TTL);
        }
        return list;
    }

    @Override
    public SysClass create(SysClass sysClass) {
        if (sysClass.getClassName() == null || sysClass.getClassName().isBlank()) {
            throw new IllegalArgumentException("班级名称不能为空");
        }
        String name = sysClass.getClassName().trim();
        if (sysClassMapper.selectByName(name) != null) {
            throw new IllegalArgumentException("班级「" + name + "」已存在");
        }
        sysClass.setClassName(name);
        sysClassMapper.insert(sysClass);
        evictClassListCache();
        return sysClass;
    }

    @Override
    public void update(SysClass sysClass) {
        if (sysClass.getId() == null) {
            throw new IllegalArgumentException("班级 ID 不能为空");
        }
        if (sysClass.getClassName() == null || sysClass.getClassName().isBlank()) {
            throw new IllegalArgumentException("班级名称不能为空");
        }
        String name = sysClass.getClassName().trim();
        SysClass exist = sysClassMapper.selectByName(name);
        if (exist != null && !exist.getId().equals(sysClass.getId())) {
            throw new IllegalArgumentException("班级「" + name + "」已存在");
        }
        sysClass.setClassName(name);
        int rows = sysClassMapper.update(sysClass);
        if (rows == 0) {
            throw new IllegalArgumentException("班级不存在");
        }
        evictClassListCache();
    }

    @Override
    public void delete(Long id) {
        if (sysClassMapper.countStudents(id) > 0) {
            throw new IllegalArgumentException("该班级下仍有学生，无法删除");
        }
        int rows = sysClassMapper.deleteById(id);
        if (rows == 0) {
            throw new IllegalArgumentException("班级不存在");
        }
        evictClassListCache();
    }

    /** 清除班级列表缓存 */
    private void evictClassListCache() {
        if (redisCacheService != null) {
            redisCacheService.delete(CLASS_LIST_KEY);
        }
    }

    @Override
    public java.util.Map<String, Object> createBatch(List<SysClass> classes) {
        java.util.List<String> failedReasons = new java.util.ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        int success = 0;
        int failed = 0;
        int lineNo = 0;

        if (classes != null) {
            for (SysClass c : classes) {
                lineNo++;
                String name = c.getClassName() == null ? "" : c.getClassName().trim();
                if (name.isEmpty()) {
                    failed++;
                    failedReasons.add("第 " + lineNo + " 行：班级名称为空，已跳过");
                    continue;
                }
                if (name.length() > 50) {
                    failed++;
                    failedReasons.add("第 " + lineNo + " 行：班级「" + name + "」名称超过 50 字，已跳过");
                    continue;
                }
                if (!seenNames.add(name)) {
                    failed++;
                    failedReasons.add("第 " + lineNo + " 行：班级「" + name + "」在本批次内重复，已跳过");
                    continue;
                }
                if (sysClassMapper.selectByName(name) != null) {
                    failed++;
                    failedReasons.add("第 " + lineNo + " 行：班级「" + name + "」系统中已存在，已跳过");
                    continue;
                }
                try {
                    c.setClassName(name);
                    sysClassMapper.insert(c);
                    success++;
                } catch (Exception e) {
                    failed++;
                    failedReasons.add("第 " + lineNo + " 行：班级「" + name + "」保存失败：" + e.getMessage());
                }
            }
        }

        if (success > 0) {
            evictClassListCache();
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedReasons", failedReasons);
        return result;
    }

    @Override
    public java.util.Map<String, Object> importExcel(org.springframework.web.multipart.MultipartFile file) {
        java.util.List<String> failedReasons = new java.util.ArrayList<>();
        int success = 0;
        int failed = 0;

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream())) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            java.util.Set<String> seenNames = new java.util.HashSet<>();

            for (int i = 1; i <= lastRow; i++) {
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.getRow(i);
                if (row == null) continue;
                String name = getCellString(row.getCell(0));
                String grade = getCellString(row.getCell(1));
                String department = getCellString(row.getCell(2));
                String major = getCellString(row.getCell(3));
                int rowNum = i + 1;

                if (name == null || name.trim().isEmpty()) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：班级名称为空，已跳过");
                    continue;
                }
                name = name.trim();
                if (!seenNames.add(name)) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：班级「" + name + "」在文件内重复，已跳过");
                    continue;
                }
                if (sysClassMapper.selectByName(name) != null) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：班级「" + name + "」系统中已存在，已跳过");
                    continue;
                }
                try {
                    SysClass c = new SysClass();
                    c.setClassName(name);
                    c.setGrade(grade);
                    c.setDepartment(department);
                    c.setMajor(major);
                    sysClassMapper.insert(c);
                    success++;
                } catch (Exception e) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：班级「" + name + "」保存失败：" + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel 解析失败：" + e.getMessage(), e);
        }

        if (success > 0) {
            evictClassListCache();
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedReasons", failedReasons);
        return result;
    }

    /** 读取 Excel 单元格为字符串 */
    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) return String.valueOf((long) d);
                return String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return null;
        }
    }
}
