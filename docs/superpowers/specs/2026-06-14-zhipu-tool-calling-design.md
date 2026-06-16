# ZhiPu Tool Calling Upgrade Design

## Goal

Use ZhiPu GLM through Spring AI Tool Calling for HR request understanding and
tool selection while preserving the Redis-backed leave confirmation state
machine and deterministic keyword fallback.

## Request Routing

1. Exact confirmation and cancellation commands are handled first by
   `AiChatService` and never sent to the model.
2. Complete leave-application requests are converted to Redis pending state by
   the backend and never submitted directly.
3. Other requests are sent to ZhiPu GLM with query-only HR tools.
4. If the model is unavailable or fails, the existing deterministic routing is
   used as fallback.

## Tool Boundary

Tools visible to ZhiPu:

- `queryLeaveBalance`
- `queryAttendance`
- `queryLeavePolicy`

Backend-only operation:

- `applyLeave`

`applyLeave` has no `@Tool` annotation. It can only be invoked by the backend
after an exact confirmation command, matching Redis `userId + sessionId`
pending state, and successful pending confirmation.

Leave application parsing remains deterministic in `LeaveRequestParser`.
Application requests create pending state before model routing so the model
cannot invent or directly submit leave details.

## ZhiPu Configuration

The ZhiPu starter supplies the `ChatModel`. Chat auto-configuration is disabled
by default so local startup remains secret-free and deterministic. Runtime
enables it using:

```bash
export SPRING_AI_CHAT_MODEL=zhipuai
export ZHIPUAI_API_KEY=...
export ZHIPUAI_CHAT_MODEL=glm-4-air
```

## Fallback And Safety

- Exact confirm/cancel commands always use Redis state.
- ZhiPu failures fall back to keyword routing.
- Keyword leave application fallback creates pending state and never submits.
- `applyLeave` independently validates confirmation and session-scoped pending
  state before submission.
