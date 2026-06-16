# Agent Memory And Spring AI Tool Calling Design

## Goal

Upgrade the existing `/api/ai/chat` endpoint into a session-aware employee agent that can select HR query tools and can only submit leave after an explicit, same-session confirmation.

## Architecture

- Keep `hr-gateway` as the public entry and extend the existing `hr-service`.
- Add `sessionId` to `AiChatRequest`; resolve `userId` and employee identity only through `CurrentUserService`.
- Put confirmation and cancellation handling before any model call.
- Store `PendingLeaveApplyDTO` through `AgentMemoryService`, keyed by `agent:pending_leave_apply:{userId}:{sessionId}`, with a configurable 20-minute TTL.
- Use Redis in normal runtime. Use an in-memory `AgentMemoryStore` only in tests or when Redis memory is explicitly disabled, so local startup remains possible without secrets or Redis.
- Keep a deterministic fallback router for local/test operation without an AI model. When a `ChatClient` is available, use it for query-tool selection and natural-language replies.
- Expose Spring AI `@Tool` methods through a focused `HrAgentTools` component. Every tool delegates to existing business services and returns `ToolResult<T>`.

## Leave Confirmation Flow

1. A leave request is parsed into leave type, start time, end time, and reason.
2. Missing required parameters return a clarification message.
3. A complete leave request is validated but not submitted, then saved as `PENDING_CONFIRMATION`.
4. Confirmation words (`确认`, `确定`, `提交`) load the pending request for the current `userId + sessionId`, mark it confirmed in memory, and call `applyLeave`.
5. `applyLeave` re-checks current identity, session, and confirmed memory before delegating to `LeaveService.apply`.
6. Successful submission deletes memory. Failed submission keeps the pending item so the employee can retry.
7. Cancellation words (`取消`, `算了`, `不提交`) delete only the current session pending item.

## Tool Safety

- Query tools may run immediately: `queryLeaveBalance`, `queryAttendance`, and `queryLeavePolicy`.
- `applyLeave` is never made available as a normal model-selected action for an unconfirmed request.
- `applyLeave` requires `confirmed=true` and a matching confirmed pending item in `AgentMemoryService`.
- Tool arguments cannot select another employee for an employee-role caller.
- `toolToken` is context-only and must never appear in a public response.
- The legacy leave workflow endpoint must no longer submit directly; it will return a pending confirmation through the same agent flow.

## Parsing

- Leave types: `年假 -> ANNUAL`, `病假 -> SICK`, `事假 -> PERSONAL`.
- Time periods: `上午 -> 09:00:00-12:00:00`, `下午 -> 14:00:00-18:00:00`, `全天 -> 09:00:00-18:00:00`.
- Relative dates use an injected `Clock` in Asia/Shanghai: today, tomorrow, the day after tomorrow, and weekdays such as next Monday.
- Public time formatting is `yyyy-MM-dd HH:mm:ss`.

## Prompt

The system prompt identifies the agent as an enterprise employee assistant, permits direct query tools, requires pending confirmation for business operations, prohibits fabricated data, binds relative language to current user/time, and forbids displaying `toolToken`.

## Error Handling

- Missing or expired pending state returns `当前没有待确认的申请`.
- Missing leave slots return a targeted clarification request.
- Tool exceptions become a friendly agent reply while retaining server-side error details.
- Redis failures do not silently submit anything.
- Repeated confirmation cannot submit twice because successful submission deletes the pending item.
- Cross-session confirmation cannot find or submit another session's pending item.

## Tests

Integration tests cover the eight requested scenarios. Focused unit tests cover parsing, confirmation/cancellation words, TTL behavior, and session isolation. Tests use a fixed clock and in-memory memory store; runtime Redis behavior is verified through serialization/store tests and configuration.
