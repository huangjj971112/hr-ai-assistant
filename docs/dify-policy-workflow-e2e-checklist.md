# Dify 制度查询 Workflow 与 Multi-Agent 端到端验证清单

本文用于验证 HR Agent 的制度查询、Dify Workflow、Multi-Agent 薪酬判断和右侧观测面板是否正常。

## 1. Dify Workflow 输出契约

制度查询 Workflow 推荐在结束节点输出以下字段：

```json
{
  "answer": "制度摘要，必须能直接作为 PolicyAgent 的制度依据。",
  "source": "员工手册-请假考勤薪酬制度"
}
```

后端目前仍兼容旧字段：

```json
{
  "output": "制度摘要"
}
```

但旧字段没有明确来源，观测面板会显示默认来源 `dify-workflow-handbook`。因此推荐统一改成 `answer` 和 `source`。

## 2. hr-service 启动配置

连接本地 Dify Workflow 时，启动 `hr-service` 前配置：

```bash
export DIFY_ENABLED=true
export DIFY_BASE_URL=http://localhost/v1
export DIFY_API_KEY=app-你的制度查询WorkflowKey
export DIFY_WORKFLOW_PATH=/workflows/run
```

如果同时保留 Dify Agent/Chat 应用，可额外配置：

```bash
export DIFY_AGENT_API_KEY=app-你的聊天助手Key
export DIFY_AGENT_PATH=/chat-messages
```

当前优先级为：

```text
DIFY_API_KEY Workflow -> Chat App 兼容回退 -> DIFY_AGENT_API_KEY Agent Streaming -> 本地 mock
```

## 3. 知识库内容检查

知识库中必须能检索到以下关键制度词：

- 年假：`带薪`、`不扣工资`、`不影响工资`
- 病假：`病假工资`、`按比例`、`扣款`、`影响工资`
- 迟到：`迟到`、`扣款`、`影响绩效`、`影响工资`

如果 Dify 调试窗口回答“暂未找到相关信息”，优先检查知识库文档是否真的包含这些词，以及索引状态是否为可用。

## 4. 端到端验证用例

### 用例 A：年假薪酬影响

用户问题：

```text
我下周想请三天年假，会不会影响工资？
```

预期观测：

- Coordinator 调度：`PolicyAgent`、`LeaveAgent`、`SalaryAgent`
- Tool：`query_leave_policy`、`query_leave_balance`
- 制度来源：`员工手册-请假考勤薪酬制度` 或 `dify-workflow-handbook`
- 最终判断：`NO_IMPACT`
- 回复要点：年假属于带薪假期，审批通过后通常不影响工资

### 用例 B：病假薪酬影响

用户问题：

```text
我想请病假，会不会扣工资？
```

预期观测：

- Coordinator 调度：`PolicyAgent`、`SalaryAgent`
- Tool：`query_leave_policy`
- 制度来源：`员工手册-请假考勤薪酬制度` 或 `dify-workflow-handbook`
- 最终判断：`POSSIBLE_IMPACT`
- 回复要点：病假可能按病假工资规则影响工资，具体需结合病假天数、证明材料和 HR 审核确认
- 不应出现：具体扣款金额，例如“扣 100 元”

### 用例 C：迟到薪酬影响

用户问题：

```text
我这周迟到了两次，会影响工资吗？
```

预期观测：

- Coordinator 调度：`PolicyAgent`、`AttendanceAgent`、`SalaryAgent`
- Tool：`query_leave_policy`、`query_attendance`
- 考勤摘要：`lateCount` 应大于等于 1，具体取决于演示数据
- 最终判断：`POSSIBLE_IMPACT`
- 回复要点：迟到可能影响工资或绩效，具体以迟到次数、迟到时长、是否有合理说明和 HR 核算为准

## 5. 常见异常定位

- `source = mock-employee-handbook`：没有连上 Dify，检查 `DIFY_ENABLED` 和 API Key。
- `source = dify-workflow-handbook` 且回答为空泛：Workflow 通了，但输出没有显式 `source`，或知识库没有命中。
- `MCP_TOOL_CALL_FAILED`：先看右侧观测 `traceId`，再查 `hr-service` 和 Dify 容器日志。
- Tool 耗时异常长：通常是 Dify 模型、检索、Embedding 或网络调用慢。
- Dify 页面能答，HR 不能答：检查 Workflow Start 变量名是否为 `query`，以及 API 请求是否通过 `inputs.query` 传入。
