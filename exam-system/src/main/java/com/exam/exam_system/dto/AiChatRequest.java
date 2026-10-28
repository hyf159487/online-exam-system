package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 错题多轮对话请求
 *
 * <p>前端在错题讲解对话中维护全部历史消息，每次追问携带：
 * <ul>
 *     <li>question —— 学生本次的提问；</li>
 *     <li>history  —— 此前多轮的 user/assistant 消息（不含 system，由后端统一注入题目上下文）。</li>
 * </ul>
 */
@Data
public class AiChatRequest {

    /** 学生本次提问内容（必填） */
    private String question;

    /** 此前对话历史（可选，为空表示首轮提问） */
    private List<Message> history;

    /** 单轮对话消息 */
    @Data
    public static class Message {
        /** user=学生 / assistant=AI */
        private String role;
        /** 消息正文 */
        private String content;
    }
}
