# hr-ai-assistant

面向 HR SaaS 场景的 AI Assistant Demo。第一阶段先不用真实大模型，以规则方式模拟意图识别和工具调用，重点展示 Spring Boot + Spring AI 工程化分层、RESTful API、HR 业务接口和后续 RAG/Tool Calling 扩展边界。

## 技术栈

- Java 17
- Spring Boot 3.5.x
- Spring AI BOM
- Maven
- MyBatis-Plus
- PostgreSQL Driver / H2 Test DB
- Lombok
- RESTful API
- PostgreSQL 持久化，测试环境使用 H2
- Spring Security + JWT 登录认证

## 功能

- 登录认证、JWT 鉴权、角色控制
- 员工年假/病假余额查询
- 请假申请
- 候选人查询
- 面试安排
- JD 生成
- HR 制度知识库问答接口预留
- `/api/ai/chat` AI 对话入口，当前使用规则模拟意图识别
- `/api/ai/workflows/leave/apply` 请假 AI Workflow 编排
- AI Workflow 执行日志落库：`ai_workflow_run`、`ai_workflow_step_log`
- Dify 员工手册智能问答：`/api/employee/handbook/ask`

## 项目结构

```text
hr-ai-assistant
├── pom.xml                 # parent
├── docker-compose.yml
├── hr-service              # 业务服务：HR/AI 业务、数据库、Workflow
│   ├── pom.xml
│   └── src/main/java/com/example/hrai
├── hr-gateway              # 网关服务：JWT 校验、接口权限过滤、反向代理
│   ├── pom.xml
│   └── src/main/java/com/example/hraigateway
└── hr-web                  # Vue 前端工作台
    ├── package.json
    └── src
```

## 启动 PostgreSQL

项目默认连接本地 PostgreSQL：

```text
jdbc:postgresql://localhost:5432/hr_ai_assistant
```

可以用 Docker Compose 启动数据库：

```bash
docker compose up -d
```

默认账号：

```text
database: hr_ai_assistant
username: postgres
password: postgres
```

应用启动时会执行 `schema.sql` 建表，并由 `DemoDataInitializer` 初始化演示数据。

确认数据落库：

```bash
docker exec -it hr-ai-assistant-postgres psql -U postgres -d hr_ai_assistant
```

常用查看语句：

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

## 启动应用

现在项目拆成两个服务：

```text
hr-gateway   8090，对外入口，负责 JWT 校验和接口权限过滤
hr-service   8091，业务服务，负责 HR/AI 业务和数据权限边界
```

先启动业务服务：

```bash
cd hr-service
mvn spring-boot:run
```

再启动 Gateway：

```bash
cd ../hr-gateway
mvn spring-boot:run
```

也可以在项目根目录指定模块启动：

```bash
mvn -pl hr-service spring-boot:run
```

```bash
mvn -pl hr-gateway spring-boot:run
```

如果本机 Maven 配置了不可用代理，可临时使用：

```bash
mvn --settings /private/tmp/maven-central-only-settings.xml spring-boot:run
```

默认地址：

```text
http://localhost:8090
```

业务服务内部地址：

```text
http://localhost:8091
```

健康检查：

```bash
curl http://localhost:8090/actuator/health
```

业务接口建议始终通过 Gateway 访问，不要直接访问 `8091`。

## 启动前端

前端是 Vue 3 + Vite，开发环境会把 `/api` 代理到 Gateway：

