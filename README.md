# SEForge

面向软件工程教学的课程工作台，连接课程资料、AI 助学、作业、评审和教师反馈。

## 项目简介

SEForge 从多智能体问答项目 SmartSE 演进而来，目标是支持软件工程课程的教学与练习闭环。教师可以组织课程和资料、发布作业、查看学生提交并审阅 AI 建议；学生可以加入课程、按课程资料提问、完成作业并获取受策略约束的 Tutor 辅导。AI 输出作为参考，授权、提交和最终成绩由服务端及教师控制。

## 核心功能

- **Identity / RBAC**：学号学生身份、教师与管理员账号、课程成员角色、Redis Session、CSRF 和服务端权限校验。
- **Course / Class / Resource**：学期、课程、教学班、邀请码、成员、章节、知识点、公告及课程资源。
- **Teaching Content**：章节是教学内容容器；章节资料和课程参考资料共用 `CourseResource`，支持将 PDF/PPT/PPTX/DOCX/Markdown/TXT 加入知识库、查看摄取状态、重建索引和删除。
- **AI Knowledge Points**：教师可基于章节 READY 文档生成可编辑的知识点草稿；只有教师确认后才会创建正式知识点，并保留文件/页码/section 来源引用。
- **RAG / Course Assistant**：课程资料摄取、课程范围检索、带来源引用的问答、会话历史和答案反馈。
- **Assignment / AI Tutor**：手动富内容录题，或从 PDF / 图片 / Markdown 智能提取多题草稿，教师编辑确认后导入；题目与 Rubric、草稿自动保存、正式提交与重交规则；Tutor 提供六类、受教师策略限制的辅导操作。
- **Review 初评中心**：作业、学生报告文档或独立上传文档的结构化评审，也可完全不依赖 AI 进行人工初评；代码静态分析由 SonarQube 提供权威 finding，AI 负责解释。课程知识库不再作为新建文档评审的默认对象。
- **成绩与反馈**：汇总 RULE / AI_ASSISTED / MANUAL 初评，教师逐项确认或覆盖后形成最终成绩；确认与发布分离，仅发布后的成绩对学生可见，支持单条/作业批量发布及审计。
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

在根目录完成 `.env` 凭据配置后，一条命令启动完整开发环境：

```powershell
node scripts/dev.mjs
```

脚本等待基础设施就绪、**用当前工作树执行一次 `mvnw package`**，然后复制刚生成的 JAR，依次启动 **API（8080）→ 独立 Worker JVM（8082）→ Vite（5173）**。因此它默认使用启动时源码对应的最新后端；修改 Java 代码后必须停止并重新执行脚本，运行中的 JVM 不会热替换。打开 `http://127.0.0.1:5173`；日志在 `logs/dev/`。Ctrl+C 会清理本次启动的子进程和新启动的基础设施，原先运行的容器及全部 volume 保留。不要同时启动生产 Compose 的 frontend/API。

启动后可用以下命令确认实际实例，而不是误连旧服务：

```powershell
curl.exe http://127.0.0.1:8080/actuator/health
curl.exe http://127.0.0.1:8082/actuator/health
Get-Item logs/dev/backend-*.jar | Select-Object LastWriteTime,Length
```

两个健康检查都应返回 `"status":"UP"`，但健康检查不代表云模型已通过真实调用。没有收到 HTTP 响应既可能是连接失败，也可能是等待超时，不能据此认定请求未到达 API。前端现在区分这两种情况；服务端模型、Redis、草稿结构错误会显示明确错误码与 traceId。先检查 Vite / API 进程及 `logs/dev/api.log`，再结合 `ai_trace` 的模型、状态与耗时定位；不要复制 Session、密钥或完整 Prompt 到日志。

