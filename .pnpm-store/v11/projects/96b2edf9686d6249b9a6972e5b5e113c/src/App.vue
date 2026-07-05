<template>
  <main class="app-root">
    <section class="app-frame" aria-label="北京市山洪防御空间辅助决策智能体">
      <header class="topbar">
        <div class="brand">
          <img class="brand-mark" src="/flood-logo.png" alt="网站 logo" />
          <div>
<!--            <p class="brand-kicker">Fluent AI Workspace</p>-->
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
<!--              <p class="section-kicker">AI 防汛管家</p>-->
              <h2>山洪防御空间辅助决策会话</h2>
            </div>
            <div class="chat-meta">
              <span>{{ messages.length }} 条消息记录</span>
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
              rows="3"
              placeholder="Enter 发送，Shift + Enter 换行"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <div class="composer-footer">
              <div class="composer-tools" aria-label="输入模式">
                <button type="button" class="tool-chip">
                  深度思考
                </button>
                <button type="button" class="tool-chip">
                  智能搜索
                </button>
              </div>
              <button
                v-if="isStreaming"
                type="button"
                class="send-or-stop stop-button"
                aria-label="停止生成"
                title="停止生成"
                @click="stopGeneration"
              >
                ■
              </button>
              <button
                v-else
                type="submit"
                class="send-or-stop send-button"
                :disabled="!canSend"
                aria-label="发送"
                title="发送"
              >
                ➤
              </button>
            </div>
          </form>
        </section>

        <section v-else class="knowledge-card fluent-card">
          <div class="knowledge-header">
            <div>
              <p class="section-kicker">Knowledge Base Management</p>
              <h2>知识库管理</h2>
            </div>
          </div>

          <div class="knowledge-tabs" role="tablist" aria-label="知识库模块">
            <button
              type="button"
              :class="{ active: knowledgeTab === 'documents' }"
              @click="switchKnowledgeTab('documents')"
            >
              文档管理
            </button>
            <button
              type="button"
              :class="{ active: knowledgeTab === 'graph' }"
              @click="switchKnowledgeTab('graph')"
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
                  <button type="button" class="primary" :disabled="isKnowledgeBusy" @click="openUploadDialog">
                    上传文档
                  </button>
                  <input
                    ref="fileInputRef"
                    class="file-input"
                    type="file"
                    multiple
                    accept=".pdf"
                    @change="handleFileSelected"
                  />
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

          <section v-else class="knowledge-body graph-workspace" :class="{ fullscreen: isGraphFullscreen }">
            <div v-if="graphError" class="message-banner error">
              {{ graphError }}
            </div>
            <div v-if="graphNotice" class="message-banner">
              {{ graphNotice }}
            </div>

            <div class="graph-top-grid">
              <div
                v-for="item in graphStats"
                :key="item.label"
                class="stat-card"
              >
                <span>{{ item.value }}</span>
                <p>{{ item.label }}</p>
              </div>
            </div>

            <div class="graph-shell">
              <aside class="graph-control-panel">
                <div class="panel-block">
                  <div class="panel-title">图谱查询</div>
                  <div class="graph-form">
                    <label>
                      图谱关键词
                      <input v-model.trim="graphKeyword" type="text" placeholder="例如：张家坟村、永定河、危险区" />
                    </label>
                    <div class="graph-form-grid">
                      <label>
                        最大深度
                        <input v-model.number="graphDepth" type="number" min="1" max="5" />
                      </label>
                      <label>
                        返回节点
                        <input v-model.number="graphLimit" type="number" min="1" max="1000" />
                      </label>
                    </div>
                    <div class="graph-actions">
                      <button type="button" class="primary" :disabled="isGraphBusy" @click="loadGraphData">
                        查询图谱
                      </button>
                      <button type="button" :disabled="isGraphBusy" @click="loadGraphStats">
                        刷新统计
                      </button>
                    </div>
                  </div>
                </div>

                <div v-if="graphSettings.showSearchBar" class="panel-block">
                  <div class="panel-title">当前结果内搜索</div>
                  <input
                    v-model.trim="graphFilterKeyword"
                    class="graph-inline-input"
                    type="text"
                    placeholder="过滤当前实体 / 关系"
                  />
                  <p class="graph-muted">
                    当前显示 {{ visibleGraphNodes.length }} 个实体、{{ visibleGraphEdges.length }} 条关系。
                  </p>
                </div>

                <div class="panel-block">
                  <div class="panel-title">可视化控制</div>
                  <div class="graph-form-grid">
                    <label>
                      节点大小
                      <input v-model.number="graphNodeSize" type="range" min="22" max="86" />
                    </label>
                    <label>
                      边粗细
                      <input v-model.number="graphEdgeWidth" type="range" :min="graphEdgeMinWidth" :max="graphEdgeMaxWidth" />
                    </label>
                  </div>
                  <label>
                    图布局
                    <select v-model="graphLayout" @change="buildNodePositions">
                      <option value="circle">环形布局</option>
                      <option value="grid">网格布局</option>
                      <option value="radial">放射布局</option>
                    </select>
                  </label>
                  <label>
                    节点颜色
                    <input v-model="selectedTypeColor" type="color" @input="applySelectedTypeColor" />
                  </label>
                  <p class="graph-muted">
                    选中节点后，可为该实体类型单独设置颜色。
                  </p>
                  <div class="graph-tool-grid">
                    <button type="button" @click="rotateGraph(-15)">逆时针</button>
                    <button type="button" @click="rotateGraph(15)">顺时针</button>
                    <button type="button" @click="zoomGraph(1.18)">放大</button>
                    <button type="button" @click="zoomGraph(0.85)">缩小</button>
                    <button type="button" @click="resetGraphView">重置缩放</button>
                    <button type="button" @click="toggleGraphFullscreen">{{ isGraphFullscreen ? '退出全屏' : '全屏' }}</button>
                  </div>
                </div>

                <div class="panel-block">
                  <div class="panel-title">设置</div>
                  <label class="graph-check">
                    <input v-model="graphSettings.healthCheck" type="checkbox" @change="handleHealthCheckToggle" />
                    健康检查
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.showPropertyPanel" type="checkbox" />
                    显示属性面板
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.showSearchBar" type="checkbox" />
                    显示搜索栏
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.showNodeLabels" type="checkbox" />
                    显示节点标签
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.draggableNodes" type="checkbox" />
                    节点可拖动
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.showEdgeLabels" type="checkbox" />
                    显示边标签
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.hideUnselectedEdges" type="checkbox" />
                    隐藏未选中的边
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.edgeEvents" type="checkbox" />
                    边事件
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.boxSelect" type="checkbox" />
                    框选
                  </label>
                  <div class="graph-form-grid">
                    <label>
                      边最小
                      <input v-model.number="graphEdgeMinWidth" type="number" min="1" max="8" />
                    </label>
                    <label>
                      边最大
                      <input v-model.number="graphEdgeMaxWidth" type="number" min="1" max="12" />
                    </label>
                  </div>
                </div>
              </aside>

              <section class="graph-main-card">
                <div class="graph-toolbar">
                  <div>
                    <h3>知识图谱可视化</h3>
                    <p>
                      {{ graphKeyword || '默认图谱' }} ·
                      {{ visibleGraphNodes.length }} 个实体 ·
                      {{ visibleGraphEdges.length }} 条关系
                    </p>
                  </div>
                  <span class="graph-badge" :class="{ online: graphHealth?.connected }">
                    {{ graphHealth?.connected ? 'Neo4j 已连接' : 'Graph RAG' }}
                  </span>
                </div>

                <div
                  ref="graphCanvasRef"
                  class="graph-visual-canvas"
                  :class="{ selecting: graphSettings.boxSelect }"
                  @wheel.prevent="handleGraphWheel"
                  @mousedown.self="startBoxSelect"
                  @mousemove="handleGraphMouseMove"
                  @mouseup="endGraphPointerAction"
                  @mouseleave="endGraphPointerAction"
                >
                  <svg class="graph-svg" viewBox="0 0 1000 620" role="img" aria-label="知识图谱可视化">
                    <g :transform="graphTransform">
                      <line
                        v-for="edge in visibleGraphEdges"
                        :key="edge.id"
                        :x1="getNodePosition(edge.source).x"
                        :y1="getNodePosition(edge.source).y"
                        :x2="getNodePosition(edge.target).x"
                        :y2="getNodePosition(edge.target).y"
                        :class="['graph-edge-line', { active: isEdgeActive(edge), dimmed: isEdgeDimmed(edge) }]"
                        :stroke-width="getEdgeWidth(edge)"
                        @click.stop="selectEdge(edge)"
                      />
                      <text
                        v-for="edge in edgeLabels"
                        :key="`${edge.id}-label`"
                        class="graph-edge-label"
                        :x="getEdgeLabelPosition(edge).x"
                        :y="getEdgeLabelPosition(edge).y"
                        @click.stop="selectEdge(edge)"
                      >
                        {{ edge.type }}
                      </text>

                      <g
                        v-for="node in visibleGraphNodes"
                        :key="node.id"
                        :class="['graph-node-svg', { active: isNodeSelected(node), related: isNodeRelated(node), dimmed: isNodeDimmed(node) }]"
                        :transform="`translate(${getNodePosition(node.id).x}, ${getNodePosition(node.id).y})`"
                        @mousedown.stop="startNodeDrag(node, $event)"
                        @click.stop="selectNode(node)"
                      >
                        <circle
                          :r="getNodeRadius(node)"
                          :fill="getNodeColor(node)"
                        />
                        <text
                          v-if="graphSettings.showNodeLabels"
                          text-anchor="middle"
                          :y="getNodeRadius(node) + 17"
                        >
                          {{ getShortLabel(node.label) }}
                        </text>
                      </g>
                    </g>
                  </svg>
                  <div v-if="boxSelection.active" class="box-selection" :style="boxSelectionStyle"></div>
                  <div v-if="isGraphBusy" class="graph-loading">正在加载图谱数据...</div>
                  <div v-else-if="visibleGraphNodes.length === 0" class="graph-empty">请输入关键词查询图谱，或点击“查询图谱”加载默认子图。</div>
                </div>
              </section>

              <aside v-if="graphSettings.showPropertyPanel" class="graph-property-panel">
                <div class="panel-title">属性面板</div>
                <div v-if="selectedGraphItem" class="property-card">
                  <div class="property-title">
                    <span>{{ selectedGraphItem.kind === 'node' ? '实体' : '关系' }}</span>
                    <strong>{{ selectedGraphItem.title }}</strong>
                  </div>
                  <div class="property-list">
                    <div
                      v-for="item in selectedProperties"
                      :key="item.key"
                      class="property-row"
                    >
                      <span>{{ item.key }}</span>
                      <b>{{ item.value }}</b>
                    </div>
                  </div>
                </div>
                <div v-else class="graph-empty small">
                  点击一个实体或关系，查看具体属性。
                </div>

                <div v-if="graphHealth && graphSettings.healthCheck" class="relation-card">
                  <h3>健康检查</h3>
                  <p>{{ graphHealth.connected ? 'Neo4j 连接正常' : graphHealth.message }}</p>
                </div>

                <div class="relation-card">
                  <h3>实体类型</h3>
                  <p v-for="item in topNodeLabels" :key="item.name">
                    {{ item.name }}：{{ item.count }}
                  </p>
                </div>

                <div class="relation-card">
                  <h3>关系类型</h3>
                  <p v-for="item in topRelationshipTypes" :key="item.name">
                    {{ item.name }}：{{ item.count }}
                  </p>
                </div>
              </aside>
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
  getKnowledgeGraphHealth,
  getKnowledgeGraphNode,
  getKnowledgeGraphRelationship,
  getKnowledgeGraphStats,
  getKnowledgeDocuments,
  rebuildKnowledgeDocument,
  searchKnowledgeGraph,
  uploadKnowledgeDocument,
  visualizeKnowledgeGraph,
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

