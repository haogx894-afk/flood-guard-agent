<template>
  <main class="app-root">
    <section class="app-frame" aria-label="北京市山洪防御空间辅助决策智能体">
      <header class="topbar">
        <div class="brand">
          <div class="brand-mark" aria-hidden="true">汛</div>
          <div>
            <p class="brand-kicker">Fluent AI Workspace</p>
            <h1>北京市山洪防御空间辅助决策智能体</h1>
          </div>
        </div>

        <div class="header-actions">
          <nav class="app-nav" aria-label="应用页面">
            <button
              type="button"
              :class="{ active: activeView === 'chat' }"
              @click="activeView = 'chat'"
            >
              智能对话
            </button>
            <button
              type="button"
              :class="{ active: activeView === 'knowledge' }"
              @click="activeView = 'knowledge'"
            >
              知识库管理
            </button>
          </nav>

          <div class="runtime-status" :class="{ active: isStreaming }">
            <span aria-hidden="true"></span>
            {{ isStreaming ? '响应中' : '在线' }}
          </div>
        </div>
      </header>

      <div class="workspace">
        <section v-if="activeView === 'chat'" class="chat-card fluent-card">
          <div class="chat-card-header">
            <div>
              <p class="section-kicker">AI 防汛管家</p>
              <h2>空间辅助决策会话</h2>
            </div>
            <div class="chat-meta">
              <span>{{ messages.length }} 条消息</span>
              <span>{{ isStreaming ? '生成中' : '待命' }}</span>
            </div>
          </div>

          <section
            ref="messagePanelRef"
            class="message-panel"
            aria-label="聊天记录"
            @scroll.passive="handlePanelScroll"
          >
            <ChatMessage
              v-for="message in messages"
              :key="message.id"
              :message="message"
            />
          </section>

          <form class="composer" @submit.prevent="sendMessage">
            <textarea
              v-model.trim="inputValue"
              :disabled="isStreaming"
              rows="2"
              placeholder="请输入山洪防御、预案、村庄、转移路线、监测站等问题"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <button type="submit" :disabled="!canSend">
              {{ isStreaming ? '生成中' : '发送' }}
            </button>
          </form>
        </section>

        <section v-else class="knowledge-card fluent-card">
          <div class="knowledge-header">
            <div>
              <p class="section-kicker">Graph RAG Knowledge Base</p>
              <h2>知识库管理</h2>
            </div>
            <div class="knowledge-actions">
              <button type="button">刷新状态</button>
              <button type="button" class="primary">上传文档</button>
            </div>
          </div>

          <div class="knowledge-tabs" role="tablist" aria-label="知识库模块">
            <button
              type="button"
              :class="{ active: knowledgeTab === 'documents' }"
              @click="knowledgeTab = 'documents'"
            >
              文档管理
            </button>
            <button
              type="button"
              :class="{ active: knowledgeTab === 'graph' }"
              @click="knowledgeTab = 'graph'"
            >
              知识图谱
            </button>
          </div>

          <section v-if="knowledgeTab === 'documents'" class="knowledge-body">
            <div class="stat-grid">
              <div
                v-for="item in documentStats"
                :key="item.label"
                class="stat-card"
              >
                <span>{{ item.value }}</span>
                <p>{{ item.label }}</p>
              </div>
            </div>

            <div class="table-card">
              <div class="table-header">
                <div>
                  <h3>已上传文档</h3>
                  <p>后续接入 API 后，这里展示 PDF、Word、Markdown 等入库状态。</p>
                </div>
                <div class="table-tools">
                  <button type="button">扫描/重试</button>
                  <button type="button">清空失败</button>
                </div>
              </div>

              <div class="document-table">
                <div class="table-row table-head">
                  <span>文件名</span>
                  <span>类型</span>
                  <span>状态</span>
                  <span>分块</span>
                  <span>更新时间</span>
                </div>
                <div
                  v-for="doc in documentRows"
                  :key="doc.name"
                  class="table-row"
                >
                  <span class="doc-name">{{ doc.name }}</span>
                  <span>{{ doc.type }}</span>
                  <span>
                    <em :class="['status-dot', doc.statusType]"></em>
                    {{ doc.status }}
                  </span>
                  <span>{{ doc.chunks }}</span>
                  <span>{{ doc.updatedAt }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-else class="knowledge-body graph-layout">
            <div class="graph-card">
              <div class="table-header">
                <div>
                  <h3>知识图谱</h3>
                  <p>用于把实体、关系与向量检索结合，形成图谱增强 RAG。</p>
                </div>
                <span class="graph-badge">Graph RAG</span>
              </div>

              <div class="graph-canvas" aria-label="知识图谱预览">
                <div class="graph-link link-a"></div>
                <div class="graph-link link-b"></div>
                <div class="graph-link link-c"></div>
                <div class="graph-node node-main">山洪沟流域</div>
                <div class="graph-node node-a">山区村</div>
                <div class="graph-node node-b">安置点</div>
                <div class="graph-node node-c">预案条款</div>
                <div class="graph-node node-d">监测站</div>
              </div>
            </div>

            <div class="graph-side">
              <div
                v-for="item in graphStats"
                :key="item.label"
                class="stat-card"
              >
                <span>{{ item.value }}</span>
                <p>{{ item.label }}</p>
              </div>
              <div class="relation-card">
                <h3>关系类型</h3>
                <p>所属行政区、位于流域内、关联安置点、经过桥涵、引用预案条款。</p>
              </div>
            </div>
          </section>
        </section>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import ChatMessage from './components/ChatMessage.vue';
import { buildManusSseUrl } from './services/api';

const CHAT_ID_STORAGE_KEY = 'hgx-ai-agent-chat-id';
const CONNECTING_MESSAGE = '正在连接防汛智能体...';
const AUTO_SCROLL_THRESHOLD = 20;
const openingMessage = '您好！我是您的专属防汛管家。在制定方案前，我需要先了解您的问题。请输入您的问题？';

const activeView = ref('chat');
const knowledgeTab = ref('documents');

const documentStats = [
  { label: '文档总数', value: '0' },
  { label: '向量切片', value: '0' },
  { label: '待接入 API', value: 'API' },
  { label: 'Graph RAG', value: 'ON' },
];

const documentRows = [
  {
    name: '北京市山洪灾害防御预案.pdf',
    type: 'PDF',
    status: '待接入',
    statusType: 'pending',
    chunks: '-',
    updatedAt: '-',
  },
  {
    name: '山区村防御对象台账.docx',
    type: 'DOCX',
    status: '待接入',
    statusType: 'pending',
    chunks: '-',
    updatedAt: '-',
  },
  {
    name: '山洪沟流域与危险区关系表.xlsx',
    type: 'XLSX',
    status: '待接入',
    statusType: 'pending',
    chunks: '-',
    updatedAt: '-',
  },
];

const graphStats = [
  { label: '实体', value: '0' },
  { label: '关系', value: '0' },
  { label: '三元组', value: '0' },
];

function getOrCreateChatId() {
  let chatId = localStorage.getItem(CHAT_ID_STORAGE_KEY);
  if (!chatId) {
    chatId = crypto.randomUUID();
    localStorage.setItem(CHAT_ID_STORAGE_KEY, chatId);
  }
  return chatId;
}

const chatId = getOrCreateChatId();

const messages = ref([
  {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: openingMessage,
    isTyping: false,
  },
]);

const inputValue = ref('');
const isStreaming = ref(false);
const shouldAutoScroll = ref(true);
const messagePanelRef = ref(null);
const typingTimers = new Set();
let eventSource = null;
let typingQueue = Promise.resolve();

const canSend = computed(() => inputValue.value.length > 0 && !isStreaming.value);

function getDistanceToBottom() {
  const panel = messagePanelRef.value;
  if (!panel) {
    return 0;
  }
  return panel.scrollHeight - panel.scrollTop - panel.clientHeight;
}

function isNearBottom() {
  return getDistanceToBottom() <= AUTO_SCROLL_THRESHOLD;
}

function handlePanelScroll() {
  shouldAutoScroll.value = isNearBottom();
}

function scrollToBottom({ behavior = 'smooth', force = false } = {}) {
  nextTick(() => {
    const panel = messagePanelRef.value;
    if (!panel) {
      return;
    }
    if (!force && !shouldAutoScroll.value) {
      return;
    }
    panel.scrollTo({
      top: panel.scrollHeight,
      behavior,
    });
  });
}

function wait(ms) {
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => {
      typingTimers.delete(timer);
      resolve();
    }, ms);
    typingTimers.add(timer);
  });
}

