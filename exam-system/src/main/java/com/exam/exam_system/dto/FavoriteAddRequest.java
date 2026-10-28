package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 收藏请求体（练习页点击「收藏」时提交）
 */
@Data
public class FavoriteAddRequest {

    /** 要收藏的试题 ID */
    private Long questionId;
}
