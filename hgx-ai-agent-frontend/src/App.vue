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
              @click="switchView('chat')"
            >
              智能对话
            </button>
            <button
              type="button"
              :class="{ active: activeView === 'knowledge' }"
              @click="switchView('knowledge')"
            >
              知识库管理
            </button>
          </nav>

          <div class="runtime-status" :class="{ active: isStreaming || isKnowledgeBusy }">
            <span aria-hidden="true"></span>
            {{ isStreaming ? '响应中' : isKnowledgeBusy ? '处理中' : '在线' }}
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
              <p class="section-kicker">Knowledge Base Management</p>
              <h2>知识库管理</h2>
            </div>
            <div class="knowledge-actions">
              <button type="button" :disabled="isKnowledgeBusy" @click="fetchDocuments">
                刷新状态
              </button>
              <button type="button" class="primary" :disabled="isKnowledgeBusy" @click="openUploadDialog">
                上传文档
              </button>
              <input
                ref="fileInputRef"
                class="file-input"
                type="file"
                accept=".pdf,.doc,.docx,.md,.markdown"
                @change="handleFileSelected"
              />
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
            <div v-if="knowledgeError" class="message-banner error">
              {{ knowledgeError }}
            </div>
            <div v-if="knowledgeNotice" class="message-banner">
              {{ knowledgeNotice }}
            </div>

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
                  <p>知识库中的文档入库信息</p>
                </div>
                <div class="table-tools">
                  <button type="button" :disabled="isKnowledgeBusy" @click="fetchDocuments">
                    刷新
                  </button>
                </div>
              </div>

              <div class="document-table">
                <div class="table-row table-head">
                  <span>文件名</span>
                  <span>类型</span>
                  <span>状态</span>
                  <span>分块</span>
                  <span>更新时间</span>
                  <span>操作</span>
                </div>

                <div v-if="isLoadingDocuments" class="empty-row">
                  正在加载知识库文档...
                </div>
                <div v-else-if="documentRows.length === 0" class="empty-row">
                  暂无文档，请点击右上角上传文档。
                </div>

                <div
                  v-for="doc in documentRows"
                  v-else
                  :key="doc.id"
                  class="table-row"
                >
                  <span class="doc-name" :title="doc.name">{{ doc.name }}</span>
                  <span>{{ doc.type }}</span>
                  <span>
                    <em :class="['status-dot', doc.statusType]"></em>
                    {{ doc.status }}
                  </span>
                  <span>{{ doc.chunks }}</span>
                  <span>{{ doc.updatedAt }}</span>
                  <span class="row-actions">
                    <button type="button" :disabled="isKnowledgeBusy" @click="rebuildDocument(doc.id)">
                      重建
                    </button>
                    <button type="button" class="danger" :disabled="isKnowledgeBusy" @click="deleteDocument(doc.id)">
                      删除
                    </button>
                  </span>
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
import {
  buildManusSseUrl,
  deleteKnowledgeDocument,
  getKnowledgeDocuments,
  rebuildKnowledgeDocument,
  uploadKnowledgeDocument,
} from './services/api';

const CHAT_ID_STORAGE_KEY = 'hgx-ai-agent-chat-id';
const CONNECTING_MESSAGE = '正在连接防汛智能体...';
const AUTO_SCROLL_THRESHOLD = 20;
const openingMessage = '您好！我是您的专属防汛管家。在制定方案前，我需要先了解您的问题。请输入您的问题？';

const activeView = ref('chat');
const knowledgeTab = ref('documents');
const knowledgeDocuments = ref([]);
const isLoadingDocuments = ref(false);
const isUploadingDocument = ref(false);
const operatingDocumentId = ref('');
const knowledgeError = ref('');
const knowledgeNotice = ref('');
const fileInputRef = ref(null);

const graphStats = computed(() => [
  { label: '实体', value: '-' },
  { label: '关系', value: '-' },
  { label: '入库文档', value: knowledgeDocuments.value.length },
]);

