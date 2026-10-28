package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生交卷请求 DTO
 *
 * <p>答案格式约定（与题库 answer 字段一致）：
 * <ul>
 *     <li>单选："A" / "B" / "C" / "D"</li>
 *     <li>多选："A,C"（逗号分隔，顺序不限，判分时会排序）</li>
 *     <li>判断："T" / "F"</li>
 *     <li>填空 / 简答：纯文本</li>
 * </ul>
 */
@Data
public class SubmitRequest {

    private List<AnswerItem> answers;

    /** 心跳/监控用：当前剩余时间（秒），可空 */
    private Integer remainingTime;

    /** 心跳/监控用：累计切屏次数，可空 */
    private Integer screenSwitchCount;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private String answer;
    }
}
