# SEForge

面向软件工程教学的课程工作台，连接课程资料、AI 助学、作业、评审和教师反馈。

## 项目简介

SEForge 从多智能体问答项目 SmartSE 演进而来，目标是支持软件工程课程的教学与练习闭环。教师可以组织课程和资料、发布作业、查看学生提交并审阅 AI 建议；学生可以加入课程、按课程资料提问、完成作业并获取受策略约束的 Tutor 辅导。AI 输出作为参考，授权、提交和最终成绩由服务端及教师控制。

## 核心功能

- **Identity / RBAC**：学号学生身份、教师与管理员账号、课程成员角色、Redis Session、CSRF 和服务端权限校验。
- **Course / Class / Resource**：学期、课程、教学班、邀请码、成员、章节、知识点、公告及课程资源。
- **RAG / Course Assistant**：课程资料摄取、课程范围检索、带来源引用的问答、会话历史和答案反馈。
- **Assignment / AI Tutor**：题目与 Rubric、草稿自动保存、正式提交与重交规则；Tutor 提供六类、受教师策略限制的辅导操作。
- **Document / Assignment / Code Review**：文档和作业结构化评审；代码静态分析由 SonarQube 提供权威 finding，AI 负责解释。
- **AI-assisted Grading**：AI 提供分项评分建议；教师确认或覆盖后才形成最终成绩，并记录审阅信息。
- **Teacher Dashboard / Analytics**：按课程和教学班查看完成率、成绩、知识点、常见问题、Tutor 用量和任务错误趋势。
- **AI Runtime / Reliable Jobs**：能力路由、版本化 Prompt、Trace、结构化输出与流式调用；基于 MySQL Outbox、Redis Streams 和独立 worker 执行可恢复任务。

## 技术架构

统一 Vue 3 / TypeScript / Pinia 前端通过 `/api/v1` 访问 Java 21、Spring Boot 3.4.5 模块化单体。LangChain4j 1.12.1 封装模型服务；MySQL 保存业务数据与任务 Outbox，Redis 保存 Session 并传递任务，MinIO 保存课程和作业文件，Milvus 保存版本化课程向量。API 与独立 worker 共用后端代码；Docker Compose 管理单机服务。SonarQube 是可选的代码评审服务。DeepSeek 与 DashScope 通过 AI Runtime 接入，供应商密钥由环境变量注入。

## 快速开始

### 前置依赖

- Docker Engine 24+ 和 Docker Compose v2
- 本地前后端开发：JDK 21、Node.js 22、npm
- PowerShell 示例适用于 Windows；类 Unix 环境可将 `mvnw.cmd` 替换为 `./mvnw`

### Docker Compose 启动（推荐）

在仓库根目录执行：

```powershell
git clone https://github.com/YangMingZhe34985/SEForge.git
cd SEForge
Copy-Item .env.example .env
```

编辑 `.env`：将所有 `change-me` 凭据替换为随机的本地值；若只通过本机 HTTP 使用默认 Compose，将 `SESSION_COOKIE_SECURE=false`、`SEFORGE_BIND_ADDRESS=127.0.0.1`、`SEFORGE_ALLOWED_ORIGINS=http://localhost:8080`。这只适用于本机开发。生产部署须保留 Secure Cookie，配置 TLS，并追加 `backend/docker/compose.production.yml`；详见[运维手册](docs/operations.md)。

配置首次管理员（空值默认关闭 Bootstrap）：

```dotenv
SEFORGE_BOOTSTRAP_ADMIN_EMAIL=admin@example.invalid
SEFORGE_BOOTSTRAP_ADMIN_USERNAME=admin
SEFORGE_BOOTSTRAP_ADMIN_PASSWORD=<随机且符合长度要求的本地密码>
```

启动并检查服务：

```powershell
docker compose --env-file .env -f backend/docker/docker-compose.yml config --quiet
docker compose --env-file .env -f backend/docker/docker-compose.yml up -d --build
docker compose --env-file .env -f backend/docker/docker-compose.yml ps
```

浏览器访问 `http://localhost:8080`。用引导账号进入 `/login/admin`，随后由管理员创建教师账号。管理员成功创建后清空 `.env` 中的 Bootstrap 邮箱和密码，再重建 API 容器；普通用户、教师和学号学生从登录页进入。模板启用 AI Runtime，但只有填入供应商凭据后 AI 才可用；空凭据时核心平台仍可启动。SonarQube 不随默认栈启动。

停止容器但保留数据库和文件：

