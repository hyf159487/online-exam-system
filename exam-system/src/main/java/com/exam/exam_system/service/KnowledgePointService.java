package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.KnowledgePointQuery;
import com.exam.exam_system.entity.KnowledgePoint;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识点 Service
 *
 * <p>知识点归属课程，管理员统一维护；教师 / 管理员均可只读查询（题目录入下拉用）。
 * 删除前校验是否被试题引用，引用中的知识点禁止删除，防止脏数据。
 */
public interface KnowledgePointService {

    /** 分页查询知识点（管理页） */
    PageResult<KnowledgePoint> page(KnowledgePointQuery query);

    /** 按课程查知识点列表（无分页，题目录入下拉 / 只读查看用） */
    List<KnowledgePoint> listByCourse(Long courseId, String keyword);

    /** 按 id 查单个 */
    KnowledgePoint getById(Long id);

    /** 新增知识点，返回新 id */
    Long add(KnowledgePoint kp);

    /** 更新知识点 */
    void update(KnowledgePoint kp);

    /** 删除知识点（被试题引用时抛异常禁止删除） */
    void deleteById(Long id);

    /** 批量删除知识点（任一被引用则整体拒绝，防止部分删除造成脏数据） */
    void deleteBatch(List<Long> ids);

    /** Excel 批量导入（按课程导入），返回成功条数；有错误行时抛 IllegalArgumentException */
    int importExcel(MultipartFile file, Long courseId);
}