const graphHealth = ref(null);
const graphStatsData = ref(null);
const graphSearchResult = ref(null);
const graphData = ref({ nodes: [], edges: [] });
const graphKeyword = ref('');
const graphFilterKeyword = ref('');
const graphDepth = ref(1);
const graphLimit = ref(300);
const graphLayout = ref('circle');
const graphNodeSize = ref(38);
const graphEdgeWidth = ref(2);
const graphEdgeMinWidth = ref(1);
const graphEdgeMaxWidth = ref(6);
const graphZoom = ref(1);
const graphRotation = ref(0);
const isGraphFullscreen = ref(false);
const isLoadingGraph = ref(false);
const isLoadingGraphStats = ref(false);
const isLoadingGraphHealth = ref(false);
const graphError = ref('');
const graphNotice = ref('');
const selectedGraphItem = ref(null);
const selectedTypeColor = ref('#0078d4');
const customTypeColors = ref({});
const draggedNode = ref(null);
const boxSelectedNodeIds = ref(new Set());
const nodePositions = ref({});
const graphCanvasRef = ref(null);
const boxSelection = ref({
  active: false,
  startX: 0,
  startY: 0,
  currentX: 0,
  currentY: 0,
});

const graphSettings = ref({
  healthCheck: true,
  showPropertyPanel: true,
  showSearchBar: true,
  showNodeLabels: true,
  draggableNodes: true,
  showEdgeLabels: true,
  hideUnselectedEdges: false,
  edgeEvents: true,
  boxSelect: true,
});

