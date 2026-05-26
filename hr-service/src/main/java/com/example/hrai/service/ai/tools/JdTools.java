package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.jd.JdGenerateRequest;
import com.example.hrai.dto.jd.JdGenerateResponse;
import com.example.hrai.service.JdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JdTools {

    private final JdService jdService;

    public JdGenerateResponse generateJd() {
        JdGenerateRequest request = new JdGenerateRequest();
        request.setPositionName("高级 Java 开发工程师");
        request.setYears(5);
        request.setSkills(List.of("Spring Boot", "Spring Cloud", "MySQL", "Redis", "RAG"));
        return jdService.generate(request);
    }
}
