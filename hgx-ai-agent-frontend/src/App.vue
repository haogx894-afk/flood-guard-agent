<template>
  <main class="app-root" :class="{ 'auth-root': !currentUser }">
    <section v-if="isAuthChecking" class="auth-screen" aria-label="正在检查登录状态">
      <div class="auth-shell fluent-card">
        <img class="auth-logo" src="/flood-logo.png" alt="网站 logo" />
        <p class="auth-kicker">Flood Intelligence Workspace</p>
        <h1>正在进入北京市山洪防御空间辅助决策智能体</h1>
        <p class="auth-copy">正在检查登录状态，请稍候。</p>
      </div>
    </section>

    <section v-else-if="!currentUser" class="auth-screen" aria-label="用户登录注册">
      <div class="auth-layout">
        <section class="auth-hero">
          <img class="auth-hero-logo" src="/flood-logo.png" alt="网站 logo" />
          <p>Graph RAG · Spatial Decision Agent</p>
          <h1>北京市山洪防御空间辅助决策智能体</h1>
          <span>融合 RAG 知识库、Neo4j 知识图谱与智能体工具调用，辅助开展预案查询、空间研判和风险对象排查。</span>
        </section>

        <section class="auth-card fluent-card">
          <div class="auth-tabs" role="tablist" aria-label="认证方式">
            <button
              type="button"
              :class="{ active: authMode === 'login' }"
              @click="switchAuthMode('login')"
            >
              登录
            </button>
            <button
              type="button"
              :class="{ active: authMode === 'register' }"
              @click="switchAuthMode('register')"
            >
              注册
            </button>
          </div>

          <div class="auth-card-heading">
            <p>{{ authMode === 'login' ? 'Welcome Back' : 'Create Account' }}</p>
            <h2>{{ authMode === 'login' ? '登录工作台' : '创建新账号' }}</h2>
          </div>

          <div v-if="authError" class="message-banner error">
            {{ authError }}
          </div>
          <div v-if="authNotice" class="message-banner">
            {{ authNotice }}
          </div>

          <form class="auth-form" @submit.prevent="submitAuth">
            <label>
              账号
              <input
                v-model.trim="authForm.account"
                type="text"
                autocomplete="username"
                placeholder="请输入账号，不少于 4 位"
              />
            </label>
            <label>
              密码
              <input
                v-model.trim="authForm.password"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码，不少于 8 位"
              />
            </label>
            <label v-if="authMode === 'register'">
              确认密码
              <input
                v-model.trim="authForm.confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="请再次输入密码"
              />
            </label>

            <button type="submit" class="auth-submit" :disabled="isAuthSubmitting">
              {{ authSubmitText }}
            </button>
          </form>
        </section>
      </div>
    </section>

    <section v-else class="app-frame" aria-label="北京市山洪防御空间辅助决策智能体">
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
            <button
              v-if="canManageUsers"
              type="button"
              :class="{ active: activeView === 'users' }"
              @click="switchView('users')"
            >
              用户管理
            </button>
          </nav>

          <div class="current-user-chip">
            <span>{{ currentUser.userAccount || currentUser.username || '用户' }}</span>
            <strong>{{ canManageUsers ? '管理员' : '普通用户' }}</strong>
          </div>
          <button type="button" class="logout-button" @click="logoutCurrentUser">
            退出
          </button>

          <div class="runtime-status" :class="{ active: isStreaming || isKnowledgeBusy }">
            <span aria-hidden="true"></span>
            {{ isStreaming ? '响应中' : isKnowledgeBusy ? '处理中' : '在线' }}
          </div>
        </div>
      </header>

      <div class="workspace">
        <section v-if="activeView === 'chat'" class="chat-card fluent-card">
          <aside class="conversation-sidebar" aria-label="历史对话">
            <div class="conversation-sidebar-header">
              <div>
                <p class="section-kicker">Conversation History</p>
                <h2>历史记录</h2>
              </div>
              <button
                type="button"
                class="new-conversation-button"
                :disabled="isStreaming || isLoadingConversations"
                @click="createNewConversation"
              >
                新建
              </button>
            </div>

            <div v-if="conversationError" class="conversation-notice error">
              {{ conversationError }}
            </div>

            <div class="conversation-list">
              <div v-if="isLoadingConversations" class="conversation-empty">
                正在加载历史记录...
              </div>
              <div v-else-if="conversationRows.length === 0" class="conversation-empty">
                暂无历史对话，点击新建后开始提问。
              </div>
              <template v-else>
                <button
                  v-for="item in conversationRows"
                  :key="item.id"
                  type="button"
                  class="conversation-item"
                  :class="{ active: item.id === currentConversationId }"
                  @click="selectConversation(item.id)"
                >
                  <span class="conversation-title">{{ item.title }}</span>
                  <span class="conversation-preview">{{ item.lastMessage || '还没有消息' }}</span>
                  <span class="conversation-footer">
                    <em>{{ item.messageCount }} 条消息</em>
                    <time>{{ item.updatedAt }}</time>
                  </span>
                  <span
                    class="conversation-delete"
                    title="删除对话"
                    @click.stop="deleteConversationItem(item)"
                  >
                    删除
                  </span>
                </button>
              </template>
            </div>
          </aside>

          <section class="chat-main-pane">
            <div class="chat-card-header">
              <div>
                <p class="section-kicker">AI 防汛管家</p>
                <h2>{{ currentConversationTitle }}</h2>
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
            <div v-if="isLoadingConversationMessages" class="chat-loading-row">
              正在打开历史对话...
            </div>
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
        </section>

        <section v-else-if="activeView === 'knowledge'" class="knowledge-card fluent-card">
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
                    <button
                      v-if="canManageUsers"
                      type="button"
                      class="danger"
                      :disabled="isKnowledgeBusy"
                      @click="deleteDocument(doc.id)"
                    >
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
              <button
                v-for="item in graphStats"
                :key="item.key"
                type="button"
                class="stat-card stat-card-button"
                @click="openGraphStatDetail(item.key)"
              >
                <span>{{ item.value }}</span>
                <p>{{ item.label }}</p>
              </button>
            </div>

            <div class="graph-shell">
              <button
                v-if="isGraphControlCollapsed"
                type="button"
                class="floating-tab left"
                @click="isGraphControlCollapsed = false"
              >
                图谱查询
              </button>
              <button
                v-if="isGraphPropertyCollapsed"
                type="button"
                class="floating-tab right"
                @click="isGraphPropertyCollapsed = false"
              >
                属性面板
              </button>
              <button
                v-if="isGraphLegendCollapsed"
                type="button"
                class="floating-tab legend-tab"
                @click="isGraphLegendCollapsed = false"
              >
                图例
              </button>

              <aside v-if="graphStatDetail.visible" class="graph-stat-detail-panel">
                <div class="floating-panel-header compact">
                  <div>
                    <div class="panel-title">{{ graphStatDetail.title }}</div>
                    <p>{{ graphStatDetail.summary }}</p>
                  </div>
                  <button type="button" @click="closeGraphStatDetail">关闭</button>
                </div>
                <div class="stat-detail-list">
                  <div
                    v-for="item in graphStatDetailItems"
                    :key="item.name"
                    class="stat-detail-row"
                  >
                    <span>{{ item.name }}</span>
                    <b>{{ formatNumber(item.count) }}</b>
                  </div>
                </div>
              </aside>

              <aside
                v-if="!isGraphControlCollapsed"
                class="graph-control-panel floating-panel"
                :style="floatingPanelStyle(graphControlPanelPosition)"
              >
                <div class="floating-panel-header" @mousedown="startFloatingPanelDrag('control', $event)">
                  <div class="panel-title">图谱查询</div>
                  <button type="button" @mousedown.stop @click.stop="isGraphControlCollapsed = true">收起</button>
                </div>
                <div class="panel-block">
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
                      <input v-model.number="graphNodeSize" type="range" min="22" max="86" @input="rebuildNodePositions" />
                    </label>
                    <label>
                      边粗细
                      <input v-model.number="graphEdgeWidth" type="range" :min="graphEdgeMinWidth" :max="graphEdgeMaxWidth" />
                    </label>
                  </div>
                  <label>
                    图布局
                    <select v-model="graphLayout" @change="rebuildNodePositions">
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
                    <input v-model="graphSettings.highlightAll" type="checkbox" />
                    全部高亮 / 选中关联高亮
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.edgeEvents" type="checkbox" />
                    边事件
                  </label>
                  <label class="graph-check">
                    <input v-model="graphSettings.boxSelect" type="checkbox" />
                    框选（按住 Shift 拖动）
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
                  :class="{ selecting: boxSelection.active, panning: graphPan.active }"
                  @wheel.prevent="handleGraphWheel"
                  @mousedown="startGraphCanvasPointer"
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
                        @mousedown.stop
                        @click.stop="selectEdge(edge)"
                      />
                      <text
                        v-for="edge in edgeLabels"
                        :key="`${edge.id}-label`"
                        class="graph-edge-label"
                        :x="getEdgeLabelPosition(edge).x"
                        :y="getEdgeLabelPosition(edge).y"
                        @mousedown.stop
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
                          {{ getShortLabel(getNodeDisplayName(node)) }}
                        </text>
                      </g>
                    </g>
                  </svg>
                  <div v-if="boxSelection.active" class="box-selection" :style="boxSelectionStyle"></div>
                  <div v-if="isGraphBusy" class="graph-loading">正在加载图谱数据...</div>
                  <div v-else-if="visibleGraphNodes.length === 0" class="graph-empty">请输入关键词查询图谱，或点击“查询图谱”加载默认子图。</div>
                </div>
              </section>

              <aside v-if="!isGraphLegendCollapsed" class="graph-legend-panel">
                <div class="legend-header">
                  <strong>图例</strong>
                  <button type="button" @click="isGraphLegendCollapsed = true">隐藏</button>
                </div>
                <div class="legend-list">
                  <div
                    v-for="item in graphLegendItems"
                    :key="item.type"
                    class="legend-item"
                  >
                    <i :style="{ background: item.color }"></i>
                    <span>{{ item.type }}</span>
                  </div>
                </div>
              </aside>

              <aside
                v-if="graphSettings.showPropertyPanel && !isGraphPropertyCollapsed"
                class="graph-property-panel floating-panel"
                :style="floatingPanelStyle(graphPropertyPanelPosition)"
              >
                <div class="floating-panel-header" @mousedown="startFloatingPanelDrag('property', $event)">
                  <div class="panel-title">属性面板</div>
                  <button type="button" @mousedown.stop @click.stop="isGraphPropertyCollapsed = true">收起</button>
                </div>
                <div v-if="selectedGraphItem" class="property-card">
                  <div class="property-title">
                    <span>{{ getSelectedItemKindText(selectedGraphItem.kind) }}</span>
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
              </aside>

            </div>
          </section>
        </section>

        <section v-else class="user-admin-card fluent-card">
          <div class="user-admin-header">
            <div>
              <p class="section-kicker">User Administration</p>
              <h2>用户管理</h2>
            </div>
            <div class="user-admin-actions">
              <input
                v-model.trim="userSearchKeyword"
                type="text"
                placeholder="按用户名搜索"
                @keydown.enter.prevent="fetchUsers"
              />
              <button type="button" :disabled="isUserBusy" @click="fetchUsers">
                搜索 / 刷新
              </button>
            </div>
          </div>

          <section class="user-admin-body">
            <div v-if="userError" class="message-banner error">
              {{ userError }}
            </div>
            <div v-if="userNotice" class="message-banner">
              {{ userNotice }}
            </div>

            <div class="stat-grid user-stat-grid">
              <div v-for="item in userStats" :key="item.label" class="stat-card">
                <span>{{ item.value }}</span>
                <p>{{ item.label }}</p>
              </div>
            </div>

            <div class="table-card">
              <div class="table-header">
                <div>
                  <h3>系统用户</h3>
                  <p>管理员可以查询用户并删除普通账号。</p>
                </div>
              </div>

              <div class="user-table">
                <div class="user-row user-head">
                  <span>ID</span>
                  <span>账号</span>
                  <span>用户名</span>
                  <span>角色</span>
                  <span>状态</span>
                  <span>创建时间</span>
                  <span>操作</span>
                </div>

                <div v-if="isLoadingUsers" class="empty-row">
                  正在加载用户列表...
                </div>
                <div v-else-if="userRows.length === 0" class="empty-row">
                  暂无用户数据。
                </div>

                <template v-else>
                  <div
                    v-for="user in userRows"
                    :key="user.id"
                    class="user-row"
                  >
                    <span>{{ user.id }}</span>
                    <span class="doc-name">{{ user.userAccount }}</span>
                    <span>{{ user.username }}</span>
                    <span>
                      <em :class="['role-pill', user.userRole === 1 ? 'admin' : 'normal']">
                        {{ getRoleText(user.userRole) }}
                      </em>
                    </span>
                    <span>{{ user.userStatus === 0 ? '正常' : '禁用' }}</span>
                    <span>{{ formatDateTime(user.createTime) }}</span>
                    <span class="row-actions">
                      <button
                        type="button"
                        class="danger"
                        :disabled="isUserBusy || user.id === currentUser.id"
                        @click="deleteUserAccount(user)"
                      >
                        {{ user.id === currentUser.id ? '当前用户' : '删除' }}
                      </button>
                    </span>
                  </div>
                </template>
              </div>
            </div>
          </section>
        </section>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import ChatMessage from './components/ChatMessage.vue';
