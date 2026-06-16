# ZhiPu Tool Calling Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route ordinary HR requests through ZhiPu Spring AI Tool Calling while keeping confirmation safety and deterministic fallback.

**Architecture:** `AiChatService` remains the safety router. Exact confirmation/cancellation and leave application requests stay backend-controlled; ZhiPu receives only query tools, and keyword routing handles model absence or failure.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI 1.1.6, ZhiPu AI, Redis, JUnit 5

---

### Task 1: Lock Down Tool Exposure

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/ai/tool/HrAgentTools.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/tool/HrAgentToolsTest.java`

- [ ] Add a reflection test proving `applyLeave` is not annotated with `@Tool`.
- [ ] Remove `@Tool` from `applyLeave` while preserving its Redis confirmation checks.
- [ ] Run `HrAgentToolsTest`.

### Task 2: Route Through ZhiPu Before Keyword Fallback

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/service/ai/AiChatService.java`
- Modify: `hr-service/src/main/java/com/example/hrai/service/ai/SpringAiToolCallingService.java`
- Modify: `hr-service/src/main/resources/prompts/hr-agent-system.txt`
- Test: `hr-service/src/test/java/com/example/hrai/service/ai/AiChatServiceToolCallingTest.java`

- [ ] Add tests proving exact confirm/cancel and leave application bypass the model.
- [ ] Add tests proving ordinary requests prefer the model and fall back on model failure.
- [ ] Move the model call before query keyword branches.
- [ ] Keep leave creation backend-controlled and remove the direct-submit fallback.
- [ ] Register only query tools with `ChatClient`.

### Task 3: Configure ZhiPu Provider

**Files:**
- Modify: `hr-service/pom.xml`
- Modify: `hr-service/src/main/resources/application.yml`
- Modify: `README.md`

- [ ] Add `spring-ai-starter-model-zhipuai`.
- [ ] Disable chat model auto-configuration by default.
- [x] Document `SPRING_AI_CHAT_MODEL`, `ZHIPUAI_API_KEY`, and `ZHIPUAI_CHAT_MODEL`.
- [ ] Verify startup remains available without an API key.

### Task 4: Verification

- [ ] Run focused tool and conversation tests.
- [ ] Run the full Maven test suite.
- [ ] Compile with default secret-free configuration.
