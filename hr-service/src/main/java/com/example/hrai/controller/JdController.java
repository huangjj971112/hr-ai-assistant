package com.example.hrai.controller;

import com.example.hrai.dto.jd.JdGenerateRequest;
import com.example.hrai.dto.jd.JdGenerateResponse;
import com.example.hrai.service.JdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/jd")
public class JdController {

    private final JdService jdService;

    @PostMapping("/generate")
    public JdGenerateResponse generate(@Valid @RequestBody JdGenerateRequest request) {
        return jdService.generate(request);
    }
}
