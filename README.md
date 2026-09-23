# SEForge

SEForge 是面向软件工程课程的教学与 AI 辅导平台。仓库采用 Vue 3 + Spring Boot 模块化单体，并以独立 worker 执行文档摄取、AI 与评审任务。

```text
frontend/                 Vue 3 / Vite Web
backend/                  Spring Boot API 与 worker
backend/docker/           单机 Docker Compose 发布栈
docs/                     设计与业务文档
.github/workflows/ci.yml  持续集成门禁
```

## 开发环境

需要 JDK 21、Node.js 22、npm，以及 Docker Engine 24+ 与 Docker Compose v2。

```bash
# 后端
cd backend
./mvnw clean verify
./mvnw spring-boot:run

# 前端（另一个终端）
cd frontend
npm ci
npm run dev
```

本地前端默认运行在 `http://localhost:5173`。dev server 默认通过已提交的 `frontend/.env.development` 启用 API 代理，指向本机后端 `http://127.0.0.1:8080`（机器级覆盖写 `.env.development.local`）。AI Provider 未配置时，核心课程业务仍应可以启动和测试。

## 单机容器部署

1. 复制环境变量模板，并替换所有 `change-me` 值。不要提交真实 `.env`。

   ```bash
   cp .env.example .env
   ```

2. 校验并启动默认栈。

   ```bash
   docker compose --env-file .env -f backend/docker/docker-compose.yml config --quiet
   docker compose --env-file .env -f backend/docker/docker-compose.yml up -d --build
   ```

3. 打开 `http://localhost:8080`（端口由 `SEFORGE_HTTP_PORT` 控制），查看状态与日志：

   ```bash
   docker compose --env-file .env -f backend/docker/docker-compose.yml ps
   docker compose --env-file .env -f backend/docker/docker-compose.yml logs -f api worker
   ```

模板默认启用 Secure Session Cookie。若只做不经 TLS 的本机联调，可临时设置 `SESSION_COOKIE_SECURE=false`；正式环境必须恢复为 `true` 并在前置代理终止 TLS。

浏览器跨域调用只放行 `SEFORGE_ALLOWED_ORIGINS`（逗号分隔）中列出的 Origin，其值必须与实际访问的公共地址（协议 + 主机 + `SEFORGE_HTTP_PORT`）一致；经自带 nginx 的同源流量自动豁免，不依赖该列表。

SonarQube 代码评审服务是可选组件。Linux 主机先按 SonarQube 要求设置 `vm.max_map_count`，再启动 `review` profile：

```bash
# 在 .env 中设置 SEFORGE_SONAR_ENABLED=true，并配置 SEFORGE_SONAR_TOKEN
docker compose --env-file .env -f backend/docker/docker-compose.yml --profile review up -d
```

SonarQube 默认仅监听 `127.0.0.1:9002`。MySQL、Redis、MinIO 与 Milvus 不向宿主机公开端口。

发布前检查、一致性备份、灾难恢复演练和安全事件处理见 [SEForge 运维手册](docs/operations.md)。

停止服务使用 `docker compose ... down`。只有确认不再需要数据库、对象和索引数据时才使用 `down --volumes`。

## 质量门禁

CI 执行后端 `clean verify`，前端 lint、类型检查、单元测试与生产构建，并校验 Compose 配置及应用镜像构建。配置与容器运行细节见 [backend/README.md](backend/README.md)。

发布前的参数化混合负载门禁位于 [docs/load-test/README.md](docs/load-test/README.md)。它默认覆盖 200 个已认证会话（125 空闲、50 个传统 API 用户、20 路课程问答 SSE、5 个后台任务），必须使用隔离的 Stub AI 和测试账号/课程数据运行：

```bash
docker compose --env-file .env -f backend/docker/docker-compose.yml --profile load-test run --rm k6
```

每门样例课程的 50+ 题 RAG Golden Set 门禁见 [RAG 评测说明](docs/rag-evaluation/README.md)，可在部署完成并导入真实课程资料后执行：

```bash
node docs/rag-evaluation/run.mjs \
  --base-url http://localhost:8080 \
  --identifier evaluator@example.com \
  --password '<password>' \
  --course-id 1 \
  --dataset docs/rag-evaluation/golden-set.jsonl
```