知识点生成要求本章节有 READY Chunk、Redis 可用及 REASONING 模型已配置（DeepSeek 主路由或 DashScope 备用路由）。生成复用 AI Runtime 流式传输，仅在完整结构与来源校验成功后返回草稿，失败不写正式知识点。错误码为 `KNOWLEDGE_POINT_TIMEOUT`、`KNOWLEDGE_POINT_PROVIDER_FAILED`、`KNOWLEDGE_POINT_REDIS_UNAVAILABLE` 或 `KNOWLEDGE_POINT_INVALID_OUTPUT`。资料达到 READY 仍需要独立 Worker。

知识点专用配置 `SEFORGE_KNOWLEDGE_POINT_TIMEOUT=300s` 将总生成时限设为默认 5 分钟（可配置 60～600 秒）；单次模型尝试为 `min(总时限,180秒)`，同时应用到 SDK 与 Runtime。前端等待上限 630 秒，以便收到服务端最终错误。其他 AI 调用仍使用 `SEFORGE_AI_TIMEOUT`，无需调大全局超时。Compose 已透传新变量，AI 出站代理等待上限同步放宽，供应商访问白名单不变；本地未设置新变量时也默认 300 秒。

多文档采用服务端授权选材：仅本课程/章节的 READY 文档和当前 embeddingVersion 的有效 Chunk → 文档间均衡分配最多 40 个片段、24,000 字符 → 一次 REASONING 调用 → 结构与来源校验 → 教师确认。小章节完整提供所有有效片段，大章节按各文档首尾及均匀位置抽样，页面显示覆盖数量与截取提示，不宣称穷尽全文。超过 40 份文档或 READY 文档缺少有效 Chunk 时明确拒绝，而非静默漏掉资料。引用属于该章节不等于语义一定正确，仍需教师复核。

页面还会区分“提供给模型的文档”与“当前勾选草稿实际引用的文档”，列出尚未引用的资料；请据此检查是否遗漏概念，不要将完整输入覆盖当成完整知识点总结。

`node scripts/dev.mjs` 每次启动都会重新执行后端 `package`，但运行中的 JVM 不会自动加载后续 Java 修改。更新源码后应先在原启动终端 `Ctrl+C`，再运行同一命令。真实知识点 smoke 可在 `backend` 中构建后执行 `node --env-file=../.env scripts/verify-knowledge-points.mjs --launch-api`：使用本地基础设施与已有 Worker、临时 API 端口 18081，创建标记为 `BUG9 synthetic smoke` 的测试课程，实际调用供应商并验证教师确认；会消耗少量模型额度并保留合成测试数据，凭据不写入报告。

端口冲突时脚本会停止并提示，不会杀掉其他进程。可在 `.env` 设置 `SEFORGE_DEV_API_PORT`、`SEFORGE_DEV_WORKER_PORT`、`SEFORGE_DEV_VITE_PORT`；代理地址与开发 Origin 自动同步。`node scripts/dev.mjs --smoke` 在三项 HTTP 健康检查通过后自动退出并清理。脚本只启动基础设施容器，**不改变生产 API/Worker 分离架构**；Code Review 仍需使用受网络限制的 Compose worker，本地脚本禁用 Sonar 扫描。

Windows 启动器会离线探测 JDK HTTP Client；如遇到 Unix-domain Pipe 初始化问题，会为本次 API/Worker 自动启用 loopback TCP 兼容模式，无需设置额外 JVM 环境变量。该探测不调用云模型，也不改变生产运行配置。

如果需要分别调试进程，可手动启动基础设施，再在不同终端运行：

```powershell
docker compose --env-file .env -f backend/docker/docker-compose.yml up -d mysql redis minio minio-init etcd milvus
# 在 backend/：
.\mvnw.cmd spring-boot:run
# 在另一个 backend/ 终端：
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,worker'
# 在 frontend/：
npm install
npm run dev
```

