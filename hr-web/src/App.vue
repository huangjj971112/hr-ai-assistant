<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

type UserRole = 'EMPLOYEE' | 'HR';

type LoginResponse = {
  tokenType: string;
  accessToken: string;
  username: string;
  employeeName: string;
  role: UserRole;
};

type UserAccountSummary = {
  id: number;
  username: string;
  employeeName: string;
  role: UserRole;
  enabled: boolean;
  employeeProfileStatus?: string;
  message?: string;
};

type CreateUserForm = {
  username: string;
  password: string;
  employeeName: string;
  role: UserRole;
  enabled: boolean;
};

type LeaveBalance = {
  employeeName: string;
  annualLeaveBalance: number;
  sickLeaveBalance: number;
};

type LeaveApplication = {
  applyNo: string;
  employeeName: string;
  leaveType: 'ANNUAL' | 'SICK' | 'PERSONAL';
  startTime: string;
  endTime: string;
  reason: string;
  status: string;
  createdAt: string;
};

type Candidate = {
  name: string;
  position: string;
  years: number;
  skills: string[];
  status: string;
  phone: string;
  email: string;
};

type WorkflowStep = {
  stepName: string;
  status: string;
  message: string;
  executedAt: string;
};

type WorkflowResponse = {
  workflowId: string;
  intent: string;
  status: string;
  steps: WorkflowStep[];
  result: unknown;
};

type KnowledgeAnswer = {
  answer: string;
  source: string;
  conversationId?: string | null;
};

type LeaveType = 'ANNUAL' | 'SICK' | 'PERSONAL';
type AiAssistantType = 'WORKFLOW' | 'HR_AGENT';
type EmployeeAgentMode = 'LOCAL' | 'MCP';

type DifyWorkflowAnswer = {
  answer: string;
  source: string;
  needConfirm?: boolean | null;
  leaveType?: LeaveType | null;
  startTime?: string | null;
  endTime?: string | null;
  reason?: string | null;
  message?: string | null;
};

type LeaveConfirmation = {
  leaveType: LeaveType;
  startTime: string;
  endTime: string;
  reason: string;
  message: string;
};

type LeaveApplyResponse = {
  applyNo: string;
  status: string;
  message: string;
};

type AssistantMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  source?: string;
  observation?: AgentObservationSnapshot | null;
};

type AgentChatResponse = {
  intent: string;
  reply: string;
  data: unknown;
  observation?: AgentObservationSnapshot | null;
};

type AgentObservationStatus = 'SUCCESS' | 'FAILED' | 'PARTIAL';

type AgentToolObservation = {
  toolName: string;
  status: AgentObservationStatus;
  durationMs: number;
  traceId?: string | null;
  inputSummary: Record<string, string | number | boolean>;
  resultSummary: Record<string, string | number | boolean>;
  evidenceSource?: string | null;
  errorCode?: string | null;
};

type AgentObservationStep = {
  agentName: string;
  status: AgentObservationStatus;
  durationMs: number;
  summary?: string | null;
  toolCalls: AgentToolObservation[];
};

type AgentDecisionObservation = {
  outcome: string;
  basis: string;
  needsHumanConfirmation: boolean;
};

type AgentPlannerObservation = {
  plannerType: string;
  traceId?: string | null;
  fallbackReason?: string | null;
  summary?: string | null;
};

type AgentReflectionObservation = {
  traceId?: string | null;
  ruleAction?: string | null;
  action: string;
  reason?: string | null;
  needRetry: boolean;
  needReplan: boolean;
  llmRawOutput?: string | null;
};

type AgentObservationSnapshot = {
  requestId: string;
  status: AgentObservationStatus;
  totalDurationMs: number;
  summarySteps: string[];
  steps: AgentObservationStep[];
  planner?: AgentPlannerObservation | null;
  reflection?: AgentReflectionObservation | null;
  decision?: AgentDecisionObservation | null;
};

type PendingLeaveApply = {
  state: string;
  employeeName: string;
  leaveType: LeaveType;
  startTime: string;
  endTime: string;
  reason: string;
  confirmed: boolean;
  createdAt: string;
  expiresAt: string;
};

const apiBase = '/api';
const loading = ref(false);
const error = ref('');
const chatError = ref('');
const workflowError = ref('');
const handbookError = ref('');
const difyWorkflowError = ref('');
const handbookStreaming = ref(false);
const difyWorkflowSending = ref(false);
const leaveConfirmSubmitting = ref(false);
const leaveConfirmError = ref('');
const aiChatSending = ref(false);
const session = ref<LoginResponse | null>(loadSession());

const username = ref('zhangsan');
const password = ref('123456');
const hrEmployeeName = ref('张三');
const candidateKeyword = ref('Java');
const chatMessage = ref('帮我查询张三的年假余额');
// 员工侧默认走 MCP Agent，调试时再切回本地 Agent，避免主流程和旧演示入口混在一起。
const employeeAgentMode = ref<EmployeeAgentMode>('MCP');
// 高级能力默认折叠，只在排查 Dify、旧 Workflow 或原始数据时展开。
const showAdvancedTools = ref(false);
const aiChatSessionId = crypto.randomUUID();
const difyWorkflowMessage = ref('帮我查一下我的年假余额');
const aiAssistantType = ref<AiAssistantType>('WORKFLOW');
const workflowMessage = ref('我想明天下午请年假，原因是个人事务');
const jdPosition = ref('高级 Java 开发工程师');
const jdYears = ref(5);
const jdSkills = ref('Spring Boot, Spring Cloud, MySQL, Redis, RAG');
const leaveHistoryYear = ref(new Date().getFullYear());
const leaveHistoryType = ref('ANNUAL');
const handbookQuestion = ref('员工年假制度是什么？');

const me = ref<unknown>(null);
const myBalance = ref<LeaveBalance | null>(null);
const myLeaveApplications = ref<LeaveApplication[]>([]);
const hrBalance = ref<LeaveBalance | null>(null);
const candidates = ref<Candidate[]>([]);
const users = ref<UserAccountSummary[]>([]);
const createUserForm = ref<CreateUserForm>({
  username: '',
  password: '123456',
  employeeName: '',
  role: 'EMPLOYEE',
  enabled: true
});
const userManagementMessage = ref('');
const chatResult = ref<unknown>(null);
const aiChatPendingLeave = ref<LeaveConfirmation | null>(null);
const selectedObservationMessageId = ref<number | null>(null);
const debugReportCopied = ref(false);
const aiChatMessages = ref<AssistantMessage[]>([
  {
    id: Date.now(),
    role: 'assistant',
    content: '你好，我可以帮你查询假期余额、考勤，并通过多轮确认申请请假。'
  }
]);
const difyWorkflowResult = ref<DifyWorkflowAnswer | null>(null);
const pendingLeaveConfirmation = ref<LeaveConfirmation | null>(null);
const difyWorkflowMessages = ref<AssistantMessage[]>([
  {
    id: Date.now(),
    role: 'assistant',
    content: '你好，我可以帮你查询假期余额、请假记录和考勤记录。'
  }
]);
const workflowResult = ref<WorkflowResponse | null>(null);
const jdResult = ref('');
const handbookAnswer = ref<KnowledgeAnswer | null>(null);

