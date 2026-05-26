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

## 测试

```bash
mvn test
```

当前测试覆盖 Spring 上下文启动、HR 业务接口、AI 对话入口和请假 Workflow。

## 后续扩展方向

- 引入 Flyway/Liquibase 替代 `schema.sql`，管理表结构和初始化数据。
- 用 Spring AI `ChatClient` 替换规则式意图识别。
- 在 `LeaveTools`、`CandidateTools` 等工具类上接入 Spring AI Tool Calling 注解。
- 将员工手册问答从 Dify blocking 模式扩展为 SSE 流式输出。
- 为 HR 制度文档增加切片、Embedding、向量检索和 RAG 回答链路。
- 增加 SSE 流式输出接口，例如 `/api/ai/chat/stream`。
- 增加鉴权、租户隔离、审计日志和操作追踪。
# hr-ai-assistant
