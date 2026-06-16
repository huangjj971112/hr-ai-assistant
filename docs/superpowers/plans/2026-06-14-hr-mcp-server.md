# HR MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add six authenticated HR MCP tools to the existing `hr-service`.

**Architecture:** A thin `HrMcpTools` protocol adapter delegates to
`HrMcpToolService`. The service reuses existing token validation, HR tools,
leave parsing, Redis memory, and confirmed submission behavior.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AI 1.1.6 MCP WebMVC, Redis,
JUnit 5, Mockito

---

### Task 1: MCP Tool Contract

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/dto/*.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/HrMcpTools.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/mcp/HrMcpToolsExposureTest.java`

- [ ] Write a failing reflection test requiring exactly the six approved MCP tool names.
- [ ] Add explicit request DTO records.
- [ ] Add the thin MCP adapter and make the exposure test pass.

### Task 2: MCP Service And Safety

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/mcp/HrMcpToolService.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/mcp/HrMcpToolServiceTest.java`

- [ ] Write failing tests for query delegation and token-derived identity.
- [ ] Write failing tests for pending creation, confirm-submit, cancellation,
  missing pending, and session isolation.
- [ ] Implement the minimal service using existing `ToolTokenService`,
  `LeaveTools`, `AttendanceTools`, `KnowledgeTools`, `LeaveRequestParser`,
  `AgentMemoryService`, and `HrAgentTools`.

### Task 3: MCP Runtime Configuration

**Files:**
- Modify: `hr-service/pom.xml`
- Modify: `hr-service/src/main/resources/application.yml`
- Modify: `README.md`

- [ ] Add the Spring AI MCP WebMVC starter.
- [ ] Configure synchronous Streamable HTTP `/mcp` transport.
- [ ] Document the six tools and token requirement.

### Task 4: Verification

- [ ] Run focused MCP tests.
- [ ] Run the full `hr-service` test suite.
- [ ] Run the root multi-module test suite.
- [ ] Run `git diff --check`.

