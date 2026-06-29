# HR AI Assistant 项目总结

## 1. 项目定位

本项目是一个面向 HR SaaS 场景的员工智能助手 Demo，目标不是只做一个聊天框，而是把大模型能力接入真实企业业务系统。

系统围绕“员工自助 HR 服务”展开，支持假期余额查询、考勤查询、请假制度查询、请假待确认申请、薪酬影响判断等场景。项目重点展示 Spring Boot 业务系统如何结合 Spring AI Tool Calling、MCP、Dify RAG、Redis Memory 和 Multi-Agent 协作，实现可控、可观测、可验证的企业级 Agent。

## 2. 核心能力

- 员工登录后通过自然语言提问，后端根据当前身份执行业务查询。
- 大模型通过 Spring AI Tool Calling 自主选择工具，不再依赖关键词路由。
- HR 业务能力通过 MCP Server 暴露，Agent 只能通过 MCP Tool 调用业务服务。
- 制度查询优先复用 Dify 工作流和知识库，用于员工手册 RAG 问答。
- 请假写操作采用 pending + confirm 机制，禁止模型或前端直接提交真实申请。
- Redis Memory 保存待确认申请，支持用户继续回复“确认”或“取消”。
- Multi-Agent 用 CoordinatorAgent 调度 PolicyAgent、AttendanceAgent、LeaveAgent、SalaryAgent。
- 前端提供 Agent 调用链观测面板，可以看到子 Agent、Tool、traceId、耗时、状态和证据来源。

## 3. 总体架构

核心调用链：

```text
前端
-> POST /api/ai/mcp/chat
-> HrMcpChatController
-> HrMcpChatService
-> CoordinatorAgent 或 SpringAiMcpModelAgent
-> Spring AI ChatClient.tools(...).call()
-> ModelFacingMcpTools
-> StreamableHttpHrMcpCaller
-> MCP /mcp
-> HrMcpTools
-> HrMcpToolService
-> 现有 HR Service
```

制度查询链路：

```text
PolicyAgent
-> query_leave_policy
-> HrMcpToolService
-> KnowledgeBaseService / Dify Workflow
-> Dify 知识库
-> 返回制度摘要和 source
```

写操作链路：

```text
用户提出请假
-> create_leave_pending
-> Redis Memory 保存 pending
-> 前端展示待确认内容
-> 用户回复“确认”
-> confirm_leave_apply
-> LeaveService 创建真实请假申请
```

## 4. Multi-Agent 设计

CoordinatorAgent 只负责理解问题、选择子 Agent、汇总结构化结果，不直接写具体 HR 业务规则。

子 Agent 职责：

- PolicyAgent：查询制度依据，复用 `query_leave_policy` 和 Dify RAG。
- AttendanceAgent：查询考勤事实，复用 `query_attendance`。
- LeaveAgent：查询假期余额或创建请假 pending，复用 `query_leave_balance`、`create_leave_pending`。
- SalaryAgent：判断薪酬影响。当前没有独立薪酬接口时，先基于制度、考勤和假期结构化结果做保守判断。

示例问题：

```text
我下周想请三天年假，会不会影响工资？
-> PolicyAgent + LeaveAgent + SalaryAgent

我想请病假，会不会扣工资？
-> PolicyAgent + SalaryAgent

我这周迟到了两次，会影响工资吗？
-> AttendanceAgent + PolicyAgent + SalaryAgent
```

## 5. 安全控制

项目里对 Agent 调用企业系统做了多层限制：

- Tool Token 由后端生成，模型和前端不能伪造。
- MCP Tool 内部做权限校验，只允许员工查询自己的数据。
- 写操作必须先 pending，再由用户确认后提交。
- `confirm_leave_apply` 使用 pendingId、version、idempotencyKey 防止重复提交。
- AgentToolCallGuard 限制单次模型请求的 Tool 调用次数，避免模型循环调用产生费用。
- 同一个 Tool 在单次请求中禁止重复调用，降低失控风险。
- 审计日志会脱敏记录工具调用信息，避免敏感字段泄露。

