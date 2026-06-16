# Agent Memory And Spring AI Tool Calling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe multi-turn leave confirmation and Spring AI HR tool selection to the existing AI chat endpoint.

**Architecture:** A deterministic confirmation state machine wraps the agent entry. Redis-backed `AgentMemoryService` is authoritative for pending leave submission; Spring AI tools delegate to existing services and cannot bypass memory confirmation.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI 1.1.6, Spring Data Redis, JUnit 5, H2

---

### Task 1: Session-Aware Pending Leave Memory

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/dto/ai/AiChatRequest.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/memory/PendingLeaveApplyDTO.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/memory/AgentMemoryStore.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/memory/RedisAgentMemoryStore.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/memory/InMemoryAgentMemoryStore.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/memory/AgentMemoryService.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/memory/AgentMemoryServiceTest.java`

- [ ] Write failing tests for save/load, user-session isolation, confirmation, cancellation, and expiry.
- [ ] Run focused tests and verify they fail because memory classes are absent.
- [ ] Implement the DTO, storage abstraction, Redis key/TTL behavior, and test fallback.
- [ ] Run focused tests and verify they pass.
- [ ] Commit the memory slice without staging unrelated existing changes.

### Task 2: Deterministic Leave Parsing And Confirmation State Machine

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/service/ai/LeaveRequestParser.java`
- Create: `hr-service/src/main/java/com/example/hrai/service/ai/AgentConversationService.java`
- Modify: `hr-service/src/main/java/com/example/hrai/controller/AiChatController.java`
- Modify: `hr-service/src/main/java/com/example/hrai/service/ai/AiChatService.java`
- Test: `hr-service/src/test/java/com/example/hrai/service/ai/LeaveRequestParserTest.java`
- Test: `hr-service/src/test/java/com/example/hrai/HrAiAssistantApplicationTests.java`

- [ ] Add failing tests for pending creation, confirm, cancel, missing pending, direct-submit wording, relative dates, and cross-session isolation.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement parser and confirmation-first conversation routing using an injected Asia/Shanghai clock.
- [ ] Run focused tests and verify they pass.
- [ ] Commit the state-machine slice without staging unrelated existing changes.

### Task 3: Spring AI HR Tools And Prompt

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/tool/ToolResult.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/tool/HrAgentTools.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/dto/LeaveBalanceAgentToolRequest.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/dto/AttendanceAgentToolRequest.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/dto/LeavePolicyAgentToolRequest.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/dto/ConfirmedLeaveApplyToolRequest.java`
- Create: `hr-service/src/main/resources/prompts/hr-agent-system.st`
- Modify: `hr-service/src/main/java/com/example/hrai/service/ai/AiChatService.java`
- Modify: `hr-service/pom.xml`
- Test: `hr-service/src/test/java/com/example/hrai/ai/tool/HrAgentToolsTest.java`

- [ ] Add failing tests for direct balance/attendance/policy tools and guarded apply.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement `@Tool` methods delegating to existing services with uniform `ToolResult`.
- [ ] Add optional ChatClient integration and system prompt while preserving deterministic fallback.
- [ ] Run focused tests and verify they pass.
- [ ] Commit the Spring AI tool slice without staging unrelated existing changes.

### Task 4: Runtime Redis, Legacy Bypass Protection, And Full Verification

**Files:**
- Modify: `hr-service/src/main/resources/application.yml`
- Modify: `hr-service/src/test/resources/application-test.yml`
- Modify: `docker-compose.yml`
- Modify: `hr-service/src/main/java/com/example/hrai/controller/AiWorkflowController.java`
- Modify: `README.md`
- Test: `hr-service/src/test/java/com/example/hrai/HrAiAssistantApplicationTests.java`

- [ ] Add failing tests proving the old workflow cannot directly submit and all eight requested scenarios work.
- [ ] Configure Redis runtime/default TTL and test fallback.
- [ ] Route the old workflow through safe pending behavior.
- [ ] Run focused tests, then the full Maven test suite.
- [ ] Review the final diff for accidental changes and commit only task-owned files.
