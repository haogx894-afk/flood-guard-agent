# 微信云托管部署说明

本项目建议前后端分开构建两个容器镜像：

- 后端：Spring Boot，根目录 `Dockerfile`
- 前端：Vue3 + nginx，`hgx-ai-agent-frontend/Dockerfile`

当前生产环境配置文件为：

```text
src/main/resources/application-prod.yml
```

生产环境不包含 MCP 服务配置，不需要额外部署 MCP Server Jar。

## 后端镜像

在项目根目录执行：

```bash
docker build -t hgx-ai-agent-backend:prod .
```

后端容器暴露端口：

```text
8123
```

后端接口前缀：

```text
/api
```

## 后端环境变量

在微信云托管后端服务中配置：

```text
SPRING_PROFILES_ACTIVE=prod
PORT=8123

DASHSCOPE_API_KEY=你的阿里云百炼 DashScope Key
DASHSCOPE_CHAT_MODEL=qwen-max

SPRING_DATASOURCE_URL=jdbc:postgresql://你的PostgreSQL地址:5432/hgx_ai_agent
SPRING_DATASOURCE_USERNAME=postgres用户名
SPRING_DATASOURCE_PASSWORD=postgres密码

SPRING_NEO4J_URI=bolt://你的Neo4j地址:7687
SPRING_NEO4J_USERNAME=neo4j
SPRING_NEO4J_PASSWORD=neo4j密码

SEARCH_API_KEY=你的搜索接口Key，可为空
KNOWLEDGE_STORAGE_DIR=/data/knowledge-documents
```

可选：

```text
JAVA_OPTS=-Xms256m -Xmx768m
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
MAX_FILE_SIZE=100MB
MAX_REQUEST_SIZE=100MB
SESSION_TIMEOUT=1d
```

## 前端镜像

如果前端和后端通过同一域名访问，并由网关把 `/api` 转发到后端，直接构建：

```bash
docker build -t hgx-ai-agent-frontend:prod ./hgx-ai-agent-frontend
```

如果前端和后端是两个不同域名，构建时传入后端公网地址：

```bash
docker build ^
  --build-arg VITE_API_BASE_URL=https://你的后端域名/api ^
  -t hgx-ai-agent-frontend:prod ^
  ./hgx-ai-agent-frontend
```

前端容器暴露端口：

```text
80
```

## 注意事项

1. PostgreSQL 需要提前创建数据库，并安装 `pgvector` 扩展。
2. Neo4j 需要能被微信云托管后端服务访问。
3. 当前登录态使用 Spring Boot 默认 Session。生产初期建议后端实例数先设置为 1；如果后续要多实例扩容，建议再接入 Redis Session。
4. 知识库上传文件会保存到 `KNOWLEDGE_STORAGE_DIR`。如果需要容器重启后仍可重建文档，请在云托管中挂载持久化目录，或后续改为对象存储。
5. 不要把真实密钥写入代码或镜像，统一使用微信云托管环境变量。