const colorPalette = [
  '#0078d4',
  '#107c10',
  '#d83b01',
  '#5c2d91',
  '#038387',
  '#c239b3',
  '#8a8886',
  '#e81123',
  '#498205',
  '#8764b8',
  '#00b7c3',
  '#ffaa44',
  '#006666',
  '#b146c2',
  '#4f6bed',
  '#ca5010',
  '#0b6a0b',
  '#881798',
  '#005a9e',
  '#986f0b',
];

const graphStats = computed(() => [
  { label: '实体总数', value: formatNumber(graphStatsData.value?.totalNodes) },
  { label: '关系总数', value: formatNumber(graphStatsData.value?.totalRelationships) },
  { label: '搜索实体', value: formatNumber(graphSearchResult.value?.nodeCount) },
  { label: '搜索关系', value: formatNumber(graphSearchResult.value?.relationshipCount) },
]);

const isKnowledgeBusy = computed(
  () => isLoadingDocuments.value || isUploadingDocument.value || Boolean(operatingDocumentId.value) || isGraphBusy.value
);

const isGraphBusy = computed(
  () => isLoadingGraph.value || isLoadingGraphStats.value || isLoadingGraphHealth.value
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

const visibleGraphNodes = computed(() => {
  const keyword = graphFilterKeyword.value.toLowerCase();
  if (!keyword) {
    return graphData.value.nodes;
  }

  const matchedNodeIds = new Set();
  graphData.value.nodes.forEach((node) => {
    if (matchesGraphText(node, keyword)) {
      matchedNodeIds.add(node.id);
    }
  });
  graphData.value.edges.forEach((edge) => {
    if (matchesGraphText(edge, keyword)) {
      matchedNodeIds.add(edge.source);
      matchedNodeIds.add(edge.target);
    }
  });

  return graphData.value.nodes.filter((node) => matchedNodeIds.has(node.id));
});

const visibleGraphNodeIds = computed(() => new Set(visibleGraphNodes.value.map((node) => node.id)));

const visibleGraphEdges = computed(() => {
  const keyword = graphFilterKeyword.value.toLowerCase();
  return graphData.value.edges.filter((edge) => {
    const hasVisibleEndpoint = visibleGraphNodeIds.value.has(edge.source) && visibleGraphNodeIds.value.has(edge.target);
    if (!hasVisibleEndpoint) {
      return false;
    }
    if (graphSettings.value.hideUnselectedEdges && selectedGraphItem.value && !activeEdgeIds.value.has(edge.id)) {
      return false;
    }
    return !keyword || matchesGraphText(edge, keyword) || visibleGraphNodeIds.value.has(edge.source) || visibleGraphNodeIds.value.has(edge.target);
  });
});

const edgeLabels = computed(() => (graphSettings.value.showEdgeLabels ? visibleGraphEdges.value : []));

const selectedProperties = computed(() => {
  if (!selectedGraphItem.value) {
    return [];
  }
  return Object.entries(selectedGraphItem.value.properties || {}).map(([key, value]) => ({
    key,
    value: formatPropertyValue(value),
  }));
});

const topNodeLabels = computed(() => graphStatsData.value?.nodeLabels?.slice(0, 8) || []);
const topRelationshipTypes = computed(() => graphStatsData.value?.relationshipTypes?.slice(0, 8) || []);

const selectedNodeIds = computed(() => {
  if (!selectedGraphItem.value) {
    return new Set();
  }
  if (selectedGraphItem.value.kind === 'box') {
    return new Set(boxSelectedNodeIds.value);
  }
  if (selectedGraphItem.value.kind === 'node') {
    return new Set([selectedGraphItem.value.id]);
  }
  return new Set([selectedGraphItem.value.source, selectedGraphItem.value.target]);
});

const relatedNodeIds = computed(() => {
  if (!selectedGraphItem.value) {
    return new Set();
  }

  const ids = new Set(selectedNodeIds.value);
  visibleGraphEdges.value.forEach((edge) => {
    if (ids.has(edge.source)) {
      ids.add(edge.target);
    }
    if (ids.has(edge.target)) {
      ids.add(edge.source);
    }
  });
  return ids;
});

const activeEdgeIds = computed(() => {
  if (!selectedGraphItem.value) {
    return new Set();
  }
  if (selectedGraphItem.value.kind === 'edge') {
    return new Set([selectedGraphItem.value.id]);
  }
  return new Set(
    graphData.value.edges
      .filter((edge) => selectedNodeIds.value.has(edge.source) || selectedNodeIds.value.has(edge.target))
      .map((edge) => edge.id)
  );
});

const graphTransform = computed(() => {
  const rotation = graphRotation.value;
  const zoom = graphZoom.value;
  return `translate(500 310) rotate(${rotation}) scale(${zoom}) translate(-500 -310)`;
});

const boxSelectionStyle = computed(() => {
  const left = Math.min(boxSelection.value.startX, boxSelection.value.currentX);
  const top = Math.min(boxSelection.value.startY, boxSelection.value.currentY);
  const width = Math.abs(boxSelection.value.currentX - boxSelection.value.startX);
  const height = Math.abs(boxSelection.value.currentY - boxSelection.value.startY);
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    height: `${height}px`,
  };
});

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
let responseGenerationId = 0;

