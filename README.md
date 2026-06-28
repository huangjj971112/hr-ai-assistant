# hr-ai-assistant

面向 HR SaaS 场景的员工智能助手 Demo。项目把大模型接入真实 HR 业务系统，重点展示 Spring AI Tool Calling、MCP、Dify RAG、Redis Memory、Multi-Agent 协作、权限校验和调用链观测。

当前主线能力：

- 员工通过自然语言查询假期余额、考勤记录和请假制度。
- 大模型通过 Spring AI `ChatClient.tools(...).call()` 自主选择 Tool。
- HR 业务能力通过 MCP Server 暴露，Agent 只能经 MCP Tool 调用后端 Service。
- 制度查询复用 Dify 工作流和知识库，用于员工手册 RAG 问答。
- 请假写操作必须先创建 pending，再由用户明确确认后提交。
- Multi-Agent 支持复合问题分析，例如“请病假会不会扣工资”“迟到会不会影响工资”。
- 前端提供 Agent 调用链观测，可查看子 Agent、Tool、traceId、耗时、状态和证据来源。

更多项目讲解见：[docs/hr-agent-project-summary.md](docs/hr-agent-project-summary.md)。

## 技术栈

- Java 17
- Spring Boot 3.5.x
- Spring AI
- Spring AI MCP Server / Client
- Maven
- MyBatis-Plus
- PostgreSQL / H2 Test DB
- Redis Memory
- Spring Security + JWT
- Dify Workflow / Knowledge Base
- Vue 3 + Vite

## 项目结构

```text
hr-ai-assistant
├── pom.xml
├── docker-compose.yml
├── docs
├── hr-service              # HR/AI 业务服务、MCP Server、Agent、Dify 接入
├── hr-gateway              # Gateway，对外入口、JWT 校验、接口权限过滤
└── hr-web                  # Vue 前端工作台
```

## 核心架构

MCP Agent 调用链：

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

请假确认链路：

```text
用户提出请假
-> create_leave_pending
-> Redis Memory 保存 pending
-> 前端展示待确认内容
-> 用户回复“确认”
-> confirm_leave_apply
-> LeaveService 创建真实请假申请
```

## Multi-Agent

`CoordinatorAgent` 只负责理解问题、调度子 Agent 和汇总结果，不直接写 HR 业务规则。

子 Agent：

- `PolicyAgent`：查询制度依据，复用 `query_leave_policy` 和 Dify RAG。
- `AttendanceAgent`：查询考勤事实，复用 `query_attendance`。
- `LeaveAgent`：查询假期余额或创建请假 pending，复用 `query_leave_balance`、`create_leave_pending`。
- `SalaryAgent`：判断薪酬影响。当前没有独立薪酬接口时，先基于制度、考勤和假期结构化结果做保守判断。

示例：

```text
我下周想请三天年假，会不会影响工资？
-> PolicyAgent + LeaveAgent + SalaryAgent

我想请病假，会不会扣工资？
-> PolicyAgent + SalaryAgent

我这周迟到了两次，会影响工资吗？
-> AttendanceAgent + PolicyAgent + SalaryAgent
```

## MCP Tools

`hr-service` 默认通过 Streamable HTTP 暴露 MCP Server：

```text
http://localhost:8091/mcp
```

当前 MCP Tool：

- `query_leave_balance`
- `query_attendance`
- `query_leave_policy`
- `create_leave_pending`
- `confirm_leave_apply`
- `cancel_pending`

安全约束：

- 每次 Tool 调用都需要后端生成的短期 `toolToken`。
- 浏览器和大模型不能直接伪造 `toolToken`、`sessionId`、`pendingId`、`version`、`idempotencyKey`。
- 请假不提供直接提交 Tool，必须 `create_leave_pending` 后再 `confirm_leave_apply`。
- pending 使用 `pendingId + version + idempotencyKey` 做确认和幂等控制。
- AgentToolCallGuard 限制单次模型请求 Tool 调用次数，避免循环调用产生费用。
- MCP Tool 审计日志会脱敏记录调用信息，并返回 `traceId` 便于排查。

可通过下面配置关闭 MCP Server：

```bash
export MCP_SERVER_ENABLED=false
```

## 启动依赖

项目默认使用 PostgreSQL 和 Redis。可用 Docker Compose 启动：

```bash
docker compose up -d
```

默认数据库：

```text
database: hr_ai_assistant
username: postgres
password: postgres
jdbc: jdbc:postgresql://localhost:5432/hr_ai_assistant
```

应用启动时会执行 `schema.sql` 建表，并由 `DemoDataInitializer` 初始化演示数据。

确认数据：

```bash
docker exec -it hr-ai-assistant-postgres psql -U postgres -d hr_ai_assistant
```

常用 SQL：

```sql
select apply_no, employee_name, leave_type, start_time, end_time, status
from leave_application
order by start_time desc;

select workflow_id, workflow_type, employee_name, intent, status, started_at, finished_at
from ai_workflow_run
order by started_at desc;

select workflow_id, step_order, step_name, status, message
from ai_workflow_step_log
order by id;
```

## 启动后端

服务端口：

```text
hr-gateway   8090，对外入口，负责 JWT 校验和接口权限过滤
hr-service   8091，业务服务，负责 HR/AI 业务、MCP Server 和数据权限边界
```

先启动业务服务：

```bash
mvn -pl hr-service spring-boot:run
```

再启动 Gateway：

```bash
mvn -pl hr-gateway spring-boot:run
```

健康检查：

```bash
curl http://localhost:8090/actuator/health
```

如果本机 Maven 代理配置有问题，可以使用仓库里的空 settings：

