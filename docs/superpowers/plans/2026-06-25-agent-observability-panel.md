# Agent Observability Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixed right-side observability panel that shows the latest or selected historical Agent response's sanitized Agent, Tool, duration, `traceId`, and decision details.

**Architecture:** Add an optional top-level `observation` field to `AiChatResponse` while preserving the current `data` contract. Backend request-scoped builders capture existing Model Agent and Multi-Agent calls without making additional business or model requests; the Vue frontend stores each snapshot on its assistant message and renders the selected snapshot in a responsive right-side panel.

**Tech Stack:** Java 17, Spring Boot, Spring AI ChatClient Tool Calling, MCP, JUnit 5, AssertJ, Mockito, Vue 3 Composition API, TypeScript, Vite.

---

## File Structure

**Create**

- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationStatus.java`
  - Shared status enum for request, Agent, and Tool observation records.
- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentToolObservation.java`
  - Immutable sanitized MCP Tool observation DTO.
- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationStep.java`
  - Immutable Agent step DTO containing zero or more Tool observations.
- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentDecisionObservation.java`
  - Immutable final decision summary DTO.
- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationSnapshot.java`
  - Immutable top-level response snapshot.
- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationBuilder.java`
  - Request-scoped, non-Spring mutable builder used only during one chat request.
- `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationSanitizer.java`
  - Converts Tool arguments/results into safe summaries and removes protected fields.
- `hr-service/src/test/java/com/example/hrai/ai/observation/AgentObservationSanitizerTest.java`
  - Sanitization and sensitive-field regression tests.
- `hr-service/src/test/java/com/example/hrai/ai/observation/AgentObservationBuilderTest.java`
  - Ordering, duration, partial failure, and immutable snapshot tests.

**Modify**

- `hr-service/src/main/java/com/example/hrai/dto/ai/AiChatResponse.java`
  - Add optional `observation` while retaining the three-argument constructor.
- `hr-service/src/main/java/com/example/hrai/ai/service/SpringAiMcpModelAgent.java`
  - Capture model-agent and MCP Tool observations from existing calls.
- `hr-service/src/main/java/com/example/hrai/ai/multiagent/DefaultCoordinatorAgent.java`
  - Capture Coordinator and child Agent steps, durations, Tool traces, and salary decision.
- `hr-service/src/test/java/com/example/hrai/ai/service/SpringAiMcpModelAgentTest.java`
  - Verify the model path returns sanitized observations without duplicate Tool calls.
- `hr-service/src/test/java/com/example/hrai/ai/multiagent/DefaultCoordinatorAgentTest.java`
  - Verify ordered Multi-Agent observations and final decision details.
- `hr-service/src/test/java/com/example/hrai/ai/controller/HrMcpChatControllerTest.java`
  - Verify observation serialization remains additive to the HTTP response.
- `hr-web/src/App.vue`
  - Add observation TypeScript types, message binding, history selection, and panel markup.
- `hr-web/src/style.css`
  - Add two-column layout, selected-message styling, timeline, status, details, and responsive behavior.

---