const canSend = computed(() => inputValue.value.length > 0 && !isStreaming.value);

async function switchView(view) {
  activeView.value = view;
  if (view === 'knowledge') {
    await fetchDocuments();
    if (knowledgeTab.value === 'graph') {
      await loadGraphInitialData();
    }
  }
}

async function switchKnowledgeTab(tab) {
  knowledgeTab.value = tab;
  if (tab === 'documents') {
    await fetchDocuments();
  }
  if (tab === 'graph') {
    await loadGraphInitialData();
  }
}

async function loadGraphInitialData() {
  if (!graphStatsData.value) {
    await loadGraphStats();
  }
  if (graphSettings.value.healthCheck && !graphHealth.value) {
    await loadGraphHealth();
  }
  if (graphData.value.nodes.length === 0) {
    await loadGraphData();
  }
}

async function loadGraphHealth() {
  graphError.value = '';
  isLoadingGraphHealth.value = true;
  try {
    const { data } = await getKnowledgeGraphHealth();
    graphHealth.value = data;
  } catch (error) {
    graphError.value = getErrorMessage(error, 'Neo4j 健康检查失败');
  } finally {
    isLoadingGraphHealth.value = false;
  }
}

async function loadGraphStats() {
  graphError.value = '';
  isLoadingGraphStats.value = true;
  try {
    const { data } = await getKnowledgeGraphStats();
    graphStatsData.value = data;
  } catch (error) {
    graphError.value = getErrorMessage(error, '加载知识图谱统计失败');
  } finally {
    isLoadingGraphStats.value = false;
  }
}

