package com.exam.exam_system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 能力配置（application.properties 中 ai.* 前缀）
 *
 * <p>支持任意 OpenAI 兼容的 Chat Completions 接口（DeepSeek / 通义千问 / 智谱 / Moonshot 等），
 * 只需要填 base-url + api-key + model 即可切换供应商。
 *
 * <p>未配置 api-key 时系统自动降级为「本地规则解析引擎」，
 * 保证在没有外网/没有 key 的环境下 AI 错题讲解功能依然可用可演示。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 大模型接口根地址（OpenAI 兼容），如 https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API Key，未配置时自动降级为本地规则讲解 */
    private String apiKey = "";

    /** 模型名称，如 deepseek-chat、qwen-plus 等 */
    private String model = "deepseek-chat";

    /** 请求超时（秒），大模型生成较慢，默认 60 秒 */
    private int timeoutSeconds = 60;
}