import {
  buildManusSseUrl,
  createChatConversation,
  deleteChatConversation,
  deleteUser,
  deleteKnowledgeDocument,
  getCurrentUser,
  getChatConversations,
  getChatMessages,
  getKnowledgeGraphHealth,
  getKnowledgeGraphNode,
  getKnowledgeGraphRelationship,
  getKnowledgeGraphStats,
  getKnowledgeDocuments,
  loginUser,
  logoutUser,
  rebuildKnowledgeDocument,
  registerUser,
  searchKnowledgeGraph,
  searchUsers,
  uploadKnowledgeDocument,
  visualizeKnowledgeGraph,
} from './services/api';

const CONNECTING_MESSAGE = '正在连接防汛智能体...';
const AUTO_SCROLL_THRESHOLD = 20;
const openingMessage = '您好！我是您的专属防汛管家。在制定方案前，我需要先了解您的问题。请输入您的问题？';

const currentUser = ref(null);
const isAuthChecking = ref(true);
const isAuthSubmitting = ref(false);
const authMode = ref('login');
const authError = ref('');
const authNotice = ref('');
const authForm = ref({
  account: '',
  password: '',
  confirmPassword: '',
});

const userSearchKeyword = ref('');
const users = ref([]);
const isLoadingUsers = ref(false);
const deletingUserId = ref('');
const userError = ref('');
const userNotice = ref('');