const isHr = computed(() => session.value?.role === 'HR');
const isEmployee = computed(() => session.value?.role === 'EMPLOYEE');
const chatLeaveApplications = computed(() => {
  if (!isAiChatResponse(chatResult.value) || chatResult.value.intent !== 'LEAVE_HISTORY') {
    return [];
  }
  return Array.isArray(chatResult.value.data) ? chatResult.value.data as LeaveApplication[] : [];
});
const selectedObservationMessage = computed(() => {
  if (selectedObservationMessageId.value) {
    const selected = aiChatMessages.value.find((message) => message.id === selectedObservationMessageId.value);
    if (selected?.observation) {
      return selected;
    }
  }
  return [...aiChatMessages.value].reverse().find((message) => message.observation) ?? null;
});
const selectedObservation = computed(() => selectedObservationMessage.value?.observation ?? null);

onMounted(async () => {
  if (!session.value) {
    return;
  }
  await run(async () => {
    await fetchMe();
    await fetchMyBalance();
    await fetchMyLeaveApplications();
    if (isHr.value) {
      await fetchUsers();
    }
  });
});

async function loginAs(nextUsername: string) {
  username.value = nextUsername;
  password.value = '123456';
  await login();
}

async function login() {
  await run(async () => {
    const data = await request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: { username: username.value, password: password.value },
      auth: false
    });
    session.value = data;
    localStorage.setItem('hr-web-session', JSON.stringify(data));
    clearResults();
    if (data.role === 'EMPLOYEE') {
      await fetchMe();
      await fetchMyBalance();
      await fetchMyLeaveApplications();
    }
    if (data.role === 'HR') {
      await fetchUsers();
    }
  });
}

function logout() {
  session.value = null;
  localStorage.removeItem('hr-web-session');
  clearResults();
}

function resetSession() {
  session.value = null;
  localStorage.removeItem('hr-web-session');
  clearResults();
}

async function fetchMe() {
  me.value = await request('/employee/me');
}

async function fetchMyBalance() {
  myBalance.value = await request<LeaveBalance>('/employee/me/leave/balance');
}

async function fetchMyLeaveApplications() {
  await run(async () => {
    const params = new URLSearchParams();
    if (leaveHistoryYear.value) {
      params.set('year', String(leaveHistoryYear.value));
    }
    if (leaveHistoryType.value) {
      params.set('leaveType', leaveHistoryType.value);
    }
    myLeaveApplications.value = await request<LeaveApplication[]>(
      `/employee/me/leave/applications?${params.toString()}`
    );
  });
}

async function submitMyLeaveApplication(confirmation: LeaveConfirmation) {
  return request<LeaveApplyResponse>('/employee/me/leave/apply', {
    method: 'POST',
    body: {
      leaveType: confirmation.leaveType,
      startTime: confirmation.startTime,
      endTime: confirmation.endTime,
      reason: confirmation.reason
    }
  });
}

async function fetchHrBalance() {
  await run(async () => {
    hrBalance.value = await request<LeaveBalance>(
      `/hr/leave/balance?employeeName=${encodeURIComponent(hrEmployeeName.value)}`
    );
  });
}

async function searchCandidates() {
  await run(async () => {
    candidates.value = await request<Candidate[]>(
      `/hr/candidates?keyword=${encodeURIComponent(candidateKeyword.value)}`
    );
  });
}

async function fetchUsers() {
  await run(async () => {
    users.value = await request<UserAccountSummary[]>('/hr/users');
  });
}

async function createUser() {
  userManagementMessage.value = '';
  await run(async () => {
    // 用户创建走后端统一加密和角色校验，前端只收集学习演示需要的基础字段。
    const created = await request<UserAccountSummary>('/hr/users', {
      method: 'POST',
      body: createUserForm.value
    });
    userManagementMessage.value = `已创建账号 ${created.username}，可使用初始密码登录。${created.message ?? ''}`;
    createUserForm.value = {
      username: '',
      password: '123456',
      employeeName: '',
      role: 'EMPLOYEE',
      enabled: true
    };
    await fetchUsers();
  });
}

async function generateJd() {
  await run(async () => {
    const data = await request<{ jd: string }>('/hr/jd/generate', {
      method: 'POST',
      body: {
        positionName: jdPosition.value,
        years: jdYears.value,
        skills: jdSkills.value.split(',').map((skill) => skill.trim()).filter(Boolean)
      }
    });
    jdResult.value = data.jd;
  });
}

async function chat(messageOverride?: string) {
  const message = (messageOverride ?? chatMessage.value).trim();
  if (!message || aiChatSending.value) {
    return;
  }

  chatError.value = '';
  aiChatSending.value = true;
  aiChatMessages.value.push({
    id: Date.now(),
    role: 'user',
    content: message
  });
  chatMessage.value = '';

  await run(async () => {
    const chatPath = employeeAgentMode.value === 'MCP' ? '/ai/mcp/chat' : '/ai/chat';
    const response = await request<AgentChatResponse>(chatPath, {
      method: 'POST',
      body: { message, sessionId: aiChatSessionId }
    });
    chatResult.value = response;
    const assistantMessageId = Date.now() + 1;
    aiChatMessages.value.push({
      id: assistantMessageId,
      role: 'assistant',
      content: response.reply,
      observation: response.observation ?? null
    });
    if (response.observation) {
      selectedObservationMessageId.value = assistantMessageId;
    }

    const confirmation = toAgentLeaveConfirmation(response);
    if (confirmation) {
      aiChatPendingLeave.value = confirmation;
    }
    if (['LEAVE_APPLY_SUBMITTED', 'LEAVE_APPLY_CANCELLED', 'NO_PENDING_LEAVE_APPLY'].includes(response.intent)) {
      aiChatPendingLeave.value = null;
    }
    if (response.intent === 'LEAVE_APPLY_SUBMITTED') {
      await fetchMyLeaveApplications();
    }
  }, (message) => {
    chatError.value = message;
    aiChatMessages.value.push({
      id: Date.now() + 2,
      role: 'assistant',
      content: `Agent 调用失败：${message}`
    });
  }, false);
  aiChatSending.value = false;
}

