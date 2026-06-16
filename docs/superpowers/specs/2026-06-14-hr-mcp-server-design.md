# HR MCP Server Design

## Goal

Expose the existing employee assistant HR capabilities through a Java/Spring
Boot MCP Server without duplicating business logic or weakening the existing
Redis pending-confirmation safety boundary.

## Architecture

The MCP Server runs inside the existing `hr-service` application using the
Spring AI WebMVC Streamable HTTP transport. The endpoint is `/mcp`.

`HrMcpTools` is a protocol adapter only. It declares the six MCP tool names and
delegates every call to `HrMcpToolService`. `HrMcpToolService` validates the
short-lived `toolToken`, resolves the current employee, and delegates to the
existing `LeaveTools`, `AttendanceTools`, `KnowledgeTools`,
`LeaveRequestParser`, and `AgentMemoryService`.

No independent MCP service or duplicate HR business implementation is added.

## Tools

- `query_leave_balance`
- `query_attendance`
- `query_leave_policy`
- `create_leave_pending`
- `confirm_leave_apply`
- `cancel_pending`

Each tool accepts one explicit request DTO and returns `ToolResult<T>`.

## Authentication And Authorization

Every MCP request DTO contains a backend-generated short-lived `toolToken`.
`HrMcpToolService` parses it with the existing `ToolTokenService` and validates
the required scope.

Employee identity comes only from the token. Employee callers cannot query or
operate on another employee. The MCP client cannot override `userId`, role, or
employee identity.

## Pending Confirmation Safety

`create_leave_pending` validates the leave request, saves it through
`AgentMemoryService`, and never writes a leave application.

`confirm_leave_apply` requires the same token identity and `sessionId`, marks
the Redis pending object confirmed, then delegates submission to the existing
backend-only `HrAgentTools.applyLeave` method. Successful submission deletes
the pending object.

`cancel_pending` requires the same identity and `sessionId`, then removes the
pending object.

There is no direct submit MCP tool.

## Transport And Configuration

Use Spring AI `spring-ai-starter-mcp-server-webmvc` with synchronous Streamable
HTTP:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        type: SYNC
        name: hr-mcp-server
        version: 1.0.0
        streamable-http:
          mcp-endpoint: /mcp
```

Only tool capability is needed; resources, prompts, and completions are
disabled.

## Testing

- Reflection test proves exactly six MCP tools are exposed and there is no
  direct submit tool.
- Service tests cover token identity, query delegation, pending creation,
  confirmation, cancellation, missing pending, and session isolation.
- Application context and full existing test suite remain green.