async function loadGraphData() {
  graphError.value = '';
  graphNotice.value = '';
  isLoadingGraph.value = true;
  selectedGraphItem.value = null;

  try {
    const safeDepth = clampNumber(graphDepth.value, 1, 5, 1);
    const safeLimit = clampNumber(graphLimit.value, 1, 1000, 300);
    graphDepth.value = safeDepth;
    graphLimit.value = safeLimit;

    const [searchResponse, visualizeResponse] = await Promise.all([
      searchKnowledgeGraph(graphKeyword.value, safeLimit),
      visualizeKnowledgeGraph(graphKeyword.value, safeDepth, safeLimit),
    ]);

    graphSearchResult.value = searchResponse.data;
    graphData.value = normalizeGraphData(visualizeResponse.data);
    graphFilterKeyword.value = '';
    buildNodePositions();

    if (graphData.value.nodes.length === 0) {
      graphNotice.value = '当前关键词没有查询到可视化图谱结果，可以换一个村名、河流名、站点名或危险区名称。';
    }
  } catch (error) {
    graphError.value = getErrorMessage(error, '加载知识图谱失败');
  } finally {
    isLoadingGraph.value = false;
  }
}

function handleHealthCheckToggle() {
  if (graphSettings.value.healthCheck) {
    loadGraphHealth();
  }
}