function pushMessage(role, content) {
  messages.value.push({
    id: crypto.randomUUID(),
    role,
    content,
    isTyping: false,
  });
  scrollToBottom();
}

function isStepOutput(content) {
  return content?.startsWith('Step ');
}

async function pushTypedAssistantMessage(fullContent) {
  const messageId = crypto.randomUUID();

  messages.value.push({
    id: messageId,
    role: 'assistant',
    content: '',
    isTyping: true,
  });
  scrollToBottom();

  const isStepMessage = isStepOutput(fullContent);
  const interval = isStepMessage ? 2 : fullContent.length > 500 ? 4 : 10;
  const charsPerTick = isStepMessage ? Math.max(8, Math.ceil(fullContent.length / 120)) : 1;

  for (let index = 0; index < fullContent.length; index += charsPerTick) {
    const target = messages.value.find((message) => message.id === messageId);
    if (!target) {
      return;
    }

    target.content += fullContent.slice(index, index + charsPerTick);
    scrollToBottom({ behavior: 'auto' });
    await wait(interval);
  }

  const target = messages.value.find((message) => message.id === messageId);
  if (target) {
    target.isTyping = false;
  }
  scrollToBottom();
}

function enqueueTypedAssistantMessage(content) {
  typingQueue = typingQueue
    .catch(() => undefined)
    .then(() => pushTypedAssistantMessage(content));
  return typingQueue;
}

