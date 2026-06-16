# Frontend MCP Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route the existing frontend employee Agent panel through the HR MCP Server on demand.

**Architecture:** Add a backend MCP chat endpoint that generates tool tokens and invokes the local Streamable HTTP MCP endpoint. Keep the browser limited to the existing authenticated chat contract and add a frontend mode selector.

**Tech Stack:** Java 17, Spring Boot, Spring MVC `RestClient`, Spring AI MCP Server, Vue 3, TypeScript, Maven, Vitest/Vite

---

### Task 1: MCP Chat Routing Service

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/client/HrMcpCaller.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/client/StreamableHttpHrMcpCaller.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/service/HrMcpChatService.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/service/HrMcpChatServiceTest.java`

- [ ] Write failing tests for the six supported routing paths and unsupported messages.
- [ ] Run the focused test and verify it fails because the service is missing.
- [ ] Implement the minimal MCP caller abstraction and chat routing service.
- [ ] Run the focused test and verify it passes.

### Task 2: Authenticated MCP Chat Endpoint

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/controller/HrMcpChatController.java`
- Modify: `hr-service/src/test/java/com/example/hrai/HrAiAssistantApplicationTests.java`

- [ ] Write a failing endpoint integration test.
- [ ] Add `POST /api/ai/mcp/chat` using the existing `AiChatRequest` and `AgentChatResponse` contracts.
- [ ] Run the endpoint integration test.

### Task 3: Frontend MCP Agent Selector

**Files:**
- Modify: `hr-web/src/App.vue`

- [ ] Add Local Agent / MCP Agent state and selector.
- [ ] Route chat requests to `/ai/chat` or `/ai/mcp/chat` based on the selected mode.
- [ ] Preserve the existing browser session ID and confirmation UI.
- [ ] Run the frontend build.

### Task 4: End-To-End Verification

**Files:**
- Modify: `README.md`

- [ ] Document how to select and test MCP Agent mode.
- [ ] Run focused backend tests.
- [ ] Run the root Maven test suite.
- [ ] Run the frontend production build.
- [ ] Verify a live page-triggered balance query reaches the MCP endpoint.