const conversations = ref([]);
const currentConversationId = ref('');
const isLoadingConversations = ref(false);
const isLoadingConversationMessages = ref(false);
const conversationError = ref('');

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
const graphLimit = ref(20);
const graphLayout = ref('grid');
const graphNodeSize = ref(24);
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
const isGraphControlCollapsed = ref(false);
const isGraphPropertyCollapsed = ref(false);
const isGraphLegendCollapsed = ref(false);
const graphStatDetail = ref({
  visible: false,
  key: '',
  title: '',
  summary: '',
});
const graphControlPanelPosition = ref({ x: 16, y: 16 });
const graphPropertyPanelPosition = ref({ x: 0, y: 16, right: 16 });
const floatingPanelDrag = ref(null);
const graphPan = ref({
  active: false,
  x: 0,
  y: 0,
  startX: 0,
  startY: 0,
  originX: 0,
  originY: 0,
});
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
  highlightAll: true,
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
  { key: 'allNodes', label: '实体总数', value: formatNumber(graphStatsData.value?.totalNodes) },
  { key: 'allRelationships', label: '关系总数', value: formatNumber(graphStatsData.value?.totalRelationships) },
  { key: 'searchNodes', label: '搜索实体', value: formatNumber(graphSearchResult.value?.nodeCount) },
  { key: 'searchRelationships', label: '搜索关系', value: formatNumber(graphSearchResult.value?.relationshipCount) },
]);