function handleChatKeydown(event: KeyboardEvent) {
  // 中文输入法组词期间按 Enter 是确认候选词，不应该触发发送。
  if (event.isComposing) {
    return;
  }
  // 聊天框采用常见交互：Enter 发送，Shift + Enter 保留换行。
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    chat();
  }
}

function useAiChatPrompt(message: string) {
  chatMessage.value = message;
}

function selectObservation(message: AssistantMessage) {
  if (message.observation) {
    selectedObservationMessageId.value = message.id;
  }
}

function toAgentLeaveConfirmation(response: AgentChatResponse): LeaveConfirmation | null {
  if (response.intent !== 'LEAVE_APPLY_PENDING' || !isPendingLeaveApply(response.data)) {
    return null;
  }
  return {
    leaveType: response.data.leaveType,
    startTime: response.data.startTime,
    endTime: response.data.endTime,
    reason: response.data.reason,
    message: response.reply
  };
}

async function runDifyWorkflowChat() {
  const message = difyWorkflowMessage.value.trim();
  if (!message || difyWorkflowSending.value) {
    return;
  }

  difyWorkflowError.value = '';
  pendingLeaveConfirmation.value = null;
  leaveConfirmError.value = '';
  difyWorkflowSending.value = true;
  difyWorkflowMessages.value.push({
    id: Date.now(),
    role: 'user',
    content: message
  });
  difyWorkflowMessage.value = '';

  await run(async () => {
    const response = await request<DifyWorkflowAnswer>('/ai/dify/workflow/chat', {
      method: 'POST',
      body: {
        message,
        agentType: aiAssistantType.value
      }
    });
    difyWorkflowResult.value = response;
    const confirmation = toLeaveConfirmation(response);
    if (confirmation) {
      pendingLeaveConfirmation.value = confirmation;
      leaveConfirmError.value = '';
    }
    difyWorkflowMessages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: confirmation?.message ?? response.answer,
      source: response.source
    });
  }, (message) => {
    difyWorkflowError.value = message;
    difyWorkflowMessages.value.push({
      id: Date.now() + 2,
      role: 'assistant',
      content: `${aiAssistantType.value === 'HR_AGENT' ? 'HR Agent' : '流程'}调用失败：${message}`
    });
  }, false);
  difyWorkflowSending.value = false;
}

function useDifyWorkflowPrompt(message: string) {
  difyWorkflowMessage.value = message;
}

function toLeaveConfirmation(response: DifyWorkflowAnswer): LeaveConfirmation | null {
  if (!response.needConfirm
      || !isLeaveType(response.leaveType)
      || !response.startTime
      || !response.endTime) {
    return null;
  }
  return {
    leaveType: response.leaveType,
    startTime: response.startTime,
    endTime: response.endTime,
    reason: response.reason?.trim() || '个人事务',
    message: response.message || response.answer || '请确认是否提交该请假申请。'
  };
}

function isLeaveType(value: unknown): value is LeaveType {
  return value === 'ANNUAL' || value === 'SICK' || value === 'PERSONAL';
}

async function confirmLeaveApplication() {
  const confirmation = pendingLeaveConfirmation.value;
  if (!confirmation || leaveConfirmSubmitting.value) {
    return;
  }

  leaveConfirmSubmitting.value = true;
  leaveConfirmError.value = '';
  try {
    const result = await submitMyLeaveApplication(confirmation);
    pendingLeaveConfirmation.value = null;
    difyWorkflowMessages.value.push({
      id: Date.now(),
      role: 'assistant',
      content: `${result.message}，申请编号：${result.applyNo}，状态：${result.status}。`
    });
  } catch (cause) {
    leaveConfirmError.value = cause instanceof Error ? cause.message : '请假申请提交失败';
  } finally {
    leaveConfirmSubmitting.value = false;
  }
}

function cancelLeaveApplication() {
  pendingLeaveConfirmation.value = null;
  leaveConfirmError.value = '';
  difyWorkflowMessages.value.push({
    id: Date.now(),
    role: 'assistant',
    content: '已取消请假申请。'
  });
}

async function runWorkflow() {
  workflowError.value = '';
  await run(async () => {
    workflowResult.value = await request<WorkflowResponse>('/ai/workflows/leave/apply', {
      method: 'POST',
      body: { message: workflowMessage.value }
    });
    await fetchMyBalance();
  }, (message) => {
    workflowError.value = message;
  }, false);
}

async function askHandbook() {
  handbookError.value = '';
  await run(async () => {
    handbookAnswer.value = await request<KnowledgeAnswer>('/employee/handbook/ask', {
      method: 'POST',
      body: { question: handbookQuestion.value }
    });
  }, (message) => {
    handbookError.value = message;
  }, false);
}

async function askHandbookStream() {
  handbookError.value = '';
  handbookAnswer.value = {
    answer: '',
    source: 'streaming'
  };
  loading.value = true;
  handbookStreaming.value = true;

  try {
    const response = await fetch(`${apiBase}/employee/handbook/ask/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(session.value?.accessToken ? { Authorization: `Bearer ${session.value.accessToken}` } : {})
      },
      body: JSON.stringify({ question: handbookQuestion.value })
    });

    if (!response.ok || !response.body) {
      const data = await response.text();
      throw new Error(data || `请求失败，HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() ?? '';
      events.forEach(handleHandbookStreamEvent);
    }
    if (buffer.trim()) {
      handleHandbookStreamEvent(buffer);
    }
  } catch (cause) {
    handbookError.value = cause instanceof Error ? cause.message : '流式问答失败';
  } finally {
    loading.value = false;
    handbookStreaming.value = false;
  }
}

function handleHandbookStreamEvent(rawEvent: string) {
  const lines = rawEvent.split(/\r?\n/);
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message';
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');

  if (!handbookAnswer.value || !data) {
    return;
  }
  if (eventName === 'answer') {
    handbookAnswer.value.answer += data;
    return;
  }
  if (eventName === 'meta') {
    const meta = JSON.parse(data) as Partial<KnowledgeAnswer>;
    handbookAnswer.value.source = meta.source ?? handbookAnswer.value.source;
    handbookAnswer.value.conversationId = meta.conversationId ?? handbookAnswer.value.conversationId;
    return;
  }
  if (eventName === 'error') {
    handbookError.value = data;
  }
}

