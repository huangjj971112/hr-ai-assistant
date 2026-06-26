package com.example.hrai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.dify")
public class DifyProperties {

    /**
     * 是否启用真实 Dify 调用；关闭时使用本地 mock 员工手册兜底。
     */
    private boolean enabled;

    /**
     * Dify API 基础地址，本地自建通常是 http://localhost/v1。
     */
    private String baseUrl = "https://api.dify.ai/v1";

    /**
     * Dify Chat App / 知识库问答 App 的 API Key，默认走 blocking /chat-messages。
     */
    private String apiKey;

    /**
     * Dify Workflow App 的接口路径。制度查询 Workflow 也复用该路径。
     */
    private String workflowPath = "/workflows/run";

    /**
     * Dify Agent Chat App 的 API Key；当 apiKey 对应的不是 Chat App 时，用它走 streaming 兜底。
     */
    private String agentApiKey;

    /**
     * Agent Chat App 的接口路径，通常也是 /chat-messages。
     */
    private String agentPath = "/chat-messages";

    /**
     * 调用 Dify 的连接和读取超时时间，单位秒。
     */
    private int timeoutSeconds = 60;
}
