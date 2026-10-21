# PostGIS + pgvector 组合数据库镜像
# 以 pgvector（bookworm，RAG 向量检索）为底，安装 PostGIS（空间数据）
FROM pgvector/pgvector:pg16

# 国内构建：所有 apt 源切到阿里云镜像（用 http，精简镜像无 ca-certificates）
RUN grep -rl 'deb.debian.org\|security.debian.org\|apt.postgresql.org' /etc/apt/ 2>/dev/null | while read -r f; do \
      sed -i 's|http://deb.debian.org|http://mirrors.aliyun.com|g; \
              s|http://security.debian.org|http://mirrors.aliyun.com|g; \
              s|http://apt.postgresql.org/pub/repos/apt|http://mirrors.aliyun.com/postgresql/repos/apt|g' "$f"; \
    done \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-postgis-3 postgresql-16-postgis-3-scripts \
    && rm -rf /var/lib/apt/lists/*