```bash
mvn -s tmp-empty-settings.xml -pl hr-service spring-boot:run
```

如果暂时不启动 PostgreSQL，也可以用 H2 快速演示：

```bash
mvn -s tmp-empty-settings.xml -pl hr-service -Dspring-boot.run.profiles=local spring-boot:run
```

`local` profile 的数据只存在内存里，服务重启后会重新初始化。

## 启动前端

前端是 Vue 3 + Vite，开发环境会把 `/api` 代理到 Gateway：

```bash
cd hr-web
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

演示账号：

```text
员工端：zhangsan / 123456
HR 端：hr_admin / 123456
```

推荐启动顺序：

```text
PostgreSQL + Redis -> hr-service:8091 -> hr-gateway:8090 -> hr-web:5173
```

## 大模型配置

启用智谱 GLM Tool Calling：

```bash
export SPRING_AI_CHAT_MODEL=zhipuai
export ZHIPUAI_API_KEY=你的智谱API_KEY
export ZHIPUAI_CHAT_MODEL=glm-4-air
```

`POST /api/ai/mcp/chat` 需要可用的 `ChatModel`。模型不可用时会返回 `MCP_MODEL_UNAVAILABLE`，模型调用失败时会返回 `MCP_MODEL_CALL_FAILED`。

智谱 Embedding 和 Image Model 默认关闭，避免无关模型在没有 API Key 时影响本地启动。

## Dify 配置

在 Dify 中创建制度查询工作流或聊天助手应用，将员工手册上传到知识库并关联到应用，然后复制应用 API Key。

本地 Docker Dify 常用地址：

```bash
export DIFY_ENABLED=true
export DIFY_BASE_URL=http://localhost/v1
export DIFY_API_KEY=app-your-dify-workflow-api-key
export DIFY_WORKFLOW_PATH=/workflows/run
export DIFY_AGENT_API_KEY=app-your-dify-agent-api-key
export DIFY_AGENT_PATH=/chat-messages
```

云端 Dify 可使用：

```bash
export DIFY_BASE_URL=https://api.dify.ai/v1
```

配置项：

```yaml
dify:
  enabled: ${DIFY_ENABLED:false}
  base-url: ${DIFY_BASE_URL:https://api.dify.ai/v1}
  api-key: ${DIFY_API_KEY:}
  workflow-path: ${DIFY_WORKFLOW_PATH:/workflows/run}
  agent-api-key: ${DIFY_AGENT_API_KEY:}
  agent-path: ${DIFY_AGENT_PATH:/chat-messages}
  timeout-seconds: ${DIFY_TIMEOUT_SECONDS:60}
```

未配置 Dify 时，相关接口会返回 mock 内容，方便本地启动和测试。

## 接口示例

登录：

```bash
curl -X POST "http://localhost:8090/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "password": "123456"
  }'
```

返回里的 `accessToken` 后续放到请求头：

```text
Authorization: Bearer <accessToken>
```

MCP Agent 对话：

```bash
curl -X POST "http://localhost:8090/api/ai/mcp/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "sessionId": "employee-chat-001",
    "message": "我想请病假，会不会扣工资？"
  }'
```

查询当前员工：

```bash
curl "http://localhost:8090/api/employee/me" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>"
```

查询当前员工假期余额：

```bash
curl "http://localhost:8090/api/employee/me/leave/balance" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>"
```

员工手册问答：

```bash
curl -X POST "http://localhost:8090/api/employee/handbook/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "question": "员工年假制度是什么？"
  }'
```

Dify Workflow 入口：

```bash
curl -X POST "http://localhost:8090/api/ai/dify/workflow/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "message": "帮我查一下我的年假余额",
    "agentType": "WORKFLOW"
  }'
```

## 演示脚本

推荐按下面顺序演示：

1. 登录员工账号 `zhangsan / 123456`。
2. 输入 `查询我的年假余额`，展示 Tool Calling 查询假期余额。
3. 输入 `我想请病假，会不会扣工资？`，展示 PolicyAgent + SalaryAgent。
4. 点击“查看本次 Agent 调用链”，展示 Dify 证据来源和 traceId。
5. 输入 `我这周迟到了两次，会影响工资吗？`，展示 AttendanceAgent + PolicyAgent + SalaryAgent。
6. 输入 `帮我请明天一天年假，原因是个人事务，但先不要提交`，展示 pending + confirm。
7. 说明不能直接提交，必须用户明确确认。

注意：演示时不要随便发送“确认”，否则会创建真实请假申请。

## 测试

运行全部测试：

```bash
mvn test
```

如果本机 Maven settings 有代理问题：

```bash
mvn -s tmp-empty-settings.xml test
```

运行 `hr-service` 测试：

```bash
mvn -s tmp-empty-settings.xml -pl hr-service test
```

当前测试覆盖 Spring 上下文启动、HR 业务接口、AI 对话入口、多轮请假确认、Redis Memory、MCP Tool、Multi-Agent、Dify 员工手册问答、Dify Workflow mock 分支和 Tool API 权限控制。

## 关键类

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

## 后续扩展方向

- 接入真实薪酬规则或薪酬查询接口，让 SalaryAgent 不只依赖制度摘要。
- 扩展 LeaveRequestParser，支持更多绝对日期和自然语言日期。
- 增加模型请求总超时和费用统计。
- 将 Agent observation 持久化，支持历史调用链查询。
- 对 Dify 返回内容做更细粒度的引用展示，提升制度答案可信度。
- 引入 Flyway/Liquibase 替代 `schema.sql`，管理表结构和初始化数据。