打开 `http://localhost:5173`。Vite 已配置 `/api/v1` 代理至 `http://127.0.0.1:8080`。宿主机 API/worker 自动读取相同的 `MINIO_APP_*` 凭据，并通过回环端口连接 MinIO（9000）、Milvus（19530）、MySQL（3307）、Redis（6380）；端口可在 `.env` 调整，Compose 内部地址不受影响。无需手动复制 MinIO 凭据别名。`SEFORGE_VECTOR_STORE_ENABLED=true` 已在模板声明。课程摄取必须启动 worker 并填写 `DASHSCOPE_API_KEY`；聊天可以使用 DeepSeek，也可以使用 DashScope 的 fallback 模型。Code Review 需要额外配置 Sonar Token，并使用 Compose 中受网络限制的 worker。不要在 API 进程单独启用任务发布而忘记启动消费者。

存储连接错误分别返回 `STORAGE_UNAVAILABLE` / `VECTOR_STORE_UNAVAILABLE`，页面显示原因及追踪号。若更改了已有数据库或 MinIO 用户的密码，仅修改 `.env` 不会自动修改持久化的服务端账号，应按服务的凭据轮换流程同步更新。生产 overlay 会取消上述基础设施宿主机端口映射。

Course Assistant 的生成任务不随页面切换取消。返回或刷新会话时，客户端查询服务端 `PROCESSING / COMPLETED / FAILED / CANCELLED` 状态和完整历史；只有“停止”按钮主动取消。SSE 断开后不会重调模型。进程崩溃后的未完成请求会在 10 分钟截止期限后标为失败（不自动重放模型调用），可以手动重试。Markdown 原文持久化，展示时经过安全解析与白名单过滤。

## 环境变量

以下按仓库根目录[`.env.example`](.env.example) 分类。占位凭据须在运行前替换；实际生产秘密应使用受控密钥管理方式，不要提交 `.env`。

