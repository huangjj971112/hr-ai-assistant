# Frontend MCP Agent Design

## Goal

Allow the existing employee Agent chat panel to explicitly route requests through
the HR MCP Server without exposing `toolToken` or MCP session details to the
browser.

## Architecture

Add `POST /api/ai/mcp/chat` alongside the existing `/api/ai/chat`. The endpoint
uses the authenticated employee identity to create a five-minute tool token,
maps supported employee messages to one of the six approved MCP tools, invokes
the local Streamable HTTP MCP endpoint, and returns the existing
`AgentChatResponse` shape.

The frontend adds a Local Agent / MCP Agent selector. Local Agent keeps calling
`/api/ai/chat`; MCP Agent calls `/api/ai/mcp/chat` with the same browser-owned
conversation `sessionId`.

## Supported MCP Chat Intents

- Balance query: `query_leave_balance`
- Attendance query: `query_attendance`
- Leave policy query: `query_leave_policy`
- Leave application message: `create_leave_pending`
- Confirmation: `confirm_leave_apply`
- Cancellation: `cancel_pending`

Unsupported messages return a clear response and do not invoke arbitrary tools.

## Security And State

- The browser receives only the normal login JWT.
- The backend generates the short-lived `toolToken`.
- Pending leave state remains in the existing Redis-backed `AgentMemoryService`.
- Confirmation and cancellation must use the same authenticated user and
  frontend `sessionId`.
- No direct leave-submit tool is introduced.

## Testing

- Unit-test message-to-tool routing, token generation, MCP argument payloads,
  response mapping, and unsupported messages.
- Integration-test the endpoint with a mocked MCP caller.
- Run the existing backend and frontend builds.

