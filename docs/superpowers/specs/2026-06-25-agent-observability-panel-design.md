# Agent Observability Panel Design

## Goal

Add a learning and debugging view to the existing employee assistant without
changing the HR business flow. The chat remains the primary employee
experience, while a fixed right-side panel explains how the latest or selected
historical response was produced.

The first version must reuse the existing `AiChatResponse`, Multi-Agent
structured results, MCP `traceId`, tool audit data, and frontend chat state. It
must not introduce a separate monitoring service, real-time event stream, or a
new audit-query API.

## User Experience

The employee assistant uses a two-column layout:

- Left: the existing chat thread and composer.
- Right: a fixed observability panel for the selected assistant response.

After each successful response, the panel automatically shows the latest
request. Clicking an earlier assistant response selects that response and
restores its observation snapshot.

The panel has two information levels:

1. Employee summary: friendly steps such as "queried leave policy" and
   "completed salary impact analysis".
2. Technical details: Agent name, MCP Tool name, status, duration, `traceId`,
   sanitized input summary, sanitized result summary, evidence source, and
   decision basis.

On narrow screens, the right panel moves below the chat instead of shrinking
the conversation to an unusable width.

## Response Contract

`AiChatResponse` adds an optional top-level `observation` field. Existing
`intent`, `reply`, and `data` fields retain their current meaning, and the
existing three-argument constructor remains available.

```text
AgentObservationSnapshot
  requestId
  status
  totalDurationMs
  summarySteps[]
  steps: AgentObservationStep[]
    agentName
    status
    durationMs
    summary
    toolCalls: AgentToolObservation[]
      toolName
      status
      durationMs
      traceId
      inputSummary
      resultSummary
      evidenceSource
  decision: AgentDecisionObservation
    outcome
    basis
    needsHumanConfirmation
```

The implementation uses these DTO names:

- `AgentObservationSnapshot`
- `AgentObservationStep`
- `AgentToolObservation`
- `AgentDecisionObservation`
- `AgentObservationStatus`

The frontend must not infer the execution timeline by inspecting arbitrary
`PolicyAgentResult`, `AttendanceAgentResult`, or `LeaveAgentResult` fields.

For the normal MCP model-agent path, the snapshot is built from the tool calls
captured by `ModelFacingMcpTools`. For the Multi-Agent path, the Coordinator
records each selected sub-Agent and maps its structured result and MCP
`traceId` into the same observation contract.

## Collection Flow

Each request creates an in-memory request-scoped observation collector:

1. Record request start and generate a request ID.
2. Record Coordinator or model-agent selection.
3. Record each sub-Agent start and completion when applicable.
4. Record MCP Tool start and completion, including duration and returned
   `traceId`.
5. Build sanitized input and result summaries.
6. Record the final salary or business decision basis.
7. Freeze the collector into an immutable response snapshot.

Observation collection must not query business services again. It observes
existing calls only, so enabling the panel does not add model cost or duplicate
Tool Calling.

## Sanitization

The observation response must never expose:

- `toolToken`
- JWT or authorization headers
- `idempotencyKey`
- Redis keys
- full pending objects when they contain protected fields
- full free-text leave reasons
- raw model prompts
- model chain-of-thought or hidden reasoning

Allowed summaries include:

- requested date range
- leave type
- requested day count
- attendance record count and late count
- leave balance values relevant to the question
- policy source name and short policy summary
- pending ID in shortened or masked form
- stable error code

Sanitization is performed in the backend DTO builder. The frontend must never
receive sensitive values and then attempt to hide them with CSS.

## Frontend State

Each assistant message gains an optional observation snapshot:

```text
AssistantMessage
  id
  role
  content
  source
  observation?
```

The frontend stores `selectedObservationMessageId`. A new assistant response
selects itself automatically. Clicking an assistant message with an observation
updates the selected ID and right panel. User messages and assistant messages
without observation data are not selectable.

The right panel displays:

- overall status and total duration
- ordered Agent timeline
- nested MCP Tool calls
- friendly summaries by default
- expandable sanitized technical details
- empty state before the first observable response

## Error Handling

Observation failures must not replace a valid business answer. If observation
mapping fails, the response is returned normally with an absent or partial
snapshot and a server warning log.

Failed Agent and Tool steps remain visible with:

- failed status
- stable error code
- duration
- `traceId` when one was created
- sanitized error summary

The panel must clearly distinguish:

- business result succeeded
- Tool returned a business failure
- model or transport threw an exception
- observation data is unavailable

## Compatibility

Existing pending and confirmation behavior remains unchanged. The
observability feature is read-only and must not add buttons that submit,
confirm, cancel, approve, or retry write operations.

Existing clients that only read `intent`, `reply`, and `data` continue to work.
The new top-level `observation` field is additive and must not replace or wrap
existing Multi-Agent data.

## Testing

Backend tests cover:

- model-agent Tool calls become sanitized observation steps
- Multi-Agent dispatch order becomes an ordered Agent timeline
- `traceId`, status, and duration are mapped correctly
- sensitive fields never appear in serialized observations
- observation mapping failure does not suppress the business response
- no extra MCP or HR Service call is made for observation

Frontend verification covers:

- latest response is selected automatically
- clicking a historical assistant response switches the panel
- messages without observations are not selectable
- technical details expand and collapse
- empty, success, partial, and failed states render
- mobile layout places the panel below the conversation

## Out Of Scope

The first version does not include:

- live streaming animations during Agent execution
- raw JSON request or response dumps
- model chain-of-thought
- querying historical audit records across sessions
- charts, alerting, dashboards, or OpenTelemetry export
- a separate administrator monitoring page