const isKnowledgeBusy = computed(
  () => isLoadingDocuments.value || isUploadingDocument.value || Boolean(operatingDocumentId.value)
);

const documentStats = computed(() => {
  const documents = knowledgeDocuments.value;
  const completedCount = documents.filter((item) => item.status === 'COMPLETED').length;
  const failedCount = documents.filter((item) => item.status === 'FAILED').length;
  const chunkCount = documents.reduce((sum, item) => sum + Number(item.chunkCount || 0), 0);

  return [
    { label: '文档总数', value: documents.length },
    { label: '向量切片', value: chunkCount },
    { label: '已完成', value: completedCount },
    { label: '失败', value: failedCount },
  ];
});

const documentRows = computed(() =>
  knowledgeDocuments.value.map((doc) => ({
    id: doc.id,
    name: doc.fileName,
    type: doc.fileType?.toUpperCase() || '-',
    status: getStatusText(doc.status),
    statusType: getStatusType(doc.status),
    chunks: doc.chunkCount ?? 0,
    updatedAt: formatDateTime(doc.updatedAt),
  }))
);

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

async function switchView(view) {
  activeView.value = view;
  if (view === 'knowledge') {
    await fetchDocuments();
  }
}

function getStatusText(status) {
  const statusMap = {
    UPLOADED: '已上传',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败',
  };
  return statusMap[status] || status || '-';
}

function getStatusType(status) {
  const typeMap = {
    UPLOADED: 'pending',
    PROCESSING: 'processing',
    COMPLETED: 'completed',
    FAILED: 'failed',
  };
  return typeMap[status] || 'pending';
}

function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function setKnowledgeNotice(message) {
  knowledgeNotice.value = message;
  window.setTimeout(() => {
    if (knowledgeNotice.value === message) {
      knowledgeNotice.value = '';
    }
  }, 2600);
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.response?.data || error?.message || fallback;
}

async function fetchDocuments() {
  knowledgeError.value = '';
  isLoadingDocuments.value = true;
  try {
    const { data } = await getKnowledgeDocuments();
    knowledgeDocuments.value = Array.isArray(data) ? data : [];
  } catch (error) {
    knowledgeError.value = getErrorMessage(error, '加载知识库文档失败');
  } finally {
    isLoadingDocuments.value = false;
  }
}

function openUploadDialog() {
  fileInputRef.value?.click();
}

async function handleFileSelected(event) {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file) {
    return;
  }

  knowledgeError.value = '';
  knowledgeNotice.value = '';
  isUploadingDocument.value = true;
  try {
    const { data } = await uploadKnowledgeDocument(file, true);
    setKnowledgeNotice(`文档“${data.fileName || file.name}”已入库`);
    await fetchDocuments();
  } catch (error) {
    knowledgeError.value = getErrorMessage(error, '上传文档失败');
  } finally {
    isUploadingDocument.value = false;
  }
}

async function rebuildDocument(documentId) {
  knowledgeError.value = '';
  knowledgeNotice.value = '';
  operatingDocumentId.value = documentId;
  try {
    await rebuildKnowledgeDocument(documentId);
    setKnowledgeNotice('文档已重新切分并写入向量库');
    await fetchDocuments();
  } catch (error) {
    knowledgeError.value = getErrorMessage(error, '重建文档失败');
  } finally {
    operatingDocumentId.value = '';
  }
}

async function deleteDocument(documentId) {
  const confirmed = window.confirm('确认删除该文档及其向量数据吗？');
  if (!confirmed) {
    return;
  }

  knowledgeError.value = '';
  knowledgeNotice.value = '';
  operatingDocumentId.value = documentId;
  try {
    await deleteKnowledgeDocument(documentId);
    setKnowledgeNotice('文档和对应向量已删除');
    await fetchDocuments();
  } catch (error) {
    knowledgeError.value = getErrorMessage(error, '删除文档失败');
  } finally {
    operatingDocumentId.value = '';
  }
}

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