function removeMessage(messageId) {
  messages.value = messages.value.filter((message) => message.id !== messageId);
}

function replaceMessageContent(messageId, content) {
  const target = messages.value.find((message) => message.id === messageId);
  if (target) {
    target.content = content;
    target.isTyping = false;
    scrollToBottom();
  }
}

function closeEventSource() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}

function sendMessage() {
  if (!canSend.value) {
    return;
  }

  shouldAutoScroll.value = isNearBottom();
  const userMessage = inputValue.value;
  inputValue.value = '';

  pushMessage('user', userMessage);

  const loadingMessageId = crypto.randomUUID();
  messages.value.push({
    id: loadingMessageId,
    role: 'assistant',
    content: CONNECTING_MESSAGE,
    isTyping: false,
  });

  isStreaming.value = true;
  scrollToBottom();
  closeEventSource();

  let hasReceivedData = false;
  eventSource = new EventSource(buildManusSseUrl(userMessage, chatId));

  eventSource.onmessage = (event) => {
    const content = event.data?.trim();
    if (!content) {
      return;
    }

    if (!hasReceivedData) {
      hasReceivedData = true;
      removeMessage(loadingMessageId);
    }

    enqueueTypedAssistantMessage(content);
  };

  eventSource.onerror = async () => {
    closeEventSource();

    if (!hasReceivedData) {
      replaceMessageContent(
        loadingMessageId,
        '连接后端 SSE 接口失败，请确认 SpringBoot 服务已启动，并检查 http://localhost:8123/api/ai/manus/chat 是否可以访问。'
      );
    }

    await typingQueue.catch(() => undefined);
    isStreaming.value = false;
    scrollToBottom();
  };
}

onBeforeUnmount(() => {
  closeEventSource();
  typingTimers.forEach((timer) => window.clearTimeout(timer));
  typingTimers.clear();
});
</script>
