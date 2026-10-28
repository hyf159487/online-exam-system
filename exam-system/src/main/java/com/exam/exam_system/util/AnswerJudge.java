package com.exam.exam_system.util;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 客观题答案判分工具（单选/多选/判断/填空）。
 * 与 StudentExamServiceImpl 的判分规则保持一致，独立成工具类便于练习/重做等多处复用。
 */
public class AnswerJudge {

    /** 判分：题型 1=单选 2=多选 3=判断 4=填空 5=简答（简答返回 false，由教师阅卷） */
    public static boolean judge(Integer questionType, String userAnswer, String standardAnswer) {
        if (standardAnswer == null || userAnswer == null) return false;
        String u = userAnswer.trim();
        String s = standardAnswer.trim();
        if (u.isEmpty()) return false;

        if (questionType != null && questionType == 2) {
            // 多选：拆分 → 去空格 → 排序 → 重新拼接后比较
            return normalizeMulti(u).equals(normalizeMulti(s));
        }
        // 单选 / 判断 / 填空：精确比较（不区分前后空格）
        return u.equals(s);
    }

    /** 多选答案标准化："C, A" → "A,C" */
    public static String normalizeMulti(String raw) {
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }
}