const canManageUsers = computed(() => currentUser.value?.userRole === 1);

const authSubmitText = computed(() => {
  if (isAuthSubmitting.value) {
    return authMode.value === 'login' ? '正在登录...' : '正在注册...';
  }
  return authMode.value === 'login' ? '进入工作台' : '注册并进入';
});

const isUserBusy = computed(() => isLoadingUsers.value || Boolean(deletingUserId.value));

const userRows = computed(() =>
  users.value.map((user) => ({
    id: user.id,
    username: user.username || '-',
    userAccount: user.userAccount || '-',
    userRole: user.userRole ?? 0,
    userStatus: user.userStatus ?? 0,
    createTime: user.createTime,
  }))
);

const userStats = computed(() => {
  const adminCount = users.value.filter((user) => user.userRole === 1).length;
  return [
    { label: '当前用户', value: currentUser.value?.userAccount || '-' },
    { label: '查询结果', value: users.value.length },
    { label: '管理员', value: adminCount },
    { label: '普通用户', value: Math.max(users.value.length - adminCount, 0) },
  ];
});

const conversationRows = computed(() =>
  conversations.value.map((item) => ({
    id: item.id,
    title: item.title || '新对话',
    lastMessage: item.lastMessage || '',
    messageCount: item.messageCount ?? 0,
    updatedAt: formatFriendlyTime(item.updatedAt || item.createdAt),
  }))
);

const currentConversation = computed(() =>
  conversations.value.find((item) => item.id === currentConversationId.value)
);

const currentConversationTitle = computed(() => currentConversation.value?.title || '山洪防御空间辅助决策会话');

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

const graphLegendItems = computed(() => {
  const types = [...new Set(visibleGraphNodes.value.map((node) => node.type || 'Entity'))];
  return types.map((type) => ({
    type,
    color: customTypeColors.value[type] || getTypeColor(type),
  }));
});

const selectedProperties = computed(() => {
  if (!selectedGraphItem.value) {
    return [];
  }
  return Object.entries(selectedGraphItem.value.properties || {}).map(([key, value]) => ({
    key,
    value: formatPropertyValue(value),
  }));
});

const graphStatDetailItems = computed(() => getGraphStatItems(graphStatDetail.value.key));

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
  return `translate(${graphPan.value.x} ${graphPan.value.y}) translate(500 310) rotate(${rotation}) scale(${zoom}) translate(-500 -310)`;
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

function createOpeningMessages() {
  return [
    {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: openingMessage,
      isTyping: false,
    },
  ];
}

const messages = ref(createOpeningMessages());

const inputValue = ref('');
const isStreaming = ref(false);
const shouldAutoScroll = ref(true);
const messagePanelRef = ref(null);
const typingTimers = new Set();
let eventSource = null;
let typingQueue = Promise.resolve();
let responseGenerationId = 0;

const canSend = computed(() => inputValue.value.length > 0 && !isStreaming.value && !isLoadingConversationMessages.value);

onMounted(async () => {
  await loadCurrentUser();
  if (currentUser.value) {
    await fetchConversations({ selectLatest: true });
    scrollToBottom({ behavior: 'auto', force: true });
  }
});

async function loadCurrentUser() {
  isAuthChecking.value = true;
  authError.value = '';
  try {
    const { data } = await getCurrentUser();
    currentUser.value = data || null;
  } catch (error) {
    currentUser.value = null;
    if (error?.response?.status && error.response.status !== 401) {
      authError.value = getErrorMessage(error, '检查登录状态失败，请稍后重试');
    }
  } finally {
    isAuthChecking.value = false;
  }
}

function switchAuthMode(mode) {
  authMode.value = mode;
  authError.value = '';
  authNotice.value = '';
}

function validateAuthForm() {
  if (authForm.value.account.length < 4) {
    authError.value = '账号不能少于 4 位';
    return false;
  }
  if (authForm.value.password.length < 8) {
    authError.value = '密码不能少于 8 位';
    return false;
  }
  if (authMode.value === 'register' && authForm.value.password !== authForm.value.confirmPassword) {
    authError.value = '两次输入的密码不一致';
    return false;
  }
  return true;
}

async function submitAuth() {
  authError.value = '';
  authNotice.value = '';
  if (!validateAuthForm()) {
    return;
  }

  isAuthSubmitting.value = true;
  try {
    if (authMode.value === 'register') {
      const registerResponse = await registerUser(
        authForm.value.account,
        authForm.value.password,
        authForm.value.confirmPassword
      );
      if (!registerResponse.data || Number(registerResponse.data) <= 0) {
        authError.value = '注册失败，账号可能已存在或参数不符合要求';
        return;
      }
    }

    const { data } = await loginUser(authForm.value.account, authForm.value.password);
    if (!data || !data.id) {
      authError.value = authMode.value === 'login' ? '登录失败，请检查账号或密码' : '注册成功，但自动登录失败，请重新登录';
      return;
    }

    currentUser.value = data;
    authNotice.value = '';
    authForm.value.password = '';
    authForm.value.confirmPassword = '';
    activeView.value = 'chat';
    await fetchConversations({ selectLatest: true });
    shouldAutoScroll.value = true;
    await nextTick();
    scrollToBottom({ behavior: 'auto', force: true });
  } catch (error) {
    authError.value = getErrorMessage(error, authMode.value === 'login' ? '登录失败' : '注册失败');
  } finally {
    isAuthSubmitting.value = false;
  }
}