```bash
cd hr-web
npm install
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

页面内置两个演示账号：

```text
员工端：zhangsan / 123456
HR 端：hr_admin / 123456
```

启动顺序建议：

```text
PostgreSQL -> hr-service:8091 -> hr-gateway:8090 -> hr-web:5173
```

如果本机 Docker/PostgreSQL 暂时没开，也可以用内存 H2 快速演示：

```bash
mvn --settings /private/tmp/maven-central-only-settings.xml -pl hr-service -Dspring-boot.run.profiles=local spring-boot:run
```

`local` profile 的数据只存在内存里，服务重启后会重新初始化演示数据。

## 接入 Dify 员工手册

在 Dify 中创建 Chatflow/聊天助手应用，将员工手册上传到知识库并关联到应用，然后复制应用的 API Key。

启动 `hr-service` 时配置：

```bash
export DIFY_ENABLED=true
export DIFY_API_KEY=app-your-dify-api-key
export DIFY_BASE_URL=https://api.dify.ai/v1
mvn -pl hr-service spring-boot:run
```

如果使用自建 Dify，把 `DIFY_BASE_URL` 改成你的服务地址，例如：

```bash
export DIFY_BASE_URL=http://localhost/v1
```

未配置 `DIFY_API_KEY` 时，接口会返回 mock 提示，不影响本地启动和测试。

## Dify Workflow + HR Tool Calling Demo

本 Demo 新增了企业系统 Tool Calling 调用链：

```text
hr-web
-> hr-gateway
-> hr-service
-> Dify Workflow
-> Dify HTTP 节点回调 hr-service Tool API
-> hr-service 内部调用 LeaveTools / 业务 Service
-> Tool JSON 返回 Dify
-> Dify 组织自然语言 answer
-> hr-service 返回前端
```

前端不要直接调用 Dify。Dify 也不直接访问数据库，只能调用 `hr-service` 暴露的 Tool API。Tool API 会重新做 JWT 用户识别和权限校验，普通员工只能查自己，HR 可以查指定员工。

### Workflow 配置

启动 `hr-service` 时可配置：

```bash
export DIFY_ENABLED=true
export DIFY_API_KEY=app-your-dify-workflow-api-key
export DIFY_BASE_URL=https://api.dify.ai/v1
export DIFY_WORKFLOW_PATH=/workflows/run
export DIFY_AGENT_API_KEY=app-your-dify-agent-api-key
export DIFY_AGENT_PATH=/chat-messages
mvn -pl hr-service spring-boot:run
```

`application.yml` 中对应配置：

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

未配置 `DIFY_API_KEY` 时，`/api/ai/dify/workflow/chat` 会返回 mock answer，保证本地演示可以直接跑通。

### Dify Workflow 节点建议

```text
1. Start：接收 message、userId、username、employeeName、role、tenantId、toolToken
2. 问题分类器：识别假期余额、请假记录或考勤查询
3. 条件分支：进入对应 HTTP Tool 节点
4. End：输出 HTTP Tool 返回结果
```

推荐分类：

```text
LEAVE_BALANCE：假期余额、年假余额、病假余额
LEAVE_RECORDS：请假记录、休假记录、今年请过哪些假
ATTENDANCE_RECORDS：考勤、签到、签退、迟到、早退
```

本地 Docker Dify 调用业务服务时，三个 HTTP 节点分别使用：

```text
POST http://host.docker.internal:8091/api/ai/tools/leave/balance
POST http://host.docker.internal:8091/api/ai/tools/leave/records
POST http://host.docker.internal:8091/api/ai/tools/attendance/records
POST http://host.docker.internal:8091/api/ai/tools/leave/apply
```

假期余额 Body：

```json
{
  "employeeName": "{{employeeName}}",
  "toolToken": "{{toolToken}}"
}
```

请假记录 Body：

```json
{
  "employeeName": "{{employeeName}}",
  "year": 2026,
  "toolToken": "{{toolToken}}"
}
```

考勤查询 Body：

```json
{
  "employeeName": "{{employeeName}}",
  "startDate": "{{参数提取器.startDate}}",
  "endDate": "{{参数提取器.endDate}}",
  "toolToken": "{{toolToken}}"
}
```

考勤日期通过参数提取器从用户问题中提取。查询单日时 `startDate` 和 `endDate` 可以设置为同一天；只传其中一个日期时，后端会自动将另一个日期补成同一天。旧的 `attendanceDate` 字段仍兼容。

Tool API 会在 `hr-service` 中校验短期 `toolToken` 的身份、租户和 scope。生产架构可以让 Dify 经 Gateway 调用 Tool API；本地 Docker 演示为避免代理和 SSRF 配置干扰，可以直接调用 `8091`。

请假操作使用前端确认模式。Dify 参数提取器只提取 `leaveType`、`startTime`、`endTime` 和
`reason`，并通过结束节点返回 `needConfirm=true`：

```json
{
  "needConfirm": true,
  "leaveType": "ANNUAL",
  "startTime": "2026-06-08 14:00:00",
  "endTime": "2026-06-08 18:00:00",
  "reason": "个人事务",
  "message": "您将申请2026-06-08下午年假，是否确认提交？"
}
```

- Dify 不调用提交接口，也没有请假写入 scope。
- 前端展示确认卡片，只有用户点击确认后才调用 `POST /api/employee/me/leave/apply`。
- 后端从登录身份取得员工信息，不信任 Dify 或前端传入的员工姓名。
- `/api/ai/tools/leave/apply` 仅保留草稿校验能力；即使传入 `confirmed=true` 也会拒绝写库。

## 接口示例

### 0. 登录获取 Token

员工账号：

```bash
curl -X POST "http://localhost:8090/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "password": "123456"
  }'
