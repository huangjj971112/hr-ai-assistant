package com.example.hrai;

import com.example.hrai.ai.dify.DifyWorkflowProperties;
import com.example.hrai.config.AiProperties;
import com.example.hrai.config.DifyProperties;
import com.example.hrai.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AiProperties.class,
        DifyProperties.class,
        DifyWorkflowProperties.class,
        SecurityProperties.class
})
public class HrAiAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrAiAssistantApplication.class, args);
    }
}