async function logoutCurrentUser() {
  closeEventSource();
  stopGeneration();
  try {
    await logoutUser();
  } catch {
    // 即使后端退出接口失败，也清理前端登录态，避免用户卡在页面里。
  }
  currentUser.value = null;
  activeView.value = 'chat';
  authMode.value = 'login';
  authError.value = '';
  authNotice.value = '已退出登录';
  users.value = [];
  conversations.value = [];
  currentConversationId.value = '';
  messages.value = createOpeningMessages();
}

function handleUnauthorized(error) {
  if (error?.response?.status !== 401) {
    return false;
  }
  closeEventSource();
  currentUser.value = null;
  activeView.value = 'chat';
  authMode.value = 'login';
  authError.value = '登录状态已过期，请重新登录';
  conversations.value = [];
  currentConversationId.value = '';
  messages.value = createOpeningMessages();
  return true;
}

async function fetchConversations({ selectLatest = false } = {}) {
  if (!currentUser.value) {
    return;
  }

  conversationError.value = '';
  isLoadingConversations.value = true;
  try {
    const { data } = await getChatConversations();
    conversations.value = Array.isArray(data) ? data : [];

    if (selectLatest && conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id, { silent: true });
      return;
    }

    if (currentConversationId.value
        && !conversations.value.some((item) => item.id === currentConversationId.value)) {
      currentConversationId.value = '';
      messages.value = createOpeningMessages();
    }
  } catch (error) {
    if (!handleUnauthorized(error)) {
      conversationError.value = getErrorMessage(error, '加载历史记录失败');
    }
  } finally {
    isLoadingConversations.value = false;
  }
}

async function createNewConversation() {
  if (isStreaming.value) {
    return;
  }

  conversationError.value = '';
  try {
    const { data } = await createChatConversation('新对话');
    if (data?.id) {
      conversations.value = [data, ...conversations.value.filter((item) => item.id !== data.id)];
      currentConversationId.value = data.id;
      messages.value = createOpeningMessages();
      inputValue.value = '';
      shouldAutoScroll.value = true;
      await nextTick();
      scrollToBottom({ behavior: 'auto', force: true });
    }
  } catch (error) {
    if (!handleUnauthorized(error)) {
      conversationError.value = getErrorMessage(error, '新建对话失败');
    }
  }
}

async function ensureActiveConversation(firstMessage) {
  if (currentConversationId.value) {
    return currentConversationId.value;
  }

  const { data } = await createChatConversation(buildConversationTitle(firstMessage));
  if (!data?.id) {
    throw new Error('新建对话失败');
  }
  conversations.value = [data, ...conversations.value.filter((item) => item.id !== data.id)];
  currentConversationId.value = data.id;
  return data.id;
}

async function selectConversation(conversationId, { silent = false } = {}) {
  if (!conversationId || (isStreaming.value && !silent)) {
    return;
  }

  conversationError.value = '';
  isLoadingConversationMessages.value = true;
  try {
    const { data } = await getChatMessages(conversationId);
    currentConversationId.value = conversationId;
    messages.value = normalizeHistoryMessages(data);
    shouldAutoScroll.value = true;
    await nextTick();
    scrollToBottom({ behavior: 'auto', force: true });
  } catch (error) {
    if (!handleUnauthorized(error)) {
      conversationError.value = getErrorMessage(error, '加载对话消息失败');
    }
  } finally {
    isLoadingConversationMessages.value = false;
  }
}

async function deleteConversationItem(item) {
  if (!item?.id || isStreaming.value) {
    return;
  }

  const confirmed = window.confirm(`确认删除对话「${item.title || '新对话'}」吗？`);
  if (!confirmed) {
    return;
  }

  conversationError.value = '';
  try {
    const { data } = await deleteChatConversation(item.id);
    if (!data) {
      conversationError.value = '删除对话失败';
      return;
    }
    conversations.value = conversations.value.filter((conversation) => conversation.id !== item.id);
    if (currentConversationId.value === item.id) {
      currentConversationId.value = '';
      messages.value = createOpeningMessages();
    }
  } catch (error) {
    if (!handleUnauthorized(error)) {
      conversationError.value = getErrorMessage(error, '删除对话失败');
    }
  }
}

async function switchView(view) {
  if (view === 'users' && !canManageUsers.value) {
    return;
  }
  activeView.value = view;
  if (view === 'chat') {
    await fetchConversations();
    shouldAutoScroll.value = true;
    scrollToBottom({ behavior: 'auto', force: true });
    return;
  }
  if (view === 'users') {
    await fetchUsers();
    return;
  }
  if (view === 'knowledge') {
    await fetchDocuments();
    if (knowledgeTab.value === 'graph') {
      await loadGraphInitialData();
    }
  }
}

