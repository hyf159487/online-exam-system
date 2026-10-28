package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.HotWrongItem;
import com.exam.exam_system.dto.HotWrongQuery;
import com.exam.exam_system.mapper.HotWrongMapper;
import com.exam.exam_system.service.HotWrongService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HotWrongServiceImpl implements HotWrongService {

    @Autowired
    private HotWrongMapper hotWrongMapper;

    @Override
    public PageResult<HotWrongItem> list(HotWrongQuery query) {
        int offset = query.getOffset();
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;
        long total = hotWrongMapper.count(
                query.getQuestionType(), query.getDifficulty(),
                query.getCourseId(), query.getCourseIds(), query.getMinErrorRate());
        List<HotWrongItem> records = hotWrongMapper.list(
                query.getQuestionType(), query.getDifficulty(),
                query.getCourseId(), query.getCourseIds(), query.getMinErrorRate(),
                query.getSortBy(), offset, pageSize);
        return PageResult.of(records, total, query.getPageNum().longValue(), (long) pageSize);
    }

    @Override
    public HotWrongItem statByQuestionId(Long questionId) {
        return hotWrongMapper.statByQuestionId(questionId);
    }
}
