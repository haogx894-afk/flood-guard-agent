# 通用服务器部署指南（Docker Compose 一体化）

适用场景：一台 Linux 云服务器（阿里云 / 腾讯云 / 华为云等），用 Docker Compose 一次性拉起
前端、后端、PostgreSQL(pgvector)、Neo4j 四个服务。

> 微信云托管部署请见 `DEPLOYMENT_WECHAT_CLOUD.md`，本文档面向自有服务器。

## 一、服务器要求

- 配置：建议 ≥ 2 核 4 GB（Neo4j + Java 后端较吃内存，2 GB 会紧张）
- 系统：Ubuntu 20.04+ / Debian 11+ / CentOS 7+ 均可
- 端口放行（安全组 + 系统防火墙）：`80`（HTTP 入口，可用 HTTP_PORT 改）；数据库和 Neo4j 不对外暴露

## 二、安装 Docker

Ubuntu/Debian：

```bash
curl -fsSL https://get.docker.com | bash
sudo systemctl enable --now docker
```

验证：

```bash
docker --version
docker compose version
```

## 三、上传源码

方式 A（推荐，仓库为公开仓库）：

```bash
git clone https://github.com/haogx894-afk/flood-guard-agent.git
cd flood-guard-agent
```

方式 B（本地源码上传）：

```bash
# 在本地执行
scp -r flood-guard-agent root@服务器IP:/opt/
```

## 四、配置环境变量

```bash
cp .env.example .env
vi .env
```

必填三项：

| 变量 | 说明 |
| --- | --- |
| `DASHSCOPE_API_KEY` | 阿里云百炼 DashScope Key |
| `POSTGRES_PASSWORD` | PostgreSQL 密码（自定义强密码） |
| `NEO4J_PASSWORD` | Neo4j 密码（自定义强密码） |

可选：`HTTP_PORT`（默认 80）、`DASHSCOPE_CHAT_MODEL`（默认 qwen-max）、
`SEARCH_API_KEY`、`JAVA_OPTS`。

## 五、构建并启动

```bash
docker compose up -d --build
```

首次构建约 5–15 分钟（Maven 依赖 + pnpm 依赖下载）。查看状态与日志：

```bash
docker compose ps
docker compose logs -f backend
```

## 六、验证

- 前端：浏览器访问 `http://服务器IP/`
- 后端健康检查：`curl http://服务器IP/api/...`（接口前缀为 `/api`）

## 七、常用运维命令

```bash
docker compose up -d --build      # 代码更新后重建
docker compose restart backend    # 重启后端
docker compose down               # 停止全部（数据卷保留）
docker compose down -v            # 停止并删除数据（危险！）
```

## 八、注意事项

1. 数据持久化：PostgreSQL、Neo4j、知识库上传文件分别存于 `db-data`、`neo4j-data`、
   `knowledge-documents` 三个数据卷，容器重建不丢数据。
2. pgvector 扩展由 `deploy/initdb/01-enable-pgvector.sql` 在数据库**首次**初始化时自动创建；
   如果数据卷已存在且缺扩展，手动执行：
   `docker compose exec db psql -U postgres -d hgx_ai_agent -c "CREATE EXTENSION IF NOT EXISTS vector;"`
3. 登录态使用 Spring Session 内存存储，后端固定单实例；多实例扩容需先接 Redis Session。
4. 如需 HTTPS 和域名，建议在前面再加一层 Caddy/Nginx 或云厂商 SLB 终结 TLS。
   以 Caddy 为例（`/etc/caddy/Caddyfile`），域名 A 记录指向服务器后追加：

   ```caddy
   example.com, www.example.com {
       encode gzip
       reverse_proxy 127.0.0.1:8088
   }
   ```

   `sudo systemctl reload caddy` 后 Caddy 会自动签发并续期 Let's Encrypt 证书，
   访问地址变为 `https://example.com`（隐藏端口）。
5. 纯 HTTP 部署时前端有两个兼容性修复（`crypto.randomUUID` 回退、SSE URL 拼接），
   已包含在当前代码中，HTTPS 部署不受影响。
6. 不要把 `.env` 提交到 Git。
