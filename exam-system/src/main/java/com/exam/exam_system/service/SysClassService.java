package com.exam.exam_system.service;

import com.exam.exam_system.entity.SysClass;

import java.util.List;

/**
 * 班级管理 Service
 */
public interface SysClassService {

    /** 查全部班级（含学生数） */
    List<SysClass> listAllWithCount();

    /** 新增班级（班级名不可重复） */
    SysClass create(SysClass sysClass);

    /** 更新班级信息（班级名不可与其它班级重复） */
    void update(SysClass sysClass);

    /** 删除班级：班级下仍有学生时抛异常 */
    void delete(Long id);

    /**
     * 批量新增班级（部分失败策略：空名/批内重复/库内重复逐条跳过并报告）
     * @return {success, failed, failedReasons}
     */
    java.util.Map<String, Object> createBatch(List<SysClass> classes);

    /**
     * Excel 批量导入班级
     * 列：班级名称 | 年级 | 院系 | 专业（第一行表头跳过）
     * @return {success, failed, failedReasons}
     */
    java.util.Map<String, Object> importExcel(org.springframework.web.multipart.MultipartFile file);
}
