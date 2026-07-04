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

        <nav class="top-tabs" aria-label="应用视图">
          <button
            v-for="item in navItems"
            :key="item"
            type="button"
            :class="{ active: item === '智能对话' }"
          >
            {{ item }}
          </button>
        </nav>

        <div class="runtime-status" :class="{ active: isStreaming }">
          <span aria-hidden="true"></span>
          {{ isStreaming ? '响应中' : '在线' }}
        </div>
      </header>

      <div class="workspace">
        <aside class="sidebar fluent-card" aria-label="工作区导航">
          <div class="panel-title">工作台</div>
          <button
            v-for="item in workspaceItems"
            :key="item.name"
            type="button"
            class="side-item"
            :class="{ active: item.active }"
          >
            <span aria-hidden="true">{{ item.icon }}</span>
            {{ item.name }}
          </button>

          <div class="sidebar-section">
            <div class="panel-title">数据图层</div>
            <div
              v-for="item in layerItems"
              :key="item"
              class="layer-pill"
            >
              <span aria-hidden="true"></span>
              {{ item }}
            </div>
          </div>
        </aside>

        <section class="chat-card fluent-card">
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

        <aside class="insight-panel fluent-card" aria-label="运行状态">
          <div class="panel-title">会话状态</div>
          <div class="metric-grid">
            <div
              v-for="metric in metricItems"
              :key="metric.label"
              class="metric-card"
            >
              <span>{{ metric.value }}</span>
              <p>{{ metric.label }}</p>
            </div>
          </div>

          <div class="sidebar-section">
            <div class="panel-title">分析范围</div>
            <div class="scope-list">
              <span>山洪沟流域</span>
              <span>危险区</span>
              <span>安置点</span>
              <span>监测站</span>
              <span>防洪预案</span>
            </div>
          </div>
        </aside>
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

const navItems = ['智能对话', '空间研判', '预案核查'];
const workspaceItems = [
  { name: '智能对话', icon: '▦', active: true },
  { name: '空间查询', icon: '⌖', active: false },
  { name: '转移路线', icon: '↗', active: false },
  { name: '监测覆盖', icon: '◌', active: false },
];
const layerItems = ['区界', '流域', '村庄', '河流', '水库', '站点'];
const metricItems = [
  { label: '会话轮次', value: 'RAG' },
  { label: '响应模式', value: 'SSE' },
  { label: '界面体系', value: 'F2' },
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