### Task 1: Define The Additive Observation Response Contract

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationStatus.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentToolObservation.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationStep.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentDecisionObservation.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationSnapshot.java`
- Modify: `hr-service/src/main/java/com/example/hrai/dto/ai/AiChatResponse.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/controller/HrMcpChatControllerTest.java`

- [ ] **Step 1: Write a failing serialization test**

Add a controller test that returns:

```java
AgentObservationSnapshot observation = new AgentObservationSnapshot(
        "request-1",
        AgentObservationStatus.SUCCESS,
        120,
        List.of("已查询假期余额"),
        List.of(),
        null
);
AiChatResponse response = new AiChatResponse("MCP_MODEL_RESPONSE", "年假余额 5 天", null, observation);
```

Assert the JSON contains:

```java
assertThat(body).contains("\"observation\"");
assertThat(body).contains("\"requestId\":\"request-1\"");
assertThat(body).contains("\"totalDurationMs\":120");
```

Also keep an assertion that a legacy three-argument response can still be constructed:

```java
AiChatResponse legacy = new AiChatResponse("LEAVE_BALANCE", "余额 5 天", null);
assertThat(legacy.getObservation()).isNull();
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service -Dtest=HrMcpChatControllerTest test
```

Expected: compilation fails because the observation DTOs and four-argument constructor do not exist.

- [ ] **Step 3: Implement the immutable DTOs**

Use records:

```java
public enum AgentObservationStatus {
    SUCCESS,
    FAILED,
    PARTIAL
}
```

```java
public record AgentToolObservation(
        String toolName,
        AgentObservationStatus status,
        long durationMs,
        String traceId,
        Map<String, Object> inputSummary,
        Map<String, Object> resultSummary,
        String evidenceSource,
        String errorCode
) {
}
```

```java
public record AgentObservationStep(
        String agentName,
        AgentObservationStatus status,
        long durationMs,
        String summary,
        List<AgentToolObservation> toolCalls
) {
}
```

```java
public record AgentDecisionObservation(
        String outcome,
        String basis,
        boolean needsHumanConfirmation
) {
}
```

```java
public record AgentObservationSnapshot(
        String requestId,
        AgentObservationStatus status,
        long totalDurationMs,
        List<String> summarySteps,
        List<AgentObservationStep> steps,
        AgentDecisionObservation decision
) {
}
```

Change `AiChatResponse` to keep a manual compatibility constructor:

```java
@Data
@NoArgsConstructor
public class AiChatResponse {
    private String intent;
    private String reply;
    private Object data;
    private AgentObservationSnapshot observation;

    public AiChatResponse(String intent, String reply, Object data) {
        this(intent, reply, data, null);
    }