| 分类 | 变量 | 用途与要求 |
| --- | --- | --- |
| 镜像、Profile 和端口 | `SEFORGE_IMAGE_TAG`、`SEFORGE_API_PROFILES`、`SEFORGE_WORKER_PROFILES`、`SEFORGE_BIND_ADDRESS`、`SEFORGE_HTTP_PORT`、`MYSQL_HOST_PORT`、`REDIS_HOST_PORT`、`SONAR_HTTP_PORT` | 镜像标签、API/worker Spring profile 及本机绑定地址/映射端口。生产镜像使用不可变标签；Production TLS 另需 `SEFORGE_HTTPS_PORT`、`SEFORGE_TLS_DIR`、`SEFORGE_TLS_CONFIG`。 |
| MySQL | `MYSQL_DATABASE`、`MYSQL_APP_USERNAME`、`MYSQL_APP_PASSWORD`、`MYSQL_ROOT_PASSWORD` | Compose 启动和 API 连接所需；为应用与 root 分别设置强随机凭据。 |
| 本地统一启动 | `SEFORGE_DEV_API_PORT`、`SEFORGE_DEV_WORKER_PORT`、`SEFORGE_DEV_VITE_PORT` | 默认 8080 / 8082 / 5173；仅作用于 `scripts/dev.mjs`。手动启动且改端口时，还需同步 `SEFORGE_DEV_ALLOWED_ORIGINS` 与前端 `SEFORGE_API_PROXY`。 |
| Redis | `REDIS_PASSWORD` | Compose 启动及 Session/任务使用所需。宿主机端口仅绑定回环地址。 |
| MinIO | `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`、`MINIO_APP_ACCESS_KEY`、`MINIO_APP_SECRET_KEY`、`MINIO_APP_BUCKET`、`MINIO_HOST_PORT` | Root 凭据供 MinIO/初始化使用；API 和宿主机自动共用应用凭据。高级外部服务覆盖项为 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`。 |
| Session / Security | `SESSION_COOKIE_SECURE`、`SEFORGE_ALLOWED_ORIGINS`、`SEFORGE_API_RATE_LIMIT_GENERAL`、`SEFORGE_API_RATE_LIMIT_EXPENSIVE`、`SEFORGE_API_RATE_LIMIT_UPLOAD` | 本机 HTTP 才临时关闭 Secure Cookie；生产保持 `true`。Origin 必须匹配浏览器访问地址。限流值按每分钟预算配置。 |
| 首次管理员 | `SEFORGE_BOOTSTRAP_ADMIN_EMAIL`、`SEFORGE_BOOTSTRAP_ADMIN_USERNAME`、`SEFORGE_BOOTSTRAP_ADMIN_PASSWORD`、`SEFORGE_BOOTSTRAP_ADMIN_DISPLAY_NAME` | 新数据库的可选一次性初始化。设置后首次启动创建管理员；成功后应清空 Bootstrap 邮箱/密码。密码须符合应用长度策略。 |
| 存储配额 | `SEFORGE_STORAGE_COURSE_QUOTA_BYTES`、`SEFORGE_STORAGE_ATTACHMENT_USER_QUOTA_BYTES`、`SEFORGE_STORAGE_ATTACHMENT_COURSE_QUOTA_BYTES` | 课程资料、用户附件及课程附件容量限制，单位为字节。 |
| Milvus | `SEFORGE_VECTOR_STORE_ENABLED`、`MILVUS_ENABLED`、`MILVUS_HOST_PORT`、`MILVUS_COLLECTION_PREFIX`、`SEFORGE_VECTOR_ACTIVE_VERSION`、`SEFORGE_VECTOR_WRITE_VERSION`、`SEFORGE_VECTOR_RECONCILIATION_INTERVAL` | 统一向量客户端开关（旧 `MILVUS_ENABLED` 仅作 fallback）、本机端口、集合前缀、读写版本与对账周期。外部服务可覆盖 `MILVUS_HOST` / `MILVUS_PORT`；版本变更前重索引。 |
| DeepSeek / DashScope | `SEFORGE_AI_ENABLED`、`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DASHSCOPE_API_KEY`、`DASHSCOPE_BASE_URL`、`SEFORGE_FAST_MODEL`、`SEFORGE_REASONING_MODEL`、`SEFORGE_CODING_MODEL`、`SEFORGE_FALLBACK_MODEL`、`SEFORGE_EMBEDDING_MODEL`、`SEFORGE_EMBEDDING_DIMENSION`、`SEFORGE_AI_TIMEOUT`、`SEFORGE_AI_MAX_RETRIES` | 模板启用 Runtime；填入安全配置的密钥后才注册可用 Provider。模型名、Embedding 维数、超时和重试可调。更换向量模型/维数须配合索引版本迁移。 |
| 图片理解 / 导题 | `SEFORGE_AI_VISION_MODEL` | 图片导题、扫描 PDF 与答案图片识别使用此变量指定的独立 DashScope 模型端点，复用 `DASHSCOPE_BASE_URL`、`DASHSCOPE_API_KEY`、`SEFORGE_AI_TIMEOUT` 和 `SEFORGE_AI_MAX_RETRIES`。需选择支持图像输入的模型；留空或未配置 DashScope 凭据时这些操作明确失败，不影响文本导题、RULE 或 MANUAL 评分。 |
| 智能导题时限 | `SEFORGE_QUESTION_EXTRACTION_TIMEOUT` | 题目结构化生成使用流式传输，默认 300s，可配置 60s～480s；所有重试与备用模型共享此阶段的总预算。图片理解仍使用上行 Vision 设置。超时返回 `QUESTION_EXTRACTION_TIMEOUT`，错误详情包含 `VISION` 或 `QUESTION_EXTRACTION` 阶段。 |
| Worker 出站 | `SEFORGE_WORKER_DEEPSEEK_BASE_URL`、`SEFORGE_WORKER_DASHSCOPE_BASE_URL` | 默认留空，由 worker 经固定 allow-list egress proxy 访问供应商；Stub 验收才覆盖为内部地址。 |
| 任务运行参数 | `SEFORGE_JOBS_ENABLED`、`SEFORGE_JOB_GROUP`、`SEFORGE_JOB_STREAM`、`SEFORGE_JOB_LEASE_DURATION`、`SEFORGE_JOB_MAX_ATTEMPTS`、`SEFORGE_JOB_QUEUED_STALE_AFTER`、`SEFORGE_JOB_RECOVERY_BATCH_SIZE` | 通常由 Compose profile 或 `scripts/dev.mjs` 设置；API 保持 `SEFORGE_JOBS_ENABLED=false`，只有独立 Worker 消费任务。仅在隔离环境调整租约、重试和恢复批次。 |
| 登录与 API 限流 | `SEFORGE_LOGIN_RATE_LIMIT_MAX_ATTEMPTS`、`SEFORGE_LOGIN_RATE_LIMIT_WINDOW`、`SEFORGE_API_RATE_LIMIT_WINDOW` | 登录暴力尝试和 API 限流窗口；生产按容量与安全策略调整。 |
| 向量对账高级参数 | `SEFORGE_VECTOR_RECONCILIATION_INITIAL_DELAY`、`SEFORGE_VECTOR_RECONCILIATION_BATCH_SIZE`、`SEFORGE_VECTOR_RECONCILIATION_PAGE_SIZE` | 控制 Milvus 孤儿/缺失向量对账启动延迟和批次；一般使用默认值。 |
| Review 超时与限制 | `SEFORGE_SONAR_API_TIMEOUT`、`SEFORGE_SONAR_COMPUTE_TIMEOUT`、`SEFORGE_SONAR_POLL_INTERVAL`、`SEFORGE_SONAR_SCANNER_TIMEOUT`、`SEFORGE_SONAR_MAX_FINDINGS`、`SEFORGE_SONAR_MAX_OUTPUT_BYTES` | 仅启用 SonarQube Review 时使用，防止扫描、轮询和报告大小无限增长。 |
| SonarQube | `SEFORGE_SONAR_ENABLED`、`SEFORGE_SONAR_SERVER_URL`、`SEFORGE_SONAR_TOKEN`、`SEFORGE_SONAR_SCANNER_EXECUTABLE`、`SONAR_POSTGRES_DB`、`SONAR_POSTGRES_USER`、`SONAR_POSTGRES_PASSWORD` | Review profile 的可选静态分析服务及分析凭据；Token 只使用受限分析权限，切勿提交。 |
| 加载测试 | `SEFORGE_LOAD_TEST_*` | 仅为隔离负载测试账号、课程、角色、并发与时间配置。变量完整说明见[负载测试文档](docs/load-test/README.md)，不要使用生产账号/模型。 |

## 开发与测试

作业支持 Markdown、稳定选项 ID、图片/附件、代码与文档报告。客观题使用 `RULE` 精确匹配；文本主观题可用 `AI_ASSISTED`；无可靠文本的纯图片题/答案走 `MANUAL`，不强迫 AI 评分。最终成绩仍需教师逐项确认。代码静态评审须使用已配置 SonarQube 的受限网络 Worker，本地统一启动器不会开放源码扫描的网络权限。

在草稿作业点击“智能导入题目”，上传一份 PDF / Markdown 或按顺序选择 1～3 张 PNG / JPEG，编辑并勾选题目后“确认导入”。缺失题型、答案或分值不会伪造；评分量表仍由教师独立维护。单次最多 20 MB、20 页 PDF、5 个需要 Vision 的页面、50 题与 60000 字符；超限请拆分。PDF 优先提取文本，图片和缺少文字的页面调用独立 DashScope Vision 模型 `SEFORGE_AI_VISION_MODEL`；题目结构化提取使用 REASONING 路由。整份源文件仅教师可见，含图形题请核对原件并按需添加安全的题目附件；不会将含答案的整份试卷直接开放给学生。参考答案图片可“识别为 Markdown”，编辑确认后才替换表单中的答案，原图保留。

在 `backend/`：

```powershell
.\mvnw.cmd '-Dapi.version=1.44' clean verify
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

- [Roadmap](docs/roadmap.md)
- [运维手册](docs/operations.md)
- [UI、Agent 与 Tool 设计](docs/ai-agent-design.md)
- [RAG 评测说明](docs/rag-evaluation/README.md)
- [混合负载门禁](docs/load-test/README.md)
- [发布运行机器证据](docs/release/results/README.md)
