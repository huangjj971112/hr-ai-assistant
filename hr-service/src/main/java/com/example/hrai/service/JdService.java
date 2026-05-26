package com.example.hrai.service;

import com.example.hrai.dto.jd.JdGenerateRequest;
import com.example.hrai.dto.jd.JdGenerateResponse;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;

@Service
public class JdService {

    public JdGenerateResponse generate(JdGenerateRequest request) {
        String skillText = String.join("、", request.getSkills());
        StringJoiner jd = new StringJoiner("\n");
        jd.add("职位名称：" + request.getPositionName());
        jd.add("");
        jd.add("岗位职责：");
        jd.add("1. 负责 HR SaaS 核心业务系统的后端设计、开发与持续优化；");
        jd.add("2. 参与招聘、员工、请假、面试等业务模块的接口设计和工程落地；");
        jd.add("3. 结合 AI Workflow、RAG 和 Tool Calling 能力，建设可复用的智能化 HR 助手能力；");
        jd.add("4. 与产品、前端、测试协作，保障系统的稳定性、可扩展性和可维护性。");
        jd.add("");
        jd.add("任职要求：");
        jd.add("1. " + request.getYears() + " 年及以上后端开发经验，具备扎实的 Java 基础；");
        jd.add("2. 熟悉 " + skillText + " 等技术栈，有实际项目落地经验；");
        jd.add("3. 熟悉 RESTful API、数据库建模、缓存设计和常见系统性能优化方法；");
        jd.add("4. 对 AI 应用工程化、知识库问答或企业系统智能化集成有兴趣者优先。");
        jd.add("");
        jd.add("加分项：");
        jd.add("1. 有 Spring AI、向量数据库、RAG 或大模型工具调用相关经验；");
        jd.add("2. 有 HR SaaS、企业协同、招聘管理系统相关业务背景。");
        return new JdGenerateResponse(jd.toString());
    }
}