async function fetchUsers() {
  if (!canManageUsers.value) {
    userError.value = '只有管理员可以管理用户';
    return;
  }
  userError.value = '';
  isLoadingUsers.value = true;
  try {
    const { data } = await searchUsers(userSearchKeyword.value);
    users.value = Array.isArray(data) ? data : [];
  } catch (error) {
    if (!handleUnauthorized(error)) {
      userError.value = getErrorMessage(error, '加载用户列表失败');
    }
  } finally {
    isLoadingUsers.value = false;
  }
}

async function deleteUserAccount(user) {
  if (!user?.id || user.id === currentUser.value?.id) {
    return;
  }

  const confirmed = window.confirm(`确认删除账号「${user.userAccount}」吗？删除后该用户将无法登录。`);
  if (!confirmed) {
    return;
  }

  userError.value = '';
  userNotice.value = '';
  deletingUserId.value = user.id;
  try {
    const { data } = await deleteUser(user.id);
    if (!data) {
      userError.value = '删除失败，请确认当前账号是否拥有管理员权限';
      return;
    }
    setUserNotice(`已删除账号「${user.userAccount}」`);
    await fetchUsers();
  } catch (error) {
    if (!handleUnauthorized(error)) {
      userError.value = getErrorMessage(error, '删除用户失败');
    }
  } finally {
    deletingUserId.value = '';
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
    if (!handleUnauthorized(error)) {
      graphError.value = getErrorMessage(error, 'Neo4j 健康检查失败');
    }
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
    if (!handleUnauthorized(error)) {
      graphError.value = getErrorMessage(error, '加载知识图谱统计失败');
    }
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
    const safeLimit = clampNumber(graphLimit.value, 1, 1000, 20);
    graphDepth.value = safeDepth;
    graphLimit.value = safeLimit;

    const keyword = graphKeyword.value;
    const visualizeResponse = await visualizeKnowledgeGraph(keyword, safeDepth, safeLimit);
    graphData.value = normalizeGraphData(visualizeResponse.data);
    if (keyword) {
      const searchResponse = await searchKnowledgeGraph(keyword, safeLimit);
      graphSearchResult.value = searchResponse.data;
    } else {
      graphSearchResult.value = {
        keyword: '',
        nodeCount: graphData.value.nodes.length,
        relationshipCount: graphData.value.edges.length,
        nodes: graphData.value.nodes,
        edges: graphData.value.edges,
      };
    }
    graphFilterKeyword.value = '';
    nodePositions.value = {};
    buildNodePositions();

    if (graphData.value.nodes.length === 0) {
      graphNotice.value = '当前关键词没有查询到可视化图谱结果，可以换一个村名、河流名、站点名或危险区名称。';
    }
  } catch (error) {
    if (!handleUnauthorized(error)) {
      graphError.value = getErrorMessage(error, '加载知识图谱失败');
    }
  } finally {
    isLoadingGraph.value = false;
  }
}

function handleHealthCheckToggle() {
  if (graphSettings.value.healthCheck) {
    loadGraphHealth();
  }
}

function openGraphStatDetail(key) {
  const titleMap = {
    allNodes: '实体类型统计',
    allRelationships: '关系类型统计',
    searchNodes: '当前搜索实体统计',
    searchRelationships: '当前搜索关系统计',
  };
  const items = getGraphStatItems(key);
  graphStatDetail.value = {
    visible: true,
    key,
    title: titleMap[key] || '图谱统计',
    summary: `共 ${items.length} 种类型`,
  };
}

function closeGraphStatDetail() {
  graphStatDetail.value = {
    visible: false,
    key: '',
    title: '',
    summary: '',
  };
}

function getGraphStatItems(key) {
  if (key === 'allNodes') {
    return normalizeCountItems(graphStatsData.value?.nodeLabels);
  }
  if (key === 'allRelationships') {
    return normalizeCountItems(graphStatsData.value?.relationshipTypes);
  }
  if (key === 'searchNodes') {
    return countGraphItems(graphSearchResult.value?.nodes || graphData.value.nodes, (node) => node.type || 'Entity');
  }
  if (key === 'searchRelationships') {
    return countGraphItems(graphSearchResult.value?.edges || graphData.value.edges, (edge) => edge.type || '关系');
  }
  return [];
}

function normalizeCountItems(items = []) {
  return items
    .map((item) => ({
      name: item.name || '未命名',
      count: Number(item.count || 0),
    }))
    .sort((a, b) => b.count - a.count);
}

function countGraphItems(items = [], getName) {
  const countMap = new Map();
  items.forEach((item) => {
    const name = getName(item) || '未命名';
    countMap.set(name, (countMap.get(name) || 0) + 1);
  });
  return [...countMap.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count);
}

function normalizeGraphData(data) {
  return {
    nodes: Array.isArray(data?.nodes) ? data.nodes : [],
    edges: Array.isArray(data?.edges) ? data.edges : [],
  };
}