function normalizeGraphData(data) {
  return {
    nodes: Array.isArray(data?.nodes) ? data.nodes : [],
    edges: Array.isArray(data?.edges) ? data.edges : [],
  };
}

function buildNodePositions() {
  const nodes = graphData.value.nodes;
  const total = Math.max(nodes.length, 1);
  const positions = { ...nodePositions.value };
  const centerX = 500;
  const centerY = 310;
  const radius = Math.min(260, Math.max(110, 26 * Math.sqrt(total)));

  nodes.forEach((node, index) => {
    if (positions[node.id]) {
      return;
    }

    if (graphLayout.value === 'grid') {
      const columns = Math.ceil(Math.sqrt(total));
      const spacingX = Math.min(150, 820 / Math.max(columns, 1));
      const spacingY = 92;
      const row = Math.floor(index / columns);
      const col = index % columns;
      positions[node.id] = {
        x: 120 + col * spacingX,
        y: 90 + row * spacingY,
      };
      return;
    }

    if (graphLayout.value === 'radial') {
      const typeIndex = getTypeIndex(node.type);
      const layerRadius = 90 + (typeIndex % 4) * 70;
      const angle = (index / total) * Math.PI * 2 + typeIndex * 0.34;
      positions[node.id] = {
        x: centerX + Math.cos(angle) * layerRadius,
        y: centerY + Math.sin(angle) * layerRadius,
      };
      return;
    }

    const angle = (index / total) * Math.PI * 2 - Math.PI / 2;
    positions[node.id] = {
      x: centerX + Math.cos(angle) * radius,
      y: centerY + Math.sin(angle) * radius,
    };
  });

  nodePositions.value = positions;
}

function getNodePosition(nodeId) {
  return nodePositions.value[nodeId] || { x: 500, y: 310 };
}

function getEdgeLabelPosition(edge) {
  const source = getNodePosition(edge.source);
  const target = getNodePosition(edge.target);
  return {
    x: (source.x + target.x) / 2,
    y: (source.y + target.y) / 2 - 6,
  };
}

function getNodeRadius(node) {
  const labelLength = String(node.label || '').length;
  return Math.min(graphNodeSize.value + labelLength * 1.6, 92);
}

function getEdgeWidth(edge) {
  const rank = Number(edge.properties?.rank || 1);
  const base = Number.isFinite(rank) ? Math.min(Math.max(rank, graphEdgeMinWidth.value), graphEdgeMaxWidth.value) : graphEdgeWidth.value;
  return Math.min(Math.max(base, graphEdgeMinWidth.value), graphEdgeMaxWidth.value);
}

function getNodeColor(node) {
  if (customTypeColors.value[node.type]) {
    return customTypeColors.value[node.type];
  }
  return colorPalette[getTypeIndex(node.type) % colorPalette.length];
}

function getTypeIndex(type) {
  const types = [...new Set(graphData.value.nodes.map((node) => node.type || 'Entity'))];
  return Math.max(types.indexOf(type || 'Entity'), 0);
}

function applySelectedTypeColor() {
  if (!selectedGraphItem.value || selectedGraphItem.value.kind !== 'node') {
    return;
  }
  customTypeColors.value = {
    ...customTypeColors.value,
    [selectedGraphItem.value.type]: selectedTypeColor.value,
  };
}