```powershell
docker compose --env-file .env -f backend/docker/docker-compose.yml down
```

`down --volumes` 会删除本栈持久数据，仅应在确认不再需要这些数据时使用。

### 本地前后端开发

若在宿主机运行 API，先仅启动其依赖基础设施，避免与宿主机 API 的 8080 端口冲突：

```powershell
docker compose --env-file .env -f backend/docker/docker-compose.yml up -d mysql redis minio minio-init etcd milvus
```

在终端一运行后端（dev profile 会从仓库根目录 `.env` 读取本地 MySQL 3307、Redis 6380 配置）：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

终端二运行前端：

```powershell
cd frontend
npm ci
npm run dev
```

在终端三启动摄取与 Review worker（从 `backend/` 执行，默认端口 8082）：

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,worker'
```

打开 `http://localhost:5173`。Vite 已配置 `/api/v1` 代理至 `http://127.0.0.1:8080`。宿主机 API/worker 自动读取相同的 `MINIO_APP_*` 凭据，并通过回环端口连接 MinIO（9000）、Milvus（19530）、MySQL（3307）、Redis（6380）；端口可在 `.env` 调整，Compose 内部地址不受影响。无需手动复制 MinIO 凭据别名。`SEFORGE_VECTOR_STORE_ENABLED=true` 已在模板声明。课程摄取必须启动 worker 并填写 `DASHSCOPE_API_KEY`；聊天可以使用 DeepSeek，也可以使用 DashScope 的 fallback 模型。Code Review 需要额外配置 Sonar Token，并使用 Compose 中受网络限制的 worker。不要在 API 进程单独启用任务发布而忘记启动消费者。

存储连接错误分别返回 `STORAGE_UNAVAILABLE` / `VECTOR_STORE_UNAVAILABLE`，页面显示原因及追踪号。若更改了已有数据库或 MinIO 用户的密码，仅修改 `.env` 不会自动修改持久化的服务端账号，应按服务的凭据轮换流程同步更新。生产 overlay 会取消上述基础设施宿主机端口映射。

## 环境变量

以下按仓库根目录[`.env.example`](.env.example) 分类。占位凭据须在运行前替换；实际生产秘密应使用受控密钥管理方式，不要提交 `.env`。

