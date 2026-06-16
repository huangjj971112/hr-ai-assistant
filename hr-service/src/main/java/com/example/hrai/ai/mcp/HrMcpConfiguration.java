package com.example.hrai.ai.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@link HrMcpTools} 中标注的工具方法注册到 Spring AI MCP Server。
 */
@Configuration
public class HrMcpConfiguration {

    @Bean
    public ToolCallbackProvider hrMcpToolCallbacks(HrMcpTools hrMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(hrMcpTools)
                .build();
    }
}