async function selectNode(node) {
  boxSelectedNodeIds.value = new Set();
  selectedGraphItem.value = {
    kind: 'node',
    id: node.id,
    title: node.label,
    type: node.type,
    properties: node.properties,
  };
  selectedTypeColor.value = getNodeColor(node);

  try {
    const { data } = await getKnowledgeGraphNode(node.elementId || node.id);
    selectedGraphItem.value = {
      kind: 'node',
      id: data.id,
      title: data.label,
      type: data.type,
      properties: data.properties || {},
    };
    selectedTypeColor.value = getNodeColor(data);
  } catch {
    // 当前可视化数据里已经有属性，详情接口失败时保持本地属性。
  }
}

async function selectEdge(edge) {
  if (!graphSettings.value.edgeEvents) {
    return;
  }

  boxSelectedNodeIds.value = new Set();
  selectedGraphItem.value = {
    kind: 'edge',
    id: edge.id,
    title: edge.type,
    source: edge.source,
    target: edge.target,
    properties: edge.properties,
  };

  try {
    const { data } = await getKnowledgeGraphRelationship(edge.id);
    selectedGraphItem.value = {
      kind: 'edge',
      id: data.id,
      title: data.type,
      source: data.source,
      target: data.target,
      properties: data.properties || {},
    };
  } catch {
    // 当前可视化数据里已经有属性，详情接口失败时保持本地属性。
  }
}

function isNodeSelected(node) {
  return selectedNodeIds.value.has(node.id);
}

function isNodeRelated(node) {
  return relatedNodeIds.value.has(node.id);
}

function isNodeDimmed(node) {
  return selectedGraphItem.value && !isNodeRelated(node);
}

function isEdgeActive(edge) {
  return activeEdgeIds.value.has(edge.id);
}

function isEdgeDimmed(edge) {
  return Boolean(selectedGraphItem.value) && !isEdgeActive(edge);
}

function zoomGraph(ratio) {
  graphZoom.value = Math.min(Math.max(graphZoom.value * ratio, 0.2), 4);
}

function rotateGraph(deg) {
  graphRotation.value = (graphRotation.value + deg) % 360;
}

function resetGraphView() {
  graphZoom.value = 1;
  graphRotation.value = 0;
  buildNodePositions();
}

function toggleGraphFullscreen() {
  isGraphFullscreen.value = !isGraphFullscreen.value;
}

function handleGraphWheel(event) {
  zoomGraph(event.deltaY < 0 ? 1.08 : 0.92);
}

function startNodeDrag(node, event) {
  if (!graphSettings.value.draggableNodes) {
    return;
  }
  draggedNode.value = {
    id: node.id,
    startX: event.clientX,
    startY: event.clientY,
    origin: getNodePosition(node.id),
  };
}

function handleGraphMouseMove(event) {
  if (draggedNode.value) {
    const scale = graphZoom.value || 1;
    const dx = (event.clientX - draggedNode.value.startX) / scale;
    const dy = (event.clientY - draggedNode.value.startY) / scale;
    nodePositions.value = {
      ...nodePositions.value,
      [draggedNode.value.id]: {
        x: draggedNode.value.origin.x + dx,
        y: draggedNode.value.origin.y + dy,
      },
    };
    return;
  }

  if (boxSelection.value.active) {
    const rect = graphCanvasRef.value?.getBoundingClientRect();
    if (!rect) {
      return;
    }
    boxSelection.value.currentX = event.clientX - rect.left;
    boxSelection.value.currentY = event.clientY - rect.top;
  }
}

function endGraphPointerAction() {
  draggedNode.value = null;
  if (boxSelection.value.active) {
    finishBoxSelect();
  }
  boxSelection.value.active = false;
}

function startBoxSelect(event) {
  if (!graphSettings.value.boxSelect) {
    return;
  }
  const rect = graphCanvasRef.value?.getBoundingClientRect();
  if (!rect) {
    return;
  }
  const x = event.clientX - rect.left;
  const y = event.clientY - rect.top;
  boxSelection.value = {
    active: true,
    startX: x,
    startY: y,
    currentX: x,
    currentY: y,
  };
}