```

HR 账号：

```bash
curl -X POST "http://localhost:8090/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "hr_admin",
    "password": "123456"
  }'
```

返回里的 `accessToken` 后续放到请求头：

```text
Authorization: Bearer <accessToken>
```

### 1. 查询年假余额

HR 管理端接口，需要 HR token：

```bash
curl --get "http://localhost:8090/api/hr/leave/balance" \
  -H "Authorization: Bearer <HR_TOKEN>" \
  --data-urlencode "employeeName=张三"
```

返回：

```json
{
  "employeeName": "张三",
  "annualLeaveBalance": 5,
  "sickLeaveBalance": 3
}
```

### 2. 申请请假

```bash
curl -X POST "http://localhost:8090/api/hr/leave/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <HR_TOKEN>" \
  -d '{
    "employeeName": "张三",
    "leaveType": "ANNUAL",
    "startTime": "2026-05-20 14:00:00",
    "endTime": "2026-05-20 18:00:00",
    "reason": "个人事务"
  }'
```

### 3. 查询候选人

```bash
curl "http://localhost:8090/api/hr/candidates?keyword=Java" \
  -H "Authorization: Bearer <HR_TOKEN>"
```

### 4. 安排面试

```bash
curl -X POST "http://localhost:8090/api/hr/interview/schedule" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <HR_TOKEN>" \
  -d '{
    "candidateName": "李四",
    "interviewerName": "王经理",
    "interviewTime": "2026-05-21 15:00:00",
    "round": "一面",
    "mode": "线上"
  }'
```

### 5. 生成 JD

```bash
curl -X POST "http://localhost:8090/api/hr/jd/generate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <HR_TOKEN>" \
  -d '{
    "positionName": "高级 Java 开发工程师",
    "years": 5,
    "skills": ["Spring Boot", "Spring Cloud", "MySQL", "Redis", "RAG"]
  }'
```

### 6. HR 制度知识库问答预留

```bash
curl -X POST "http://localhost:8090/api/hr/knowledge/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <HR_TOKEN>" \
  -d '{
    "question": "年假制度是什么？"
  }'
```

### 7. AI 对话入口

```bash
curl -X POST "http://localhost:8090/api/ai/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "message": "帮我查询张三的年假余额"
  }'
```

规则：

- 包含“年假”“余额”：调用年假余额查询
- 包含“哪些天”“记录”“休过”“请过”：查询当前员工请假记录
- 包含“请假”：调用请假申请
- 包含“候选人”：查询候选人
- 包含“面试”：安排面试
- 包含“JD”或“招聘需求”：生成 JD
- 包含“制度”“政策”“知识库”：进入知识库问答预留接口

### 8. 请假 AI Workflow

```bash
curl -X POST "http://localhost:8090/api/ai/workflows/leave/apply" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "message": "我想明天下午请年假，原因是个人事务"
  }'
