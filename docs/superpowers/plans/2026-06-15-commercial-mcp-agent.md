# Commercial MCP Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a strict model-driven and production-oriented HR MCP Agent.

**Architecture:** Upgrade pending state and MCP results first, then wrap every MCP tool call with validation, authorization, trace, and database audit. Finally replace keyword routing with a ChatClient that receives dynamically discovered MCP tool callbacks and injects trusted fields server-side.

**Tech Stack:** Java 17, Spring Boot, Spring AI 1.1.6, MCP SDK, Redis, MyBatis Plus, PostgreSQL/H2, JUnit 5, Mockito

---

### Task 1: Versioned And Idempotent Pending State

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/ai/memory/PendingLeaveApplyDTO.java`
- Modify: `hr-service/src/main/java/com/example/hrai/ai/memory/AgentMemoryService.java`
- Modify: `hr-service/src/main/java/com/example/hrai/ai/mcp/dto/ConfirmLeaveApplyMcpRequest.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/memory/AgentMemoryServiceTest.java`

- [ ] Write failing tests for pending ID creation, version conflict, same-key retry, and different-key conflict.
- [ ] Implement the minimum state transitions while retaining Redis storage.
- [ ] Run memory tests and MCP pending tests.

### Task 2: Traceable Results And Tool Audit

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/ai/tool/ToolResult.java`
- Create: `hr-service/src/main/java/com/example/hrai/entity/McpToolAuditLog.java`
- Create: `hr-service/src/main/java/com/example/hrai/repository/McpToolAuditLogRepository.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/audit/McpToolAuditService.java`
- Modify: `hr-service/src/main/resources/schema.sql`
- Test: `hr-service/src/test/java/com/example/hrai/ai/mcp/audit/McpToolAuditServiceTest.java`

- [ ] Write failing audit and trace tests.
- [ ] Add trace IDs to every `ToolResult`.
- [ ] Persist sanitized tool audit records.

### Task 3: MCP Validation And Permission Matrix

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/ai/mcp/HrMcpToolService.java`
- Modify: `hr-service/src/main/java/com/example/hrai/ai/mcp/dto/*.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/mcp/HrMcpToolServiceTest.java`

- [ ] Write failing validation, permission, tenant, version, and idempotency tests.
- [ ] Wrap all six tools with validation, trace, audit, and stable errors.
- [ ] Run focused MCP tests.

### Task 4: Strict Model-Driven MCP Agent

**Files:**
- Replace: `hr-service/src/main/java/com/example/hrai/ai/service/HrMcpChatService.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/client/HrMcpClientFactory.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/client/SecureMcpToolCallbackProvider.java`
- Create: `hr-service/src/main/resources/prompts/hr-mcp-agent-system.txt`
- Test: `hr-service/src/test/java/com/example/hrai/ai/service/HrMcpChatServiceTest.java`

- [ ] Write failing tests proving model unavailability never falls back.
- [ ] Write failing tests proving tool choice is delegated to ChatClient callbacks.
- [ ] Inject trusted tool fields after model selection.
- [ ] Return stable model errors.

### Task 5: Verification And Documentation

**Files:**
- Modify: `README.md`

- [ ] Run focused pending, audit, permission, and model tests.
- [ ] Run the root Maven test suite.
- [ ] Run the frontend build.
- [ ] Run `git diff --check`.