function finishBoxSelect() {
  const rect = graphCanvasRef.value?.getBoundingClientRect();
  if (!rect) {
    return;
  }

  const left = Math.min(boxSelection.value.startX, boxSelection.value.currentX);
  const right = Math.max(boxSelection.value.startX, boxSelection.value.currentX);
  const top = Math.min(boxSelection.value.startY, boxSelection.value.currentY);
  const bottom = Math.max(boxSelection.value.startY, boxSelection.value.currentY);
  const width = Math.max(rect.width, 1);
  const height = Math.max(rect.height, 1);

  const selectedIds = visibleGraphNodes.value
    .filter((node) => {
      const position = getNodePosition(node.id);
      const screenX = (position.x / 1000) * width;
      const screenY = (position.y / 620) * height;
      return screenX >= left && screenX <= right && screenY >= top && screenY <= bottom;
    })
    .map((node) => node.id);

  boxSelectedNodeIds.value = new Set(selectedIds);
  if (selectedIds.length > 0) {
    selectedGraphItem.value = {
      kind: 'box',
      title: `已框选 ${selectedIds.length} 个实体`,
      properties: {
        selectedCount: selectedIds.length,
        selectedIds: selectedIds.join('、'),
      },
    };
  }
}

function matchesGraphText(item, keyword) {
  const text = JSON.stringify(item || {}).toLowerCase();
  return text.includes(keyword);
}

function getShortLabel(label) {
  const text = String(label || '-');
  return text.length > 10 ? `${text.slice(0, 10)}...` : text;
}

function formatNumber(value) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  return Number(value).toLocaleString('zh-CN');
}

function clampNumber(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  return Math.min(Math.max(number, min), max);
}

function formatPropertyValue(value) {
  if (value === null || value === undefined) {
    return '-';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
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
  const files = Array.from(event.target.files || []);
  event.target.value = '';
  if (files.length === 0) {
    return;
  }

  knowledgeError.value = '';
  knowledgeNotice.value = '';
  isUploadingDocument.value = true;

  let successCount = 0;
  const failedFiles = [];

  try {
    for (let index = 0; index < files.length; index++) {
      const file = files[index];
      knowledgeNotice.value = `正在上传 ${index + 1}/${files.length}：${file.name}`;

      try {
        await uploadKnowledgeDocument(file, true);
        successCount++;
      } catch (error) {
        failedFiles.push({
          name: file.name,
          message: getErrorMessage(error, '上传失败'),
        });
      }
    }

    await fetchDocuments();

    if (failedFiles.length > 0) {
      knowledgeError.value = `成功上传 ${successCount} 个，失败 ${failedFiles.length} 个：${failedFiles
        .map((item) => item.name)
        .join('、')}`;
      knowledgeNotice.value = '';
    } else {
      setKnowledgeNotice(
        files.length === 1 ? `文档“${files[0].name}”已入库` : `已成功上传 ${successCount} 个 PDF 文档`
      );
    }
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

async function pushTypedAssistantMessage(fullContent, generationId) {
  if (generationId !== responseGenerationId) {
    return;
  }

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
    if (generationId !== responseGenerationId) {
      break;
    }

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

function enqueueTypedAssistantMessage(content, generationId) {
  typingQueue = typingQueue
    .catch(() => undefined)
    .then(() => pushTypedAssistantMessage(content, generationId));
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

function stopGeneration() {
  responseGenerationId++;
  closeEventSource();
  typingQueue = Promise.resolve();
  messages.value.forEach((message) => {
    message.isTyping = false;
  });
  for (let index = messages.value.length - 1; index >= 0; index--) {
    const message = messages.value[index];
    if (message.role === 'assistant' && message.content === CONNECTING_MESSAGE) {
      message.content = '已停止生成';
      break;
    }
  }
  isStreaming.value = false;
}

function sendMessage() {
  if (!canSend.value) {
    return;
  }

  responseGenerationId++;
  const currentGenerationId = responseGenerationId;
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
    if (currentGenerationId !== responseGenerationId) {
      return;
    }

    const content = event.data?.trim();
    if (!content) {
      return;
    }

    if (!hasReceivedData) {
      hasReceivedData = true;
      removeMessage(loadingMessageId);
    }

    enqueueTypedAssistantMessage(content, currentGenerationId);
  };

  eventSource.onerror = async () => {
    if (currentGenerationId !== responseGenerationId) {
      return;
    }

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