```

Workflow 步骤：

```text
IntentRecognition -> SlotExtraction -> EmployeeValidate -> BalanceCheck -> LeaveApply -> ResponseBuild
```

返回中会包含 `workflowId`、`intent`、`status`、每一步执行轨迹，以及最终的请假申请结果。

同时会写入：

```text
ai_workflow_run       # 一次 Workflow 运行主记录
ai_workflow_step_log  # 每一步执行日志
```

当前工具层已经拆成独立 Tool Bean，便于后续迁移到 Spring AI Tool Calling：

```text
LeaveTools
CandidateTools
InterviewTools
JdTools
KnowledgeTools
```

### 9. 员工端当前用户接口

员工端不再传 `employeeName`，后端从 JWT 中识别当前员工。

```bash
curl "http://localhost:8090/api/employee/me" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>"
```

```bash
curl "http://localhost:8090/api/employee/me/leave/balance" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>"
```

### 10. 员工手册智能问答

```bash
curl -X POST "http://localhost:8090/api/employee/handbook/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "question": "员工年假制度是什么？"
  }'
```

已配置 Dify 时会调用 Dify `/chat-messages`，未配置时返回 mock 提示。

流式输出接口：

```bash
curl -N -X POST "http://localhost:8090/api/employee/handbook/ask/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "question": "员工年假制度是什么？"
  }'
```

### 11. Dify Workflow AI 入口

```bash
curl -X POST "http://localhost:8090/api/ai/dify/workflow/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "message": "帮我查一下我的年假余额",
    "agentType": "WORKFLOW"
  }'
```

`agentType` 可选，默认值为 `WORKFLOW`，原有请求保持兼容。切换到 HR Agent 时仍调用同一个接口：

```bash
curl -X POST "http://localhost:8090/api/ai/dify/workflow/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "message": "帮我查这周考勤和年假余额",
    "agentType": "HR_AGENT"
  }'
```

后端会根据当前登录用户生成有效期 5 分钟的 `toolToken`，并向 Dify Agent 传入
`employeeName`、`employeeId`、`toolToken`、`currentDateTime` 和 `query`。前端不能传入或读取 `toolToken`。

未配置 Dify 时返回：

```json
{
  "answer": "Dify Workflow 未启用，当前返回本地 mock...",
  "source": "mock-dify-workflow",
  "raw": {
    "inputs": {}
  }
}
```

### 12. Dify HTTP 节点 Tool API

查询年假余额：

```bash
curl -X POST "http://localhost:8090/api/ai/tools/leave/balance" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "employeeName": "张三"
  }'
```

查询请假记录：

```bash
curl -X POST "http://localhost:8090/api/ai/tools/leave/records" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -d '{
    "employeeName": "张三",
    "year": 2026,
    "leaveType": "ANNUAL"
  }'
```

HR 查询其他员工：

```bash
curl -X POST "http://localhost:8090/api/ai/tools/leave/balance" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <HR_TOKEN>" \
  -d '{
    "employeeName": "李四"
  }'
```

普通员工越权查询其他员工会返回：

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "员工只能查询或操作自己的请假信息",
  "data": null
}
```

### 13. Agent Memory 多轮请假确认

`/api/ai/chat` 使用 `userId + sessionId` 隔离待确认请假申请。正常运行默认使用 Redis，TTL 默认 20 分钟；测试 profile 使用内存实现。

```bash
docker compose up -d redis
```

第一轮只生成待确认申请，不写入请假表：

```json
{
  "sessionId": "employee-chat-001",
  "message": "帮我请明天下午年假"
}
```

第二轮使用同一个 `sessionId` 确认：

```json
{
  "sessionId": "employee-chat-001",
  "message": "确认"
}
```