function buildNodePositions({ preserveExisting = false } = {}) {
  const nodes = graphData.value.nodes;
  const total = Math.max(nodes.length, 1);
  const positions = preserveExisting ? { ...nodePositions.value } : {};
  const centerX = 500;
  const centerY = 310;
  const radius = Math.min(285, Math.max(130, 34 * Math.sqrt(total)));

  nodes.forEach((node, index) => {
    if (preserveExisting && positions[node.id]) {
      return;
    }

    if (graphLayout.value === 'grid') {
      const columns = Math.max(1, Math.ceil(Math.sqrt(total * 1.55)));
      const rows = Math.max(1, Math.ceil(total / columns));
      const spacingX = total <= 100 ? 840 / Math.max(columns - 1, 1) : 90;
      const spacingY = total <= 100 ? 480 / Math.max(rows - 1, 1) : 72;
      const row = Math.floor(index / columns);
      const col = index % columns;
      positions[node.id] = {
        x: columns === 1 ? centerX : 80 + col * spacingX,
        y: rows === 1 ? centerY : 70 + row * spacingY,
      };
      return;
    }

    if (graphLayout.value === 'radial') {
      const typeIndex = getTypeIndex(node.type);
      const layerRadius = 110 + (typeIndex % 4) * 86;
      const angle = (index / total) * Math.PI * 2 + typeIndex * 0.42;
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

  if (nodes.length <= 100) {
    spreadOverlappingNodes(positions, nodes);
  }

  nodePositions.value = positions;
}

function spreadOverlappingNodes(positions, nodes) {
  const minDistance = Math.max(graphNodeSize.value * 3.1, 70);
  for (let iteration = 0; iteration < 70; iteration++) {
    let moved = false;
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const first = positions[nodes[i].id];
        const second = positions[nodes[j].id];
        if (!first || !second) {
          continue;
        }

        let dx = second.x - first.x;
        let dy = second.y - first.y;
        let distance = Math.sqrt(dx * dx + dy * dy);
        if (distance === 0) {
          const angle = (i + j + 1) * 0.73;
          dx = Math.cos(angle);
          dy = Math.sin(angle);
          distance = 1;
        }
        if (distance >= minDistance) {
          continue;
        }

        const offset = (minDistance - distance) / 2;
        const ux = dx / distance;
        const uy = dy / distance;
        first.x = clampNumber(first.x - ux * offset, 42, 958, first.x);
        first.y = clampNumber(first.y - uy * offset, 42, 578, first.y);
        second.x = clampNumber(second.x + ux * offset, 42, 958, second.x);
        second.y = clampNumber(second.y + uy * offset, 42, 578, second.y);
        moved = true;
      }
    }
    if (!moved) {
      break;
    }
  }
}

function rebuildNodePositions() {
  nodePositions.value = {};
  buildNodePositions();
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
  return Math.min(Math.max(graphNodeSize.value, 16), 64);
}

function getNodeDisplayName(node) {
  return node?.id || node?.properties?.vid || node?.label || '-';
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
  return getTypeColor(node.type);
}

function getTypeColor(type) {
  const index = getTypeIndex(type);
  if (colorPalette[index]) {
    return colorPalette[index];
  }
  return `hsl(${(index * 47) % 360} 72% 44%)`;
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
    title: getNodeDisplayName(node),
    type: node.type,
    properties: node.properties,
  };
  selectedTypeColor.value = getNodeColor(node);

  try {
    const { data } = await getKnowledgeGraphNode(node.elementId || node.id);
    selectedGraphItem.value = {
      kind: 'node',
      id: data.id,
      title: getNodeDisplayName(data),
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
  return !graphSettings.value.highlightAll && selectedGraphItem.value && !isNodeRelated(node);
}

function isEdgeActive(edge) {
  return activeEdgeIds.value.has(edge.id);
}

function isEdgeDimmed(edge) {
  return !graphSettings.value.highlightAll && Boolean(selectedGraphItem.value) && !isEdgeActive(edge);
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
  graphPan.value = {
    ...graphPan.value,
    active: false,
    x: 0,
    y: 0,
  };
  rebuildNodePositions();
}

function toggleGraphFullscreen() {
  isGraphFullscreen.value = !isGraphFullscreen.value;
}

function handleGraphWheel(event) {
  zoomGraph(event.deltaY < 0 ? 1.08 : 0.92);
}

function startGraphCanvasPointer(event) {
  if (event.button !== 0) {
    return;
  }
  if (graphSettings.value.boxSelect && event.shiftKey) {
    startBoxSelect(event);
    return;
  }
  startGraphPan(event);
}

function startGraphPan(event) {
  graphPan.value = {
    ...graphPan.value,
    active: true,
    startX: event.clientX,
    startY: event.clientY,
    originX: graphPan.value.x,
    originY: graphPan.value.y,
  };
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
  if (graphPan.value.active) {
    const rect = graphCanvasRef.value?.getBoundingClientRect();
    const width = Math.max(rect?.width || 1, 1);
    const height = Math.max(rect?.height || 1, 1);
    const dx = ((event.clientX - graphPan.value.startX) / width) * 1000;
    const dy = ((event.clientY - graphPan.value.startY) / height) * 620;
    graphPan.value = {
      ...graphPan.value,
      x: graphPan.value.originX + dx,
      y: graphPan.value.originY + dy,
    };
    return;
  }

  if (draggedNode.value) {
    const rect = graphCanvasRef.value?.getBoundingClientRect();
    const width = Math.max(rect?.width || 1, 1);
    const height = Math.max(rect?.height || 1, 1);
    const scale = graphZoom.value || 1;
    const dx = (((event.clientX - draggedNode.value.startX) / width) * 1000) / scale;
    const dy = (((event.clientY - draggedNode.value.startY) / height) * 620) / scale;
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
  graphPan.value = {
    ...graphPan.value,
    active: false,
  };
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

function getSelectedItemKindText(kind) {
  if (kind === 'node') {
    return '实体';
  }
  if (kind === 'edge') {
    return '关系';
  }
  if (kind === 'box') {
    return '框选';
  }
  return '详情';
}

function floatingPanelStyle(position) {
  const style = {
    top: `${position.y}px`,
  };
  if (position.right !== undefined && position.x === 0) {
    style.right = `${position.right}px`;
  } else {
    style.left = `${position.x}px`;
  }
  return style;
}

function startFloatingPanelDrag(panel, event) {
  if (event.button !== 0) {
    return;
  }
  const shellRect = event.currentTarget.closest('.graph-shell')?.getBoundingClientRect();
  const panelRect = event.currentTarget.closest('.floating-panel')?.getBoundingClientRect();
  if (!shellRect || !panelRect) {
    return;
  }

  floatingPanelDrag.value = {
    panel,
    startX: event.clientX,
    startY: event.clientY,
    originX: panelRect.left - shellRect.left,
    originY: panelRect.top - shellRect.top,
    shellWidth: shellRect.width,
    shellHeight: shellRect.height,
    panelWidth: panelRect.width,
    panelHeight: panelRect.height,
  };
  window.addEventListener('mousemove', handleFloatingPanelDrag);
  window.addEventListener('mouseup', stopFloatingPanelDrag);
}

function handleFloatingPanelDrag(event) {
  if (!floatingPanelDrag.value) {
    return;
  }
  const drag = floatingPanelDrag.value;
  const nextX = clampNumber(drag.originX + event.clientX - drag.startX, 8, drag.shellWidth - drag.panelWidth - 8, 8);
  const nextY = clampNumber(drag.originY + event.clientY - drag.startY, 8, drag.shellHeight - drag.panelHeight - 8, 8);
  const nextPosition = { x: nextX, y: nextY };

  if (drag.panel === 'control') {
    graphControlPanelPosition.value = nextPosition;
  } else {
    graphPropertyPanelPosition.value = nextPosition;
  }
}

function stopFloatingPanelDrag() {
  floatingPanelDrag.value = null;
  window.removeEventListener('mousemove', handleFloatingPanelDrag);
  window.removeEventListener('mouseup', stopFloatingPanelDrag);
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

function formatFriendlyTime(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const now = new Date();
  const today = now.toDateString() === date.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);

  const timeText = date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });

  if (today) {
    return timeText;
  }
  if (yesterday.toDateString() === date.toDateString()) {
    return `昨天 ${timeText}`;
  }
  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  });
}