## 6. 可观测性

前端可以查看本次 Agent 调用链，后端返回 observation 数据：

- 调用了哪些 Agent。
- 每个 Agent 成功、失败或部分成功。
- 每个 Tool 的名称、耗时、traceId、错误码。
- 制度查询结果来自 Dify 还是本地 mock。
- query_leave_policy 的证据来源，如 `dify-workflow-handbook`。

这解决了一个常见问题：用户不只想知道最终答案，也要知道答案是从哪里来的。

## 7. 关键类

- `HrMcpChatController`：MCP Agent 对话入口。
- `HrMcpChatService`：选择 Multi-Agent 或模型 Agent 的服务层。
- `SpringAiMcpModelAgent`：基于 Spring AI ChatClient 的模型 Tool Calling Agent。
- `DefaultCoordinatorAgent`：Multi-Agent 调度和汇总。
- `McpPolicyAgent`：制度查询子 Agent。
- `McpAttendanceAgent`：考勤查询子 Agent。
- `McpLeaveAgent`：假期查询和 pending 创建子 Agent。
- `SalaryAgent`：薪酬影响判断。
- `HrMcpTools`：MCP Server 工具入口。
- `HrMcpToolService`：权限、校验、幂等、审计和业务编排。
- `StreamableHttpHrMcpCaller`：MCP Client 调用封装。
- `AgentMemoryService`：pending 请假 Memory 管理。
- `AgentObservationBuilder`：Agent 调用链观测数据构建。

## 8. 面试讲法

可以这样介绍项目：

```text
我做了一个 HR 员工助手，把大模型接入真实企业业务系统。
它不是简单调用大模型聊天，而是通过 Spring AI Tool Calling 让模型自主选择工具，
再通过 MCP 标准化调用后端 HR Service。

对于制度类问题，我接入了 Dify 知识库做 RAG；
对于请假、考勤、余额这类业务问题，我复用已有 Service；
对于“会不会扣工资”这种复合问题，我加了 Multi-Agent，
由 CoordinatorAgent 调度制度、考勤、假期和薪酬判断子 Agent。

为了避免 Agent 失控，我做了 pending + confirm、Tool Token、权限校验、
幂等键、调用次数限制和观测链路。这样既能展示 AI 能力，也能保证企业业务安全。
```

## 9. 演示脚本

建议按下面顺序演示：

1. 登录员工账号 `zhangsan / 123456`。
2. 输入 `查询我的年假余额`，展示 Tool Calling 查询假期余额。
3. 输入 `我想请病假，会不会扣工资？`，展示 PolicyAgent + SalaryAgent。
4. 点击“查看本次 Agent 调用链”，展示 Dify 证据来源和 traceId。
5. 输入 `我这周迟到了两次，会影响工资吗？`，展示 AttendanceAgent + PolicyAgent + SalaryAgent。
6. 输入 `帮我请明天一天年假，原因是个人事务，但先不要提交`，展示 pending + confirm。
7. 说明不能直接提交，必须用户明确确认。

注意：演示时不要随便发送“确认”，否则会创建真实请假申请。

## 10. 项目亮点

- 不是纯 Prompt Demo，而是接入真实 HR Service。
- Tool Calling 和 MCP 分层清晰，模型不直接访问数据库。
- Dify 用于制度 RAG，业务系统用于权威数据查询。
- Multi-Agent 解决复合 HR 问题，子 Agent 结构化返回结果。
- 写操作有确认、幂等和 Memory，避免误提交。
- 有 traceId、调用链和证据来源，便于排查和演示。
- 保留单元测试和端到端验证，能证明链路真正跑通。

## 11. 后续优化方向

- 接入真实薪酬规则或薪酬查询接口，让 SalaryAgent 不只依赖制度摘要。
- 扩展 LeaveRequestParser，支持更多绝对日期和自然语言日期。
- 增加模型请求总超时和费用统计。
- 将 Agent observation 持久化，支持历史调用链查询。
- 对 Dify 返回内容做更细粒度的引用展示，提升制度答案可信度。