| 分类 | 变量 | 用途与要求 |
| --- | --- | --- |
| 镜像、Profile 和端口 | `SEFORGE_IMAGE_TAG`、`SEFORGE_API_PROFILES`、`SEFORGE_WORKER_PROFILES`、`SEFORGE_BIND_ADDRESS`、`SEFORGE_HTTP_PORT`、`MYSQL_HOST_PORT`、`REDIS_HOST_PORT`、`SONAR_HTTP_PORT` | 镜像标签、API/worker Spring profile 及本机绑定地址/映射端口。生产镜像使用不可变标签；Production TLS 另需 `SEFORGE_HTTPS_PORT`、`SEFORGE_TLS_DIR`、`SEFORGE_TLS_CONFIG`。 |
| MySQL | `MYSQL_DATABASE`、`MYSQL_APP_USERNAME`、`MYSQL_APP_PASSWORD`、`MYSQL_ROOT_PASSWORD` | Compose 启动和 API 连接所需；为应用与 root 分别设置强随机凭据。 |
| Redis | `REDIS_PASSWORD` | Compose 启动及 Session/任务使用所需。宿主机端口仅绑定回环地址。 |
| MinIO | `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`、`MINIO_APP_ACCESS_KEY`、`MINIO_APP_SECRET_KEY`、`MINIO_APP_BUCKET`、`MINIO_HOST_PORT` | Root 凭据供 MinIO/初始化使用；API 和宿主机自动共用应用凭据。高级外部服务覆盖项为 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`。 |
| Session / Security | `SESSION_COOKIE_SECURE`、`SEFORGE_ALLOWED_ORIGINS`、`SEFORGE_API_RATE_LIMIT_GENERAL`、`SEFORGE_API_RATE_LIMIT_EXPENSIVE`、`SEFORGE_API_RATE_LIMIT_UPLOAD` | 本机 HTTP 才临时关闭 Secure Cookie；生产保持 `true`。Origin 必须匹配浏览器访问地址。限流值按每分钟预算配置。 |
| 首次管理员 | `SEFORGE_BOOTSTRAP_ADMIN_EMAIL`、`SEFORGE_BOOTSTRAP_ADMIN_USERNAME`、`SEFORGE_BOOTSTRAP_ADMIN_PASSWORD`、`SEFORGE_BOOTSTRAP_ADMIN_DISPLAY_NAME` | 新数据库的可选一次性初始化。设置后首次启动创建管理员；成功后应清空 Bootstrap 邮箱/密码。密码须符合应用长度策略。 |
| 存储配额 | `SEFORGE_STORAGE_COURSE_QUOTA_BYTES`、`SEFORGE_STORAGE_ATTACHMENT_USER_QUOTA_BYTES`、`SEFORGE_STORAGE_ATTACHMENT_COURSE_QUOTA_BYTES` | 课程资料、用户附件及课程附件容量限制，单位为字节。 |
| Milvus | `SEFORGE_VECTOR_STORE_ENABLED`、`MILVUS_ENABLED`、`MILVUS_HOST_PORT`、`MILVUS_COLLECTION_PREFIX`、`SEFORGE_VECTOR_ACTIVE_VERSION`、`SEFORGE_VECTOR_WRITE_VERSION`、`SEFORGE_VECTOR_RECONCILIATION_INTERVAL` | 统一向量客户端开关（旧 `MILVUS_ENABLED` 仅作 fallback）、本机端口、集合前缀、读写版本与对账周期。外部服务可覆盖 `MILVUS_HOST` / `MILVUS_PORT`；版本变更前重索引。 |
| DeepSeek / DashScope | `SEFORGE_AI_ENABLED`、`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DASHSCOPE_API_KEY`、`DASHSCOPE_BASE_URL`、`SEFORGE_FAST_MODEL`、`SEFORGE_REASONING_MODEL`、`SEFORGE_CODING_MODEL`、`SEFORGE_FALLBACK_MODEL`、`SEFORGE_EMBEDDING_MODEL`、`SEFORGE_EMBEDDING_DIMENSION`、`SEFORGE_AI_TIMEOUT`、`SEFORGE_AI_MAX_RETRIES` | 模板启用 Runtime；填入安全配置的密钥后才注册可用 Provider。模型名、Embedding 维数、超时和重试可调。更换向量模型/维数须配合索引版本迁移。 |
| Worker 出站 | `SEFORGE_WORKER_DEEPSEEK_BASE_URL`、`SEFORGE_WORKER_DASHSCOPE_BASE_URL` | 默认留空，由 worker 经固定 allow-list egress proxy 访问供应商；Stub 验收才覆盖为内部地址。 |
| SonarQube | `SEFORGE_SONAR_ENABLED`、`SEFORGE_SONAR_SERVER_URL`、`SEFORGE_SONAR_TOKEN`、`SEFORGE_SONAR_SCANNER_EXECUTABLE`、`SONAR_POSTGRES_DB`、`SONAR_POSTGRES_USER`、`SONAR_POSTGRES_PASSWORD` | Review profile 的可选静态分析服务及分析凭据；Token 只使用受限分析权限，切勿提交。 |
| 加载测试 | `SEFORGE_LOAD_TEST_*` | 仅为隔离负载测试账号、课程、角色、并发与时间配置。变量完整说明见[负载测试文档](docs/load-test/README.md)，不要使用生产账号/模型。 |

## 开发与测试

在 `backend/`：

```powershell
.\mvnw.cmd -Dapi.version=1.44 clean verify
```

需要 JDK 21；集成测试需要可用 Docker，测试容器可能使用隔离的 MySQL、Redis、MinIO、Milvus 或 Sonar 服务。

在 `frontend/`：

```powershell
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm run test:e2e
```

Compose 配置检查：

```powershell
docker compose --env-file .env.example -f backend/docker/docker-compose.yml --profile review --profile load-test config --quiet
```

可复现的阶段发布栈、故障恢复和备份演练入口见[Phase 6 机器证据说明](docs/release/results/README.md)及[运维手册](docs/operations.md)。RAG 评测和 Stub 门禁的适用范围见[RAG 评测说明](docs/rag-evaluation/README.md)。CI 会运行后端 verify、前端静态检查/单测/浏览器测试/构建和 Compose 镜像检查。

## 文档

- [开发过程记录书](docs/development-history.md)
- [Roadmap](docs/roadmap.md)
- [运维手册](docs/operations.md)
- [UI、Agent 与 Tool 设计](docs/ai-agent-design.md)
- [RAG 评测说明](docs/rag-evaluation/README.md)
- [混合负载门禁](docs/load-test/README.md)
- [发布运行机器证据](docs/release/results/README.md)
- [本地开发 BUG-0～6 排查与真实 AI 验证](docs/bug-audit-2026-09-25.md)
