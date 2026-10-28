package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.PaperTemplateVO;
import com.exam.exam_system.entity.PaperTemplate;
import com.exam.exam_system.mapper.PaperTemplateMapper;
import com.exam.exam_system.service.PaperTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaperTemplateServiceImpl implements PaperTemplateService {

    @Autowired
    private PaperTemplateMapper paperTemplateMapper;

    @Override
    public PageResult<PaperTemplateVO> page(Integer pageNum, Integer pageSize,
                                             String templateName, Long courseId,
                                             Long creatorId, Integer role) {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        long total = paperTemplateMapper.countByQuery(templateName, courseId, creatorId, role);
        List<PaperTemplateVO> records = paperTemplateMapper.listByQuery(
                (pn - 1) * ps, ps, templateName, courseId, creatorId, role);
        return PageResult.of(records, total, (long) pn, (long) ps);
    }

    @Override
    public PaperTemplate getById(Long id) {
        return paperTemplateMapper.selectById(id);
    }

    @Override
    public void add(PaperTemplate template) {
        paperTemplateMapper.insert(template);
    }

    @Override
    public void update(PaperTemplate template) {
        paperTemplateMapper.updateById(template);
    }

    @Override
    public void delete(Long id) {
        paperTemplateMapper.deleteById(id);
    }
}