    public AiChatResponse(
            String intent,
            String reply,
            Object data,
            AgentObservationSnapshot observation
    ) {
        this.intent = intent;
        this.reply = reply;
        this.data = data;
        this.observation = observation;
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service -Dtest=HrMcpChatControllerTest test
```

Expected: all `HrMcpChatControllerTest` tests pass.

- [ ] **Step 5: Record a response-contract checkpoint**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. Do not commit because the shared worktree
already contains earlier uncommitted changes in files used by this feature.

---

### Task 2: Build And Test Sanitized Request-Scoped Observation Collection

**Files:**
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationSanitizer.java`
- Create: `hr-service/src/main/java/com/example/hrai/ai/observation/AgentObservationBuilder.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/observation/AgentObservationSanitizerTest.java`
- Test: `hr-service/src/test/java/com/example/hrai/ai/observation/AgentObservationBuilderTest.java`

- [ ] **Step 1: Write failing sanitizer tests**

Cover these exact cases:

```java
Map<String, Object> arguments = Map.of(
        "toolToken", "secret-token",
        "idempotencyKey", "secret-key",
        "startDate", "2026-06-23",
        "endDate", "2026-06-25",
        "message", "帮我请三天病假，原因是详细隐私原因"
);

Map<String, Object> summary = sanitizer.sanitizeInput("create_leave_pending", arguments);

assertThat(summary).containsEntry("startDate", "2026-06-23");
assertThat(summary).containsEntry("endDate", "2026-06-25");
assertThat(summary).doesNotContainKeys("toolToken", "idempotencyKey", "message");
assertThat(summary.toString()).doesNotContain("详细隐私原因", "secret-token", "secret-key");
```

For a balance result:

```java
ToolResult<Object> result = ToolResult.success(
        "trace-1",
        "ok",
        Map.of("employeeName", "张三", "annualLeaveBalance", 5, "sickLeaveBalance", 3)
);
assertThat(sanitizer.sanitizeResult("query_leave_balance", result))
        .containsEntry("annualLeaveBalance", 5)
        .containsEntry("sickLeaveBalance", 3);
```

- [ ] **Step 2: Write failing builder tests**

Create a builder, record two Agent steps and one Tool call, then assert:

```java
assertThat(snapshot.steps()).extracting(AgentObservationStep::agentName)
        .containsExactly("CoordinatorAgent", "PolicyAgent");
assertThat(snapshot.status()).isEqualTo(AgentObservationStatus.SUCCESS);
assertThat(snapshot.totalDurationMs()).isGreaterThanOrEqualTo(0);
assertThat(snapshot.steps().get(1).toolCalls().get(0).traceId()).isEqualTo("trace-policy");
```

Add a partial result test where one Tool has `success=false`; expected snapshot status is `PARTIAL`.

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service \
  -Dtest='AgentObservationSanitizerTest,AgentObservationBuilderTest' test
```

Expected: compilation fails because the sanitizer and builder do not exist.

- [ ] **Step 4: Implement `AgentObservationSanitizer`**

Implement explicit per-tool summaries rather than generic recursive serialization:

```java
public Map<String, Object> sanitizeInput(String toolName, Map<String, Object> arguments) {
    Map<String, Object> summary = new LinkedHashMap<>();
    copy(summary, arguments, "startDate");
    copy(summary, arguments, "endDate");
    copy(summary, arguments, "expectedVersion");
    if ("query_leave_policy".equals(toolName)) {
        summary.put("question", abbreviate(arguments.get("question"), 80));
    }
    if ("create_leave_pending".equals(toolName)) {
        summary.put("request", "使用用户原始请假描述，原因已隐藏");
    }
    return summary;
}
```

Implement `sanitizeResult` with allowlisted fields for:

- `query_leave_balance`
- `query_attendance`
- `query_leave_policy`
- `create_leave_pending`
- `confirm_leave_apply`
- `cancel_pending`

Also implement:

```java
public String evidenceSource(String toolName, ToolResult<?> result)
```

It returns only a short source label for `query_leave_policy` and `null` for
tools without a source.

Never copy arbitrary maps into the response.

- [ ] **Step 5: Implement `AgentObservationBuilder`**

The builder:

- generates `requestId` with `UUID.randomUUID()`
- records `startedAtNanos`
- appends ordered immutable step drafts
- calculates durations with `System.nanoTime()`
- accepts an optional `AgentDecisionObservation`
- freezes lists with `List.copyOf`
- derives `SUCCESS`, `FAILED`, or `PARTIAL` from recorded step/tool statuses

Expose methods with this shape:

```java
AgentObservationBuilder.AgentStepHandle startAgent(String agentName, String summary);
void finishAgent(
        AgentObservationBuilder.AgentStepHandle handle,
        AgentObservationStatus status,
        String summary
);
void addToolCall(
        AgentObservationBuilder.AgentStepHandle handle,
        AgentToolObservation tool
);
void decision(AgentDecisionObservation decision);
AgentObservationSnapshot build();
```

- [ ] **Step 6: Run focused tests and verify GREEN**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service \
  -Dtest='AgentObservationSanitizerTest,AgentObservationBuilderTest' test
```

Expected: all observation builder and sanitizer tests pass.

- [ ] **Step 7: Record a collection checkpoint**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and only intended observation files are newly
added in this task.

---

### Task 3: Attach Observations To The Spring AI MCP Model Agent

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/ai/service/SpringAiMcpModelAgent.java`
- Create: `hr-service/src/test/java/com/example/hrai/ai/service/SpringAiMcpModelAgentTest.java`

- [ ] **Step 1: Write a failing model-agent observation test**

Use a stub `ChatModel` that invokes `query_leave_balance`, plus mocked
`HrMcpCaller`, `CurrentUserService`, `ToolTokenService`, and
`AgentMemoryService`.

Assert:

```java
assertThat(response.getObservation()).isNotNull();
assertThat(response.getObservation().steps())
        .extracting(AgentObservationStep::agentName)
        .containsExactly("SpringAiMcpModelAgent");
AgentToolObservation tool = response.getObservation().steps().get(0).toolCalls().get(0);
assertThat(tool.toolName()).isEqualTo("query_leave_balance");
assertThat(tool.traceId()).isEqualTo("trace-balance");
assertThat(tool.inputSummary()).doesNotContainKey("toolToken");
verify(hrMcpCaller, times(1)).call(eq("query_leave_balance"), anyMap());
```

Add a pending Tool case and assert serialized observation text does not contain:

```text
toolToken
idempotencyKey
完整请假原因
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service -Dtest=SpringAiMcpModelAgentTest test
```

Expected: response observation is null.

- [ ] **Step 3: Add request-scoped collection to `SpringAiMcpModelAgent`**

At the beginning of `chat`:

```java
AgentObservationBuilder observation = new AgentObservationBuilder();
AgentObservationBuilder.AgentStepHandle modelStep = observation.startAgent(
        "SpringAiMcpModelAgent",
        "大模型根据用户问题选择 MCP Tool"
);
ModelFacingMcpTools tools = new ModelFacingMcpTools(
        user, token, sessionId, message, observation, modelStep
);
```

In `ModelFacingMcpTools.call`:

```java
long startedAt = System.nanoTime();
ToolResult<Object> result = hrMcpCaller.call(toolName, secured);
observation.addToolCall(modelStep, new AgentToolObservation(
        toolName,
        result.success() ? SUCCESS : FAILED,
        Math.max(0, (System.nanoTime() - startedAt) / 1_000_000),
        result.traceId(),
        sanitizer.sanitizeInput(toolName, modelArguments),
        sanitizer.sanitizeResult(toolName, result),
        sanitizer.evidenceSource(toolName, result),
        result.success() ? null : result.code()
));
```

Do not pass `secured` to the sanitizer because it contains server-injected
`toolToken` and `sessionId`.

Before returning:

```java
observation.finishAgent(modelStep, SUCCESS, "模型已完成工具调用并生成回复");
return new AiChatResponse(
        "MCP_MODEL_RESPONSE",
        reply,
        data,
        observation.build()
);
```

On a Tool business failure, record a failed Tool observation before returning
or rethrowing. Observation code must not invoke `hrMcpCaller` a second time.

- [ ] **Step 4: Run model-agent tests and verify GREEN**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service \
  -Dtest='SpringAiMcpModelAgentTest,AgentToolCallLoggerTest,AgentToolCallGuardTest' test
```

Expected: all tests pass and the caller verification remains exactly one Tool call.

- [ ] **Step 5: Record a model-agent checkpoint**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. Do not stage or commit the already-modified
`SpringAiMcpModelAgent.java`.

---

### Task 4: Attach Ordered Observations To Multi-Agent Coordination

**Files:**
- Modify: `hr-service/src/main/java/com/example/hrai/ai/multiagent/DefaultCoordinatorAgent.java`
- Modify: `hr-service/src/test/java/com/example/hrai/ai/multiagent/DefaultCoordinatorAgentTest.java`

- [ ] **Step 1: Extend coordinator tests and verify RED**

For the annual leave salary scenario assert:

```java
AgentObservationSnapshot observation = response.getObservation();
assertThat(observation.steps())
        .extracting(AgentObservationStep::agentName)
        .containsExactly("CoordinatorAgent", "PolicyAgent", "LeaveAgent", "SalaryAgent");
assertThat(observation.steps().get(1).toolCalls().get(0).traceId()).isEqualTo("p1");
assertThat(observation.steps().get(2).toolCalls().get(0).traceId()).isEqualTo("l1");
assertThat(observation.decision().outcome()).contains("通常不影响工资");
```

For attendance assert the order:

```text
CoordinatorAgent, PolicyAgent, AttendanceAgent, SalaryAgent
```

Verify each mocked sub-Agent is called once.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service -Dtest=DefaultCoordinatorAgentTest test
```

Expected: observation is null.

- [ ] **Step 3: Wrap existing child Agent calls with observation timing**

Keep the existing call order and business logic. Wrap each existing call
directly so the implementation never evaluates a child Agent twice:

```java
AgentObservationBuilder.AgentStepHandle policyStep =
        observation.startAgent("PolicyAgent", "查询请假与薪酬制度");
PolicyAgentResult policy = null;
if (plan.needPolicy()) {
    try {
        policy = policyAgent.query(context);
        observation.addToolCall(policyStep, observedPolicyTool(policy));
        observation.finishAgent(
                policyStep,
                policy.success() ? SUCCESS : FAILED,
                policy.success() ? "已获得制度依据" : "制度查询失败"
        );
    } catch (RuntimeException exception) {
        observation.finishAgent(policyStep, FAILED, "PolicyAgent 执行异常");
        throw exception;
    }
}
```

Only create the step when the plan requires that Agent. Repeat the same
start/call/map/finish pattern for AttendanceAgent, LeaveAgent, and SalaryAgent.
Use private mapping methods with these concrete signatures:

```java
private AgentToolObservation observedPolicyTool(PolicyAgentResult result);
private AgentToolObservation observedAttendanceTool(AttendanceAgentResult result);
private AgentToolObservation observedLeaveTool(LeaveAgentResult result);
```

Each mapping method consumes the already-returned result and must not call MCP
or a child Agent.

Map the existing structured results as follows:

- `PolicyAgentResult`: source, abbreviated policy summary, `traceId`
- `AttendanceAgentResult`: record count, late count, `traceId`
- `LeaveAgentResult`: relevant balance summary or masked pending ID, `traceId`
- `SalaryImpactResult`: no Tool call; decision summary and basis

Create a Coordinator step first, finish it after the plan is created, and set:

```java
observation.decision(new AgentDecisionObservation(
        salary.summary(),
        salary.basis(),
        salary.needsHumanConfirmation()
));
```

Return:

```java
return new AiChatResponse(
        "MULTI_AGENT_RESPONSE",
        buildReply(result),
        result,
        observation.build()
);
```

- [ ] **Step 4: Run Multi-Agent tests and verify GREEN**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service \
  -Dtest='DefaultCoordinatorAgentTest,AgentDispatchPlannerTest,McpSubAgentTest,SalaryAgentTest' test
```

Expected: all Multi-Agent tests pass.

- [ ] **Step 5: Record a Multi-Agent checkpoint**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. Leave implementation changes uncommitted for
the user to review with the rest of the current worktree.

---

### Task 5: Add Frontend Message Binding And Right-Side Panel

**Files:**
- Modify: `hr-web/src/App.vue`
- Modify: `hr-web/src/style.css`

- [ ] **Step 1: Add TypeScript observation contracts**

Add:

```ts
type AgentObservationStatus = 'SUCCESS' | 'FAILED' | 'PARTIAL';

type AgentToolObservation = {
  toolName: string;
  status: AgentObservationStatus;
  durationMs: number;
  traceId?: string | null;
  inputSummary: Record<string, unknown>;
  resultSummary: Record<string, unknown>;
  evidenceSource?: string | null;
  errorCode?: string | null;
};

type AgentObservationStep = {
  agentName: string;
  status: AgentObservationStatus;
  durationMs: number;
  summary: string;
  toolCalls: AgentToolObservation[];
};

type AgentObservationSnapshot = {
  requestId: string;
  status: AgentObservationStatus;
  totalDurationMs: number;
  summarySteps: string[];
  steps: AgentObservationStep[];
  decision?: {
    outcome: string;
    basis: string;
    needsHumanConfirmation: boolean;
  } | null;
};
```

Extend:

```ts
type AssistantMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  source?: string;
  observation?: AgentObservationSnapshot | null;
};

type AgentChatResponse = {
  intent: string;
  reply: string;
  data: unknown;
  observation?: AgentObservationSnapshot | null;
};
```

- [ ] **Step 2: Bind observations to chat history**

Add:

```ts
const selectedObservationMessageId = ref<number | null>(null);
const selectedObservation = computed(() =>
  aiChatMessages.value.find((message) =>
    message.id === selectedObservationMessageId.value
  )?.observation ?? null
);
```

When adding the assistant response:

```ts
const assistantMessageId = Date.now() + 1;
aiChatMessages.value.push({
  id: assistantMessageId,
  role: 'assistant',
  content: response.reply,
  observation: response.observation
});
if (response.observation) {
  selectedObservationMessageId.value = assistantMessageId;
}
```

Reset `selectedObservationMessageId` in `clearResults`.

- [ ] **Step 3: Render the two-column layout**

Inside `#ai`, replace the single assistant body wrapper with:

```vue
<div class="agent-workspace">
  <div class="agent-conversation">
    <div class="quick-prompts">现有快捷问题按钮</div>
    <div class="assistant-thread">现有聊天消息和待确认卡片</div>
    <form class="assistant-composer">现有消息输入框和发送按钮</form>
  </div>

  <aside class="observation-panel" aria-label="Agent 调用详情">
    <template v-if="selectedObservation">
      <div class="observation-header">
        <strong>调用详情</strong>
        <span>{{ selectedObservation.status }} · {{ selectedObservation.totalDurationMs }} ms</span>
      </div>
      <ol class="observation-timeline">
        <li v-for="step in selectedObservation.steps" :key="step.agentName">
          <strong>{{ step.agentName }}</strong>
          <p>{{ step.summary }}</p>
          <article v-for="tool in step.toolCalls" :key="tool.toolName + tool.traceId">
            <strong>{{ tool.toolName }}</strong>
            <small>{{ tool.status }} · {{ tool.durationMs }} ms · {{ tool.traceId }}</small>
            <details>
              <summary>查看脱敏数据摘要</summary>
              <pre>{{ pretty({ input: tool.inputSummary, result: tool.resultSummary }) }}</pre>
            </details>
          </article>
        </li>
      </ol>
      <div v-if="selectedObservation.decision" class="observation-decision">
        <strong>{{ selectedObservation.decision.outcome }}</strong>
        <p>{{ selectedObservation.decision.basis }}</p>
      </div>
    </template>
    <p v-else class="empty">发送一个问题后，这里会展示 Agent 调用过程。</p>
  </aside>
</div>
```

Make assistant messages selectable only when observation exists:

```vue
<div
  :class="{
    observable: message.role === 'assistant' && message.observation,
    selected: message.id === selectedObservationMessageId
  }"
  @click="message.observation && (selectedObservationMessageId = message.id)"
>
```

Use `<details>` for input/result summaries. Render values through the existing
`pretty(...)` helper; the backend already guarantees sanitization.

- [ ] **Step 4: Add responsive panel styles**

Add:

```css
.agent-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.8fr);
  gap: 16px;
  align-items: start;
}

.observation-panel {
  position: sticky;
  top: 18px;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
  background: #fff;
  padding: 16px;
}

.assistant-message.observable {
  cursor: pointer;
}

.assistant-message.observable.selected {
  border-color: #0f766e;
  box-shadow: 0 0 0 1px #0f766e;
}
```

Add timeline, status pill, Tool card, and decision card styles following the
existing teal/gray visual language.

In the existing `@media (max-width: 900px)` block:

```css
.agent-workspace {
  grid-template-columns: 1fr;
}

.observation-panel {
  position: static;
}
```

- [ ] **Step 5: Build the frontend**

Run:

```bash
cd hr-web && npm run build
```

Expected: `vue-tsc -b` and Vite build complete successfully.

- [ ] **Step 6: Record a frontend checkpoint**

```bash
cd .. && git diff --check
git status --short
```

Expected: the frontend build and whitespace validation pass. Do not stage or
commit `App.vue` or `style.css` because both already contain earlier
uncommitted user-approved work.

---

### Task 6: Run Regression And Real Browser Verification

**Files:**
- Verify only; no planned production file changes.

- [ ] **Step 1: Run the related backend suite**

Run:

```bash
mvn -s tmp-empty-settings.xml -pl hr-service \
  -Dtest='com.example.hrai.ai.observation.*Test,SpringAiMcpModelAgentTest,DefaultCoordinatorAgentTest,HrMcpChatControllerTest,HrMcpChatServiceTest,McpSubAgentTest,HrMcpToolServiceTest,AgentToolCallGuardTest,AgentToolCallLoggerTest' test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run the full project test suite**

Run:

```bash
mvn -s tmp-empty-settings.xml test
```

Expected: all modules build and all tests pass.

- [ ] **Step 3: Run frontend build and whitespace validation**

Run:

```bash
cd hr-web && npm run build
cd .. && git diff --check
```

Expected: both commands exit with code `0`.

- [ ] **Step 4: Restart `hr-service` with the current Dify and model environment**

Confirm the process reads:

```text
DIFY_ENABLED=true
DIFY_BASE_URL=http://localhost/v1
DIFY_AGENT_API_KEY is present and non-empty
```

Do not print API key values in logs or the final response.

- [ ] **Step 5: Verify the three target conversations in the browser**

Use the employee account and ask:

```text
查询我的年假余额
我想请病假，会不会扣工资？
我这周迟到了两次，会影响工资吗？
```

Verify:

- latest assistant response selects itself
- clicking an older response switches the right panel
- balance shows `SpringAiMcpModelAgent` and `query_leave_balance`
- sick leave shows `CoordinatorAgent`, `PolicyAgent`, and `SalaryAgent`
- attendance shows `CoordinatorAgent`, `PolicyAgent`, `AttendanceAgent`, and `SalaryAgent`
- Tool cards show duration and `traceId`
- no `toolToken`, JWT, idempotency key, or complete leave reason appears
- no confirmation or write action is triggered during these read-only checks

- [ ] **Step 6: Re-run validation after any verification fix**

If browser verification reveals a defect, edit only the file responsible for
that defect, then repeat Steps 1 through 3. Leave the shared worktree
uncommitted so unrelated existing changes are never accidentally staged.
