<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

type LoginResponse = {
  tokenType: string;
  accessToken: string;
  username: string;
  employeeName: string;
  role: 'EMPLOYEE' | 'HR';
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

type DifyWorkflowAnswer = {
  answer: string;
  source: string;
};

type AssistantMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  source?: string;
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
const session = ref<LoginResponse | null>(loadSession());

const username = ref('zhangsan');
const password = ref('123456');
const hrEmployeeName = ref('张三');
const candidateKeyword = ref('Java');
const chatMessage = ref('帮我查询张三的年假余额');
const difyWorkflowMessage = ref('帮我查一下我的年假余额');
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
const chatResult = ref<unknown>(null);
const difyWorkflowResult = ref<DifyWorkflowAnswer | null>(null);
const difyWorkflowMessages = ref<AssistantMessage[]>([
  {
    id: Date.now(),
    role: 'assistant',
    content: '你好，我可以帮你查询假期余额。'
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

onMounted(async () => {
  if (!session.value) {
    return;
  }
  await run(async () => {
    await fetchMe();
    await fetchMyBalance();
    await fetchMyLeaveApplications();
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

async function chat() {
  chatError.value = '';
  await run(async () => {
    chatResult.value = await request('/ai/chat', {
      method: 'POST',
      body: { message: chatMessage.value }
    });
  }, (message) => {
    chatError.value = message;
  }, false);
}

async function runDifyWorkflowChat() {
  const message = difyWorkflowMessage.value.trim();
  if (!message || difyWorkflowSending.value) {
    return;
  }

  difyWorkflowError.value = '';
  difyWorkflowSending.value = true;
  difyWorkflowMessages.value.push({
    id: Date.now(),
    role: 'user',
    content: message
  });
  difyWorkflowMessage.value = '';

  await run(async () => {
    difyWorkflowResult.value = await request<DifyWorkflowAnswer>('/ai/dify/workflow/chat', {
      method: 'POST',
      body: { message }
    });
    difyWorkflowMessages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: difyWorkflowResult.value.answer,
      source: difyWorkflowResult.value.source
    });
  }, (message) => {
    difyWorkflowError.value = message;
    difyWorkflowMessages.value.push({
      id: Date.now() + 2,
      role: 'assistant',
      content: `流程调用失败：${message}`
    });
  }, false);
  difyWorkflowSending.value = false;
}

function useDifyWorkflowPrompt(message: string) {
  difyWorkflowMessage.value = message;
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
  chatResult.value = null;
  chatError.value = '';
  difyWorkflowResult.value = null;
  difyWorkflowError.value = '';
  difyWorkflowSending.value = false;
  difyWorkflowMessages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '你好，我可以帮你查询假期余额。'
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

function leaveTypeText(type: string) {
  const names: Record<string, string> = {
    ANNUAL: '年假',
    SICK: '病假',
    PERSONAL: '事假'
  };
  return names[type] ?? type;
}

function dateTimeText(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function isAiChatResponse(value: unknown): value is { intent: string; reply: string; data: unknown } {
  return typeof value === 'object' && value !== null && 'intent' in value && 'reply' in value;
}
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">HR</div>
        <div>
          <h1>HR AI Assistant</h1>
          <p>Gateway + AI Workflow Demo</p>
        </div>
      </div>

      <nav v-if="session" class="nav-list">
        <a href="#employee" :class="{ disabled: !isEmployee && !isHr }">员工端</a>
        <a href="#leave-history" :class="{ disabled: !isEmployee && !isHr }">请假记录</a>
        <a href="#handbook" :class="{ disabled: !isEmployee && !isHr }">员工手册</a>
        <a v-if="isHr" href="#hr">HR 工作台</a>
        <a href="#ai">AI 助手</a>
        <a href="#dify-workflow">Dify Workflow</a>
        <a href="#workflow">Workflow</a>
      </nav>
    </aside>

    <section class="content">
      <div class="topbar">
        <div>
          <h2>{{ session ? '工作台' : '登录' }}</h2>
          <p>所有请求先进入 Gateway，再转发到 hr-service。</p>
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
          <article class="panel">
            <div class="panel-header">
              <div>
                <h3>员工端身份</h3>
                <p>从 JWT 或 Gateway 透传头识别当前员工。</p>
              </div>
              <button class="secondary" @click="fetchMe">刷新</button>
            </div>
            <pre v-if="me">{{ pretty(me) }}</pre>
            <p v-else class="empty">尚未加载当前员工信息。</p>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <h3>我的假期余额</h3>
                <p>员工端不传 employeeName。</p>
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
              <span class="status-text">{{ item.status }}</span>
              <span>{{ item.reason }}</span>
            </div>
          </div>
          <p v-else class="empty">当前筛选条件下暂无请假记录。</p>
        </section>

        <section id="handbook" class="panel">
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

        <section v-if="isHr" id="hr" class="dashboard-grid">
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
              <h3>AI 对话入口</h3>
              <p>规则式意图识别，模拟 Tool Calling。</p>
            </div>
            <button @click="chat">发送</button>
          </div>
          <textarea v-model="chatMessage" rows="3"></textarea>
          <div v-if="chatError" class="inline-alert">{{ chatError }}</div>
          <div v-if="isAiChatResponse(chatResult)" class="ai-reply">{{ chatResult.reply }}</div>
          <div v-if="chatLeaveApplications.length" class="leave-table compact-table">
            <div v-for="item in chatLeaveApplications" :key="item.applyNo" class="leave-row">
              <strong>{{ item.applyNo }}</strong>
              <span>{{ leaveTypeText(item.leaveType) }}</span>
              <span>{{ dateTimeText(item.startTime) }} 至 {{ dateTimeText(item.endTime) }}</span>
              <span class="status-text">{{ item.status }}</span>
              <span>{{ item.reason }}</span>
            </div>
          </div>
          <pre v-if="chatResult">{{ pretty(chatResult) }}</pre>
        </section>

        <section id="dify-workflow" class="panel">
          <div class="panel-header">
            <div>
              <h3>员工 AI 助手</h3>
              <p>前端提问，后端调用 Dify 单流程，再由流程回调 HR Tool API。</p>
            </div>
            <button @click="runDifyWorkflowChat" :disabled="difyWorkflowSending || !difyWorkflowMessage.trim()">
              {{ difyWorkflowSending ? '发送中' : '发送' }}
            </button>
          </div>

          <div class="assistant-shell">
            <div class="quick-prompts" aria-label="快捷问题">
              <button class="secondary" @click="useDifyWorkflowPrompt('帮我查一下我的年假余额')">查年假余额</button>
              <button class="secondary" @click="useDifyWorkflowPrompt('我的病假还剩多少天')">查病假余额</button>
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

        <section id="workflow" class="panel">
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
      </template>
    </section>
  </main>
</template>
