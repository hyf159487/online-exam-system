package com.exam.exam_system.dto;

import lombok.Data;

/** 学生「错题本答对后自动移除」策略配置 */
@Data
public class PracticeConfig {
    /** 0=关 1=开 */
    private Integer wrongAutoRemove;
    /** 连续答对几次后自动移出：1~10（默认 3） */
    private Integer wrongRemoveThreshold;
}