async function run(task: () => Promise<void>, onError?: (message: string) => void, showGlobalError = true) {
  error.value = '';
  loading.value = true;
  try {
    await task();
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '请求失败';
    if (showGlobalError) {
      error.value = message;
    }
    onError?.(message);
  } finally {
    loading.value = false;
  }
}

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; auth?: boolean } = {}
): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.auth !== false && session.value?.accessToken) {
    headers.Authorization = `Bearer ${session.value.accessToken}`;
  }

  const response = await fetch(`${apiBase}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  const contentType = response.headers.get('content-type') ?? '';
  const data = contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) {
    const message = parseErrorMessage(data, response.status);
    if (response.status === 401) {
      resetSession();
    }
    throw new Error(message);
  }
  return data as T;
}

function parseErrorMessage(data: unknown, status: number) {
  if (typeof data === 'object' && data !== null) {
    const record = data as Record<string, unknown>;
    if (typeof record.message === 'string' && record.message.trim()) {
      return record.message;
    }
    if (typeof record.error === 'string' && record.error.trim()) {
      return record.error;
    }
  }
  if (typeof data === 'string' && data.trim()) {
    return data;
  }
  return `请求失败，HTTP ${status}`;
}

function loadSession(): LoginResponse | null {
  const raw = localStorage.getItem('hr-web-session');
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as LoginResponse;
  } catch {
    return null;
  }
}

function clearResults() {
  me.value = null;
  myBalance.value = null;
  myLeaveApplications.value = [];
  hrBalance.value = null;
  candidates.value = [];
  users.value = [];
  userManagementMessage.value = '';
  chatResult.value = null;
  chatError.value = '';
  aiChatSending.value = false;
  aiChatPendingLeave.value = null;
  selectedObservationMessageId.value = null;
  aiChatMessages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '你好，我可以帮你查询假期余额、考勤，并通过多轮确认申请请假。'
    }
  ];
  difyWorkflowResult.value = null;
  difyWorkflowError.value = '';
  difyWorkflowSending.value = false;
  pendingLeaveConfirmation.value = null;
  leaveConfirmSubmitting.value = false;
  leaveConfirmError.value = '';
  difyWorkflowMessages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '你好，我可以帮你查询假期余额、请假记录和考勤记录。'
    }
  ];
  workflowResult.value = null;
  workflowError.value = '';
  jdResult.value = '';
  handbookAnswer.value = null;
  handbookError.value = '';
  handbookStreaming.value = false;
}

function pretty(value: unknown) {
  return JSON.stringify(value, null, 2);
}

function statusText(status: AgentObservationStatus) {
  const names: Record<AgentObservationStatus, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    PARTIAL: '部分完成'
  };
  return names[status] ?? status;
}

function plannerTypeText(type?: string | null) {
  const names: Record<string, string> = {
    LLM: 'LLM Planner',
    RULE: 'Rule Planner'
  };
  return type ? names[type] ?? type : '未知 Planner';
}

function reflectionActionText(action?: string | null) {
  const names: Record<string, string> = {
    PASS: '通过',
    RETRY: '建议重试',
    REPLAN: '建议重新规划',
    ASK_USER: '追问用户',
    FAIL: '失败'
  };
  return action ? names[action] ?? action : '未知动作';
}

function summaryEntries(summary: Record<string, string | number | boolean>) {
  return Object.entries(summary ?? {});
}

function formatSummary(summary: Record<string, string | number | boolean>) {
  const entries = summaryEntries(summary);
  if (!entries.length) {
    return '-';
  }
  return entries.map(([key, value]) => `${key}=${value}`).join(', ');
}

function selectedUserMessage(message: AssistantMessage | null) {
  if (!message) {
    return null;
  }
  const index = aiChatMessages.value.findIndex((item) => item.id === message.id);
  for (let i = index - 1; i >= 0; i -= 1) {
    if (aiChatMessages.value[i].role === 'user') {
      return aiChatMessages.value[i];
    }
  }
  return null;
}

function buildDebugReport(message: AssistantMessage) {
  const observation = message.observation;
  const userMessage = selectedUserMessage(message);
  if (!observation) {
    return '';
  }

  const lines = [
    '# Agent 调试报告',
    '',
    `用户问题：${userMessage?.content ?? '-'}`,
    `最终回答：${message.content}`,
    '',
    `requestId：${observation.requestId}`,
    `整体状态：${statusText(observation.status)} (${observation.status})`,
    `总耗时：${observation.totalDurationMs} ms`
  ];

  if (observation.planner) {
    lines.push(
      '',
      '## Planner',
      `类型：${plannerTypeText(observation.planner.plannerType)} (${observation.planner.plannerType})`,
      `traceId：${observation.planner.traceId || '-'}`,
      `计划摘要：${observation.planner.summary || '-'}`,
      `fallback：${observation.planner.fallbackReason || '无'}`
    );
  }

  if (observation.reflection) {
    lines.push(
      '',
      '## Reflection',
      `最终动作：${reflectionActionText(observation.reflection.action)} (${observation.reflection.action})`,
      `规则动作：${reflectionActionText(observation.reflection.ruleAction)} (${observation.reflection.ruleAction || '-'})`,
      `traceId：${observation.reflection.traceId || '-'}`,
      `原因：${observation.reflection.reason || '-'}`,
      `needRetry：${observation.reflection.needRetry}`,
      `needReplan：${observation.reflection.needReplan}`
    );
  }

  lines.push('', '## Agent / Tool 调用链');
  for (const step of observation.steps) {
    lines.push(
      `- Agent：${step.agentName}`,
      `  状态：${statusText(step.status)} (${step.status})`,
      `  耗时：${step.durationMs} ms`,
      `  摘要：${step.summary || '-'}`
    );
    for (const tool of step.toolCalls) {
      lines.push(
        `  - Tool：${tool.toolName}`,
        `    状态：${statusText(tool.status)} (${tool.status})`,
        `    耗时：${tool.durationMs} ms`,
        `    traceId：${tool.traceId || '-'}`,
        `    错误码：${tool.errorCode || '-'}`,
        `    证据：${tool.evidenceSource || '-'}`,
        `    输入：${formatSummary(tool.inputSummary)}`,
        `    输出：${formatSummary(tool.resultSummary)}`
      );
    }
  }

  if (observation.decision) {
    lines.push(
      '',
      '## 最终判断',
      `结果：${observation.decision.outcome}`,
      `依据：${observation.decision.basis}`,
      `建议人工确认：${observation.decision.needsHumanConfirmation}`
    );
  }

  return lines.join('\n');
}

async function copyDebugReport() {
  if (!selectedObservationMessage.value?.observation) {
    return;
  }
  const report = buildDebugReport(selectedObservationMessage.value);
  await navigator.clipboard.writeText(report);
  debugReportCopied.value = true;
  window.setTimeout(() => {
    debugReportCopied.value = false;
  }, 1800);
}

function observationTitle(message: AssistantMessage | null) {
  if (!message) {
    return '等待一次 Agent 回复';
  }
  return `已选择第 ${aiChatMessages.value.findIndex((item) => item.id === message.id) + 1} 条助手回复`;
}

function leaveTypeText(type: string) {
  const names: Record<string, string> = {
    ANNUAL: '年假',
    SICK: '病假',
    PERSONAL: '事假'
  };
  return names[type] ?? type;
}

function leaveStatusText(status: string) {
  const names: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝'
  };
  return names[status] ?? status;
}

function dateTimeText(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function isAiChatResponse(value: unknown): value is AgentChatResponse {
  return typeof value === 'object' && value !== null && 'intent' in value && 'reply' in value;
}

function isPendingLeaveApply(value: unknown): value is PendingLeaveApply {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return record.state === 'pending_leave_apply'
    && isLeaveType(record.leaveType)
    && typeof record.startTime === 'string'
    && typeof record.endTime === 'string'
    && typeof record.reason === 'string';
}
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">HR</div>
        <div>
          <h1>HR AI Assistant</h1>
          <p>员工自助与智能助手</p>
        </div>
      </div>

      <nav v-if="session" class="nav-list">
        <a href="#employee" :class="{ disabled: !isEmployee && !isHr }">首页</a>
        <a href="#ai">员工助手</a>
        <a href="#leave-history" :class="{ disabled: !isEmployee && !isHr }">请假记录</a>
        <a v-if="isHr" href="#hr">HR 管理</a>
        <a href="#advanced">高级/调试</a>
      </nav>
    </aside>

    <section class="content">
      <div class="topbar">
        <div>
          <h2>{{ session ? '工作台' : '登录' }}</h2>
          <p>{{ session ? '先处理员工最常用的查询、请假和确认流程。' : '使用内置演示账号体验员工端和 HR 端权限差异。' }}</p>
        </div>
        <div class="topbar-actions">
          <div class="status-pill" :class="{ online: session }">
            {{ session ? 'Authenticated' : 'Anonymous' }}
          </div>
          <section v-if="session" class="user-menu" aria-label="当前登录用户">
            <div class="avatar">{{ session.employeeName.slice(0, 1) }}</div>
            <div class="user-meta">
              <strong>{{ session.employeeName }}</strong>
              <span>{{ session.username }} · {{ session.role }}</span>
            </div>
            <button class="secondary compact-button" @click="logout">退出</button>
          </section>
        </div>
      </div>

      <section v-if="!session" class="panel login-panel">
        <div>
          <h3>登录到 HR AI Assistant</h3>
          <p>使用内置演示账号体验员工端和 HR 端权限差异。</p>
        </div>
        <div class="form-grid compact">
          <label>
            账号
            <input v-model="username" />
          </label>
          <label>
            密码
            <input v-model="password" type="password" />
          </label>
        </div>
        <div class="actions">
          <button @click="login">登录</button>
          <button class="secondary" @click="loginAs('zhangsan')">员工账号</button>
          <button class="secondary" @click="loginAs('hr_admin')">HR 账号</button>
        </div>
      </section>

      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading" class="loading-bar"></div>

      <template v-if="session">
        <section id="employee" class="dashboard-grid">
          <article class="panel employee-summary">
            <div class="panel-header">
              <div>
                <h3>我的工作台</h3>
                <p>把常用入口放在这里，日常只需要看这一组信息。</p>
              </div>
            </div>
            <div class="profile-card">
              <div class="avatar large">{{ session.employeeName.slice(0, 1) }}</div>
              <div>
                <strong>{{ session.employeeName }}</strong>
                <span>{{ session.username }} · {{ session.role === 'HR' ? 'HR 管理员' : '员工' }}</span>
              </div>
            </div>
            <div class="hero-actions">
              <a href="#ai" class="link-button">打开员工助手</a>
              <a href="#leave-history" class="link-button secondary-link">查看请假记录</a>
            </div>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <h3>我的假期余额</h3>
                <p>员工端自动使用当前登录身份查询。</p>
              </div>
              <button class="secondary" @click="fetchMyBalance">查询</button>
            </div>
            <div v-if="myBalance" class="metric-row">
              <div class="metric">
                <span>年假</span>
                <strong>{{ myBalance.annualLeaveBalance }}</strong>
                <small>天</small>
              </div>
              <div class="metric">
                <span>病假</span>
                <strong>{{ myBalance.sickLeaveBalance }}</strong>
                <small>天</small>
              </div>
            </div>
            <p v-else class="empty">点击查询我的余额。</p>
          </article>
        </section>

        <section id="leave-history" class="panel">
          <div class="panel-header">
            <div>
              <h3>我的请假记录</h3>
              <p>只返回当前登录员工的数据。</p>
            </div>
            <button class="secondary" @click="fetchMyLeaveApplications">查询</button>
          </div>
          <div class="form-grid compact">
            <label>
              年份
              <input v-model.number="leaveHistoryYear" type="number" min="2000" max="2100" />
            </label>
            <label>
              假期类型
              <select v-model="leaveHistoryType">
                <option value="">全部</option>
                <option value="ANNUAL">年假</option>
                <option value="SICK">病假</option>
                <option value="PERSONAL">事假</option>
              </select>
            </label>
          </div>
          <div v-if="myLeaveApplications.length" class="leave-table">
            <div class="leave-row leave-head">
              <span>申请单</span>
              <span>类型</span>
              <span>时间</span>
              <span>状态</span>
              <span>原因</span>
            </div>
            <div v-for="item in myLeaveApplications" :key="item.applyNo" class="leave-row">
              <strong>{{ item.applyNo }}</strong>
              <span>{{ leaveTypeText(item.leaveType) }}</span>
              <span>{{ dateTimeText(item.startTime) }} 至 {{ dateTimeText(item.endTime) }}</span>
              <span class="status-text">{{ leaveStatusText(item.status) }}</span>
              <span>{{ item.reason }}</span>
            </div>
          </div>
          <p v-else class="empty">当前筛选条件下暂无请假记录。</p>
        </section>

        <section v-if="isHr" id="hr" class="dashboard-grid">
          <article class="panel wide">
            <div class="panel-header">
              <div>
                <h3>用户管理</h3>
                <p>先创建账号，再选择角色；创建后可直接登录。</p>
              </div>
              <button class="secondary" @click="fetchUsers">刷新</button>
            </div>
            <div class="form-grid">
              <label>
                登录账号
                <input v-model="createUserForm.username" placeholder="例如 lisi" />
              </label>
              <label>
                初始密码
                <input v-model="createUserForm.password" type="password" />
              </label>
              <label>
                员工姓名
                <input v-model="createUserForm.employeeName" placeholder="例如 李四" />
              </label>
              <label>
                角色
                <select v-model="createUserForm.role">
                  <option value="EMPLOYEE">普通员工</option>
                  <option value="HR">HR 管理员</option>
                </select>
              </label>
              <label class="checkbox-label">
                <input v-model="createUserForm.enabled" type="checkbox" />
                启用账号
              </label>
            </div>
            <div class="actions">
              <button @click="createUser">创建用户</button>
              <span v-if="userManagementMessage" class="success-text">{{ userManagementMessage }}</span>
            </div>
            <div v-if="users.length" class="leave-table">
              <div class="leave-row user-row leave-head">
                <span>账号</span>
                <span>员工姓名</span>
                <span>角色</span>
                <span>状态</span>
              </div>
              <div v-for="user in users" :key="user.id" class="leave-row user-row">
                <strong>{{ user.username }}</strong>
                <span>{{ user.employeeName }}</span>
                <span>{{ user.role === 'HR' ? 'HR 管理员' : '普通员工' }}</span>
                <span class="status-text">{{ user.enabled ? '启用' : '停用' }}</span>
              </div>
            </div>
            <p v-else class="empty">暂无用户数据，点击刷新或创建新用户。</p>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <h3>HR 查询假期</h3>
                <p>HR 角色可按员工名查询。</p>
              </div>
              <button @click="fetchHrBalance">查询</button>
            </div>
            <label>
              员工姓名
              <input v-model="hrEmployeeName" />
            </label>
            <pre v-if="hrBalance">{{ pretty(hrBalance) }}</pre>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <h3>候选人查询</h3>
                <p>查看匹配岗位与技能。</p>
              </div>
              <button @click="searchCandidates">搜索</button>
            </div>
            <label>
              关键词
              <input v-model="candidateKeyword" />
            </label>
            <div class="list">
              <div v-for="candidate in candidates" :key="candidate.email" class="list-item">
                <strong>{{ candidate.name }}</strong>
                <span>{{ candidate.position }} · {{ candidate.years }} 年</span>
                <small>{{ candidate.skills.join(' / ') }}</small>
              </div>
            </div>
          </article>

          <article class="panel wide">
            <div class="panel-header">
              <div>
                <h3>JD 生成</h3>
                <p>当前为规则生成，后续可接大模型。</p>
              </div>
              <button @click="generateJd">生成</button>
            </div>
            <div class="form-grid">
              <label>
                岗位
                <input v-model="jdPosition" />
              </label>
              <label>
                年限
                <input v-model.number="jdYears" type="number" min="0" />
              </label>
              <label class="span-2">
                技能
                <input v-model="jdSkills" />
              </label>
            </div>
            <pre v-if="jdResult">{{ jdResult }}</pre>
          </article>
        </section>

        <section id="ai" class="panel">
          <div class="panel-header">
            <div>
              <h3>智能员工助手</h3>
              <p>支持查假期、查考勤、制度问答和请假确认；默认走 MCP Tool Calling 链路。</p>
            </div>
          </div>

          <div class="assistant-observability-layout">
          <div class="assistant-shell">
            <details class="debug-options">
              <summary>调试选项</summary>
              <label>
                调用模式
                <select v-model="employeeAgentMode">
                  <option value="MCP">MCP Agent</option>
                  <option value="LOCAL">本地 Agent</option>
                </select>
              </label>
              <p>MCP Agent 会经过 Spring AI Tool Calling，再调用 HR MCP Server；本地 Agent 仅用于对照旧链路。</p>
            </details>

            <div class="quick-prompts" aria-label="Agent 快捷问题">
              <button class="secondary" @click="useAiChatPrompt('帮我请明天下午年假')">申请年假</button>
              <button class="secondary" @click="chat('确认')">确认</button>
              <button class="secondary" @click="chat('取消')">取消</button>
              <button class="secondary" @click="useAiChatPrompt('查询我的年假余额')">查年假余额</button>
              <button class="secondary" @click="useAiChatPrompt('查询本月考勤')">查本月考勤</button>
            </div>

            <div class="assistant-thread" aria-live="polite">
              <div
                v-for="message in aiChatMessages"
                :key="message.id"
                class="assistant-message"
                :class="[message.role, {
                  selectable: Boolean(message.observation),
                  selected: selectedObservationMessage?.id === message.id
                }]"
                @click="selectObservation(message)"
              >
                <strong>{{ message.role === 'user' ? session.employeeName : 'Agent 助手' }}</strong>
                <p>{{ message.content }}</p>
                <small v-if="message.observation">点击查看本次 Agent 调用链</small>
              </div>

              <article v-if="aiChatPendingLeave" class="leave-confirmation">
                <div class="leave-confirmation-header">
                  <div>
                    <strong>请假申请确认</strong>
                    <p>{{ aiChatPendingLeave.message }}</p>
                  </div>
                  <span>待确认</span>
                </div>
                <dl>
                  <dt>请假类型</dt>
                  <dd>{{ leaveTypeText(aiChatPendingLeave.leaveType) }}</dd>
                  <dt>开始时间</dt>
                  <dd>{{ dateTimeText(aiChatPendingLeave.startTime) }}</dd>
                  <dt>结束时间</dt>
                  <dd>{{ dateTimeText(aiChatPendingLeave.endTime) }}</dd>
                  <dt>请假原因</dt>
                  <dd>{{ aiChatPendingLeave.reason }}</dd>
                </dl>
                <div class="leave-confirmation-actions">
                  <button type="button" :disabled="aiChatSending" @click="chat('确认')">
                    {{ aiChatSending ? '处理中' : '确认提交' }}
                  </button>
                  <button type="button" class="secondary" :disabled="aiChatSending" @click="chat('取消')">
                    取消
                  </button>
                </div>
              </article>
            </div>

            <form class="assistant-composer" @submit.prevent="chat()">
              <textarea
                v-model="chatMessage"
                rows="2"
                placeholder="例如：帮我请明天下午年假"
                @keydown="handleChatKeydown"
              ></textarea>
              <button type="submit" :disabled="aiChatSending || !chatMessage.trim()">
                {{ aiChatSending ? '发送中' : '发送' }}
              </button>
              <p class="assistant-composer-hint">Enter 发送，Shift + Enter 换行</p>
            </form>
          </div>

          <aside class="observation-panel" aria-label="Agent 观测面板">
            <div class="observation-panel-header">
              <div>
                <h3>Agent 观测</h3>
                <p>{{ observationTitle(selectedObservationMessage) }}</p>
              </div>
              <div v-if="selectedObservation" class="observation-header-actions">
                <button type="button" class="secondary compact-button" @click="copyDebugReport">
                  {{ debugReportCopied ? '已复制' : '复制调试报告' }}
                </button>
                <span
                  class="observation-status"
                  :class="selectedObservation.status.toLowerCase()"
                >
                  {{ statusText(selectedObservation.status) }}
                </span>
              </div>
            </div>

            <div v-if="selectedObservation" class="observation-content">
              <div class="observation-meta">
                <span>requestId</span>
                <strong>{{ selectedObservation.requestId }}</strong>
                <span>总耗时</span>
                <strong>{{ selectedObservation.totalDurationMs }} ms</strong>
              </div>

              <div v-if="selectedObservation.planner" class="observation-card">
                <div class="observation-card-header">
                  <strong>Planner：{{ plannerTypeText(selectedObservation.planner.plannerType) }}</strong>
                  <span v-if="selectedObservation.planner.fallbackReason" class="observation-status partial">
                    已回退
                  </span>
                </div>
                <dl class="summary-dl">
                  <dt>traceId</dt>
                  <dd>{{ selectedObservation.planner.traceId || '-' }}</dd>
                  <dt>计划摘要</dt>
                  <dd>{{ selectedObservation.planner.summary || '-' }}</dd>
                  <dt>fallback</dt>
                  <dd>{{ selectedObservation.planner.fallbackReason || '无' }}</dd>
                </dl>
              </div>

              <div v-if="selectedObservation.reflection" class="observation-card">
                <div class="observation-card-header">
                  <strong>Reflection：{{ reflectionActionText(selectedObservation.reflection.action) }}</strong>
                  <span :class="['observation-status', selectedObservation.reflection.action === 'PASS' ? 'success' : 'partial']">
                    {{ selectedObservation.reflection.action }}
                  </span>
                </div>
                <dl class="summary-dl">
                  <dt>traceId</dt>
                  <dd>{{ selectedObservation.reflection.traceId || '-' }}</dd>
                  <dt>规则动作</dt>
                  <dd>{{ reflectionActionText(selectedObservation.reflection.ruleAction) }}</dd>
                  <dt>原因</dt>
                  <dd>{{ selectedObservation.reflection.reason || '-' }}</dd>
                  <dt>retry/replan</dt>
                  <dd>
                    {{ selectedObservation.reflection.needRetry ? '需要重试' : '无需重试' }} /
                    {{ selectedObservation.reflection.needReplan ? '需要重规划' : '无需重规划' }}
                  </dd>
                </dl>
                <details v-if="selectedObservation.reflection.llmRawOutput" class="observation-raw">
                  <summary>查看 LLM Reflection 原始输出</summary>
                  <pre>{{ selectedObservation.reflection.llmRawOutput }}</pre>
                </details>
              </div>

              <div v-if="selectedObservation.summarySteps.length" class="observation-card">
                <strong>摘要</strong>
                <ul>
                  <li v-for="summary in selectedObservation.summarySteps" :key="summary">{{ summary }}</li>
                </ul>
              </div>

              <ol class="agent-steps">
                <li v-for="step in selectedObservation.steps" :key="step.agentName + step.durationMs">
                  <div class="agent-step-header">
                    <strong>{{ step.agentName }}</strong>
                    <span :class="['observation-status', step.status.toLowerCase()]">
                      {{ statusText(step.status) }} · {{ step.durationMs }} ms
                    </span>
                  </div>
                  <p v-if="step.summary">{{ step.summary }}</p>

                  <div v-for="tool in step.toolCalls" :key="tool.toolName + tool.traceId" class="tool-card">
                    <div class="tool-card-title">
                      <strong>{{ tool.toolName }}</strong>
                      <span :class="['observation-status', tool.status.toLowerCase()]">
                        {{ statusText(tool.status) }} · {{ tool.durationMs }} ms
                      </span>
                    </div>
                    <small v-if="tool.traceId">traceId：{{ tool.traceId }}</small>
                    <small v-if="tool.errorCode">错误码：{{ tool.errorCode }}</small>
                    <small v-if="tool.evidenceSource">证据：{{ tool.evidenceSource }}</small>

                    <dl v-if="summaryEntries(tool.inputSummary).length" class="summary-dl">
                      <template v-for="[key, value] in summaryEntries(tool.inputSummary)" :key="'in-' + key">
                        <dt>{{ key }}</dt>
                        <dd>{{ value }}</dd>
                      </template>
                    </dl>
                    <dl v-if="summaryEntries(tool.resultSummary).length" class="summary-dl">
                      <template v-for="[key, value] in summaryEntries(tool.resultSummary)" :key="'out-' + key">
                        <dt>{{ key }}</dt>
                        <dd>{{ value }}</dd>
                      </template>
                    </dl>
                  </div>
                </li>
              </ol>

              <div v-if="selectedObservation.decision" class="observation-card">
                <strong>最终判断：{{ selectedObservation.decision.outcome }}</strong>
                <p>{{ selectedObservation.decision.basis }}</p>
                <small>{{ selectedObservation.decision.needsHumanConfirmation ? '建议人工确认' : '不需要人工确认' }}</small>
              </div>
            </div>
            <p v-else class="empty">发送一次 MCP Agent 或 Multi-Agent 问题后，这里会显示模型选择了哪些 Agent 和 Tool。</p>
          </aside>
          </div>

          <div v-if="chatError" class="inline-alert">{{ chatError }}</div>
          <div v-if="chatLeaveApplications.length" class="leave-table compact-table">
            <div v-for="item in chatLeaveApplications" :key="item.applyNo" class="leave-row">
              <strong>{{ item.applyNo }}</strong>
              <span>{{ leaveTypeText(item.leaveType) }}</span>
              <span>{{ dateTimeText(item.startTime) }} 至 {{ dateTimeText(item.endTime) }}</span>
              <span class="status-text">{{ leaveStatusText(item.status) }}</span>
              <span>{{ item.reason }}</span>
            </div>
          </div>
        </section>

        <section id="advanced" class="panel advanced-panel">
          <div class="panel-header">
            <div>
              <h3>高级/调试</h3>
              <p>这里保留制度问答、Dify 对照链路、旧 Workflow 和原始身份数据，日常使用可以先收起。</p>
            </div>
            <button class="secondary" @click="showAdvancedTools = !showAdvancedTools">
              {{ showAdvancedTools ? '收起' : '展开' }}
            </button>
          </div>

          <p v-if="!showAdvancedTools" class="empty">高级工具已收起，需要排查链路或对照旧能力时再展开。</p>

          <div v-else class="advanced-grid">
            <section id="handbook" class="panel compact-panel">
              <div class="panel-header">
                <div>
                  <h3>员工手册问答</h3>
                  <p>Dify 知识库问答，可连接员工手册应用。</p>
                </div>
                <div class="button-group">
                  <button class="secondary" @click="askHandbook">普通提问</button>
                  <button @click="askHandbookStream" :disabled="handbookStreaming">
                    {{ handbookStreaming ? '输出中' : '流式提问' }}
                  </button>
                </div>
              </div>
              <textarea v-model="handbookQuestion" rows="3"></textarea>
              <div v-if="handbookError" class="inline-alert">{{ handbookError }}</div>
              <div v-if="handbookAnswer" class="answer-box">
                <strong>回答</strong>
                <p>{{ handbookAnswer.answer }}</p>
                <small>{{ handbookAnswer.source }}<template v-if="handbookAnswer.conversationId"> · {{ handbookAnswer.conversationId }}</template></small>
              </div>
            </section>

            <section class="panel compact-panel">
              <div class="panel-header">
                <div>
                  <h3>当前员工原始数据</h3>
                  <p>用于确认 JWT 或 Gateway 透传头识别出的员工身份。</p>
                </div>
                <button class="secondary" @click="fetchMe">刷新</button>
              </div>
              <pre v-if="me">{{ pretty(me) }}</pre>
              <p v-else class="empty">尚未加载当前员工信息。</p>
            </section>

            <section id="dify-workflow" class="panel compact-panel">
          <div class="panel-header">
            <div>
              <h3>员工 AI 助手</h3>
              <p>复用同一会话窗口，可选择固定流程编排或由 HR Agent 自主调用工具。</p>
            </div>
            <button @click="runDifyWorkflowChat" :disabled="difyWorkflowSending || !difyWorkflowMessage.trim()">
              {{ difyWorkflowSending ? '发送中' : '发送' }}
            </button>
          </div>

          <div class="assistant-shell">
            <div class="assistant-mode" aria-label="助手模式">
              <button
                type="button"
                :class="{ active: aiAssistantType === 'WORKFLOW' }"
                @click="aiAssistantType = 'WORKFLOW'"
              >
                流程助手
              </button>
              <button
                type="button"
                :class="{ active: aiAssistantType === 'HR_AGENT' }"
                @click="aiAssistantType = 'HR_AGENT'"
              >
                HR Agent
              </button>
            </div>

            <div class="quick-prompts" aria-label="快捷问题">
              <button class="secondary" @click="useDifyWorkflowPrompt('帮我查一下我的年假余额')">查年假余额</button>
              <button class="secondary" @click="useDifyWorkflowPrompt('查询我今年的请假记录')">查请假记录</button>
              <button class="secondary" @click="useDifyWorkflowPrompt('查询我 2026-06-05 的考勤')">查考勤记录</button>
              <button class="secondary" @click="useDifyWorkflowPrompt('帮我请明天下午年假，原因是个人事务')">申请年假</button>
            </div>

            <div class="assistant-thread" aria-live="polite">
              <div
                v-for="message in difyWorkflowMessages"
                :key="message.id"
                class="assistant-message"
                :class="message.role"
              >
                <strong>{{ message.role === 'user' ? session.employeeName : 'AI 助手' }}</strong>
                <p>{{ message.content }}</p>
                <small v-if="message.source">{{ message.source }}</small>
              </div>

              <article v-if="pendingLeaveConfirmation" class="leave-confirmation">
                <div class="leave-confirmation-header">
                  <div>
                    <strong>请假申请确认</strong>
                    <p>{{ pendingLeaveConfirmation.message }}</p>
                  </div>
                  <span>待确认</span>
                </div>
                <dl>
                  <dt>请假类型</dt>
                  <dd>{{ leaveTypeText(pendingLeaveConfirmation.leaveType) }}</dd>
                  <dt>开始时间</dt>
                  <dd>{{ dateTimeText(pendingLeaveConfirmation.startTime) }}</dd>
                  <dt>结束时间</dt>
                  <dd>{{ dateTimeText(pendingLeaveConfirmation.endTime) }}</dd>
                  <dt>请假原因</dt>
                  <dd>{{ pendingLeaveConfirmation.reason }}</dd>
                </dl>
                <div v-if="leaveConfirmError" class="inline-alert">{{ leaveConfirmError }}</div>
                <div class="leave-confirmation-actions">
                  <button
                    type="button"
                    :disabled="leaveConfirmSubmitting"
                    @click="confirmLeaveApplication"
                  >
                    {{ leaveConfirmSubmitting ? '提交中' : '确认提交' }}
                  </button>
                  <button
                    type="button"
                    class="secondary"
                    :disabled="leaveConfirmSubmitting"
                    @click="cancelLeaveApplication"
                  >
                    取消
                  </button>
                </div>
              </article>
            </div>

            <form class="assistant-composer" @submit.prevent="runDifyWorkflowChat">
              <textarea
                v-model="difyWorkflowMessage"
                rows="2"
                placeholder="例如：帮我查一下我的年假余额"
              ></textarea>
              <button type="submit" :disabled="difyWorkflowSending || !difyWorkflowMessage.trim()">
                {{ difyWorkflowSending ? '发送中' : '发送' }}
              </button>
            </form>
          </div>

          <div v-if="difyWorkflowError" class="inline-alert">{{ difyWorkflowError }}</div>
        </section>

            <section id="workflow" class="panel compact-panel">
          <div class="panel-header">
            <div>
              <h3>请假 AI Workflow</h3>
              <p>展示意图识别、槽位抽取、余额校验和申请提交。</p>
            </div>
            <button @click="runWorkflow">运行</button>
          </div>
          <textarea v-model="workflowMessage" rows="3"></textarea>
          <div v-if="workflowError" class="inline-alert">{{ workflowError }}</div>

          <div v-if="workflowResult" class="workflow">
            <div class="workflow-summary">
              <strong>{{ workflowResult.workflowId }}</strong>
              <span>{{ workflowResult.intent }} · {{ workflowResult.status }}</span>
            </div>
            <ol class="timeline">
              <li v-for="step in workflowResult.steps" :key="step.stepName + step.executedAt">
                <span class="dot" :class="step.status.toLowerCase()"></span>
                <div>
                  <strong>{{ step.stepName }}</strong>
                  <p>{{ step.message }}</p>
                </div>
              </li>
            </ol>
            <pre>{{ pretty(workflowResult.result) }}</pre>
          </div>
            </section>
          </div>
        </section>
      </template>
    </section>
  </main>
</template>