function normalizeHistoryMessages(items = []) {
  const historyMessages = Array.isArray(items)
    ? items.map((item) => ({
        id: item.id || crypto.randomUUID(),
        role: item.role === 'user' ? 'user' : 'assistant',
        content: item.content || '',
        isTyping: false,
      }))
    : [];
  return historyMessages.length > 0 ? historyMessages : createOpeningMessages();
}

function buildConversationTitle(message) {
  const text = String(message || '').replace(/\s+/g, ' ').trim();
  if (!text) {
    return '新对话';
  }
  return text.length <= 24 ? text : `${text.slice(0, 24)}...`;
}

function setKnowledgeNotice(message) {
  knowledgeNotice.value = message;
  window.setTimeout(() => {
    if (knowledgeNotice.value === message) {
      knowledgeNotice.value = '';
    }
  }, 2600);
}

function setUserNotice(message) {
  userNotice.value = message;
  window.setTimeout(() => {
    if (userNotice.value === message) {
      userNotice.value = '';
    }
  }, 2600);
}

function getRoleText(role) {
  return role === 1 ? '管理员' : '普通用户';
}

function getErrorMessage(error, fallback) {
  const data = error?.response?.data;
  if (typeof data === 'string') {
    return data;
  }
  return data?.message || error?.message || fallback;
}

async function fetchDocuments() {
  knowledgeError.value = '';
  isLoadingDocuments.value = true;
  try {
    const { data } = await getKnowledgeDocuments();
    knowledgeDocuments.value = Array.isArray(data) ? data : [];
  } catch (error) {
    if (!handleUnauthorized(error)) {
      knowledgeError.value = getErrorMessage(error, '加载知识库文档失败');
    }
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

async function sendMessage() {
  if (!canSend.value) {
    return;
  }

  responseGenerationId++;
  const currentGenerationId = responseGenerationId;
  shouldAutoScroll.value = isNearBottom();
  const userMessage = inputValue.value;

  let activeConversationId = '';
  try {
    activeConversationId = await ensureActiveConversation(userMessage);
  } catch (error) {
    conversationError.value = getErrorMessage(error, '新建对话失败，请稍后重试');
    return;
  }

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
  eventSource = new EventSource(buildManusSseUrl(userMessage, activeConversationId), {
    withCredentials: true,
  });

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
    await fetchConversations();
    scrollToBottom();
  };
}

onBeforeUnmount(() => {
  closeEventSource();
  stopFloatingPanelDrag();
  typingTimers.forEach((timer) => window.clearTimeout(timer));
  typingTimers.clear();
});
</script>