支持确认词：`确认`、`确定`、`提交`。支持取消词：`取消`、`算了`、`不提交`。

Spring AI `ChatClient` 仅在应用中存在 `ChatModel` Bean 时启用，并自动注册查询类 `HrAgentTools`。默认无模型配置时继续使用确定性本地路由，不影响启动。`applyLeave` 不暴露给模型，只能由后端在同一会话确认 Redis pending 状态后调用。

启用智谱 GLM Tool Calling：

```bash
export SPRING_AI_CHAT_MODEL=zhipuai
export ZHIPUAI_API_KEY=你的智谱API_KEY
export ZHIPUAI_CHAT_MODEL=glm-4-air
```

智谱调用失败时会自动回退到原有关键词路由。智谱 Embedding 和 Image Model 默认关闭，避免无关模型在没有 API Key 时影响本地启动。

### 14. HR MCP Server

`hr-service` 默认通过 Streamable HTTP 暴露 MCP Server：

```text
http://localhost:8091/mcp
```

MCP Server 仅注册以下 Tool：

- `query_leave_balance`
- `query_attendance`
- `query_leave_policy`
- `create_leave_pending`
- `confirm_leave_apply`
- `cancel_pending`

每个请求都需要员工助手生成的 5 分钟 `toolToken`。请假必须先调用
`create_leave_pending`，再使用相同 `toolToken` 身份与 `sessionId` 调用
`confirm_leave_apply`；MCP Server 不提供直接提交 Tool。待确认数据继续由现有
`AgentMemoryService` 管理，正常环境使用 Redis，TTL 默认 20 分钟。

`POST /api/ai/mcp/chat` 使用配置的 `ChatModel` 自主选择上述 MCP Tool，不再使用
关键词路由；模型不可用或调用失败时分别返回 `MCP_MODEL_UNAVAILABLE` 和
`MCP_MODEL_CALL_FAILED`，不会回退到本地关键词 Agent。模型只看到业务入参，
`toolToken`、`sessionId`、`pendingId`、`version` 和 `idempotencyKey` 均由后端注入。

pending 状态包含 `pendingId`、`version` 和状态字段。确认提交需要同时匹配
`pendingId + version`，并使用 `idempotencyKey` 防止重复提交。每次 MCP Tool
调用都会返回 `traceId`，并写入 `mcp_tool_audit_log`；审计摘要会隐藏 token、
幂等键和用户原始消息等敏感内容。

可通过 `MCP_SERVER_ENABLED=false` 关闭 MCP Server，或通过
`spring.ai.mcp.server.streamable-http.mcp-endpoint` 调整端点。

前端“员工 Agent 助手”面板提供“本地 Agent / MCP Agent”调用模式。选择
“MCP Agent”后，页面继续使用普通登录 JWT 调用：

```text
POST /api/ai/mcp/chat
```

后端会生成短期 `toolToken`，由大模型选择工具后通过 MCP SDK 调用本服务的
`/mcp`，浏览器不会接触 `toolToken` 或 MCP Session。可依次测试“查询我的年假余额”、
“帮我请下周二年假”和“确认”。

## 测试

```bash
mvn test
```

当前测试覆盖 Spring 上下文启动、HR 业务接口、AI 对话入口、多轮请假确认、Redis Memory、请假 Workflow 防绕过、Dify 员工手册问答、Dify Workflow mock 分支和 Tool API 权限控制。

## 后续扩展方向

- 引入 Flyway/Liquibase 替代 `schema.sql`，管理表结构和初始化数据。
- 扩展更多 Spring AI 员工查询工具。
- 将 Dify Workflow 调用从 blocking 模式扩展为 SSE 流式输出。
- 为 HR 制度文档增加切片、Embedding、向量检索和 RAG 回答链路。
- 增加 SSE 流式输出接口，例如 `/api/ai/chat/stream`。
- 增加鉴权、租户隔离、审计日志和操作追踪。
# hr-ai-assistant
