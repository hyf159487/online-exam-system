package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.PaperTemplateVO;
import com.exam.exam_system.entity.PaperTemplate;

/**
 * 组卷模板 Service
 */
public interface PaperTemplateService {

    /** 分页查询模板列表 */
    PageResult<PaperTemplateVO> page(Integer pageNum, Integer pageSize,
                                      String templateName, Long courseId,
                                      Long creatorId, Integer role);

    /** 按 ID 查单个模板 */
    PaperTemplate getById(Long id);

    /** 新增模板 */
    void add(PaperTemplate template);

    /** 更新模板 */
    void update(PaperTemplate template);

    /** 删除模板 */
    void delete(Long id);
}
