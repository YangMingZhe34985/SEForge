# SEForge P0/P1 可用版升级实施计划

## 一、目标与总体方案

基线审计确认：

- 前端可生产构建，但仍是“登录 + 单页聊天 Demo”，主包约 5.7 MB，零自动化测试。
- 后端 146 个源码文件可编译，但现有 4 个测试均因 AI 配置与外部服务耦合而失败。
- 当前认证实际无效，课程、作业、批改、Dashboard 等核心领域尚不存在。
- Agent、RAG、Tool Calling 均存在重复实现或占位逻辑，不能直接继续扩展。
- 数据库脚本、Compose、Java 实体互相漂移，必须先建立工程基线。

首个正式版本的完成标准为：交付全部 P0/P1，包括用户/RBAC、课程班级、课程知识库、课程问答、作业与 Tutor、业务型 Agent、文档/作业/代码 Review、AI 辅助批改和教师 Dashboard。学习画像、Teacher Agent、在线 Judge、知识图谱、通用爬虫及项目管理能力后置。

目标架构采用单机 Docker Compose 上的模块化单体：

```text
Vue 3 Web
  → Spring Boot API
      → MySQL（业务数据）
      → Redis（Session、Cache、Streams）
      → MinIO（课程资料、作业文件）
      → Milvus（课程向量索引）
      → AI Worker / Review Worker
      → DeepSeek + DashScope
      → SonarQube（P1 代码评审）
```

## 二、核心架构与公共契约

- 全量更名为 SEForge：Java 包 `com.ustb.seforge`、Maven artifact `seforge-backend`、数据库 `seforge`、配置前缀 `seforge.*`、前端包 `seforge-web`，清除最终产物中的 SmartSE 文案与旧接口。
- 后端按 `identity / course / content / conversation / assignment / review / analytics / ai / job / common` 划分模块，并用架构测试禁止 Controller 直连 Mapper、业务模块直接依赖模型厂商 SDK。
- 采用 Java 21、Spring Boot 3.4.5；LangChain4j 从 `1.0.0-beta3` 升级到当前官方 1.12.1 文档线，通过官方 BOM 对齐 Core、Provider、Milvus 与 Spring 集成，移除手工固定的 gRPC/Protobuf 版本。
- 前端保留 Vue 3、Vite、Element Plus，建立 TypeScript + Pinia 新骨架；新模块全部使用 TypeScript，旧聊天页面在课程问答阶段完成拆分迁移。
- 使用统一 App Shell，通过路由元数据和服务端权限控制显示学生、教师、管理员功能，不建立三套重复前端。

公共 API 统一为 `/api/v1`：

- 成功响应：`ApiEnvelope<T> { code, message, data, traceId }`；分页为 `{ items, page, size, total }`。
- 错误使用正确 HTTP 状态码，并返回 `{ code, message, details?, traceId }`；不再用 HTTP 200 包装业务错误。
- 服务端从认证 Principal 获取用户身份，任何业务请求不再接受可信的 `userId`。
- 身份接口包括注册、登录、注销、当前用户；登录态使用 Spring Session Data Redis 和 HttpOnly/Secure/SameSite Cookie，并启用 CSRF 防护。
- 学生可注册并通过课程邀请码加入；教师和管理员账号由管理员创建。全局角色为 `ADMIN/USER`，课程角色为 `TEACHER/TA/STUDENT`。
- SSE 使用判别事件：`message.delta`、`citation`、`conversation.title`、`job.status`、`heartbeat`、`done`、`error`；包含 `requestId/traceId/eventId`，所有业务事件必须在唯一终态事件前发送。

核心数据模型：

- 身份课程域：User、UserProfile、Role、UserRole、Semester、Course、CourseClass、CourseMember、CourseInvite、CourseChapter、KnowledgePoint、CourseResource。
- 内容问答域：KnowledgeDocument、KnowledgeChunk、IngestionJob、Conversation、Message、MessageCitation、AnswerFeedback。
- 作业域：Assignment、Question、Rubric、RubricItem、Submission、SubmissionAnswer、Grade、Feedback、TutorInteraction。
- 评审与基础设施：ReviewJob、ReviewReport、AiTrace、AsyncJob、OutboxEvent、AnalyticsSnapshot。
- 所有 Schema 仅由 Flyway 管理；使用 UTC 时间、外键、课程级联合索引、乐观锁和明确状态枚举。

## 三、分阶段实施

### Phase 0：工程基线与 SEForge 骨架

- 完成全量命名迁移、POM 清理、依赖 BOM、`dev/test/prod` 配置、统一异常与日志。
- 建立全新 Flyway V1 Schema，删除相互竞争的旧 SQL 初始化方式。
- 为 backend、worker、frontend 增加 Dockerfile，重写 Compose 并固定镜像版本、健康检查、资源限制和非默认凭据。
- 应用启动不访问云模型或探测 Milvus；AI 不可用时平台以 degraded 状态启动。
- 移除公开测试端点、天气 Tool、假用户数据、硬编码统计、失效入口和生产包中的 main 测试类。
- 建立后端 JUnit/Testcontainers/ArchUnit，前端 ESLint、TypeScript、Vitest、Vue Test Utils、Playwright 和 CI 门禁。
- 建立统一 API Client、错误处理、认证 Store、路由权限和代码分包。

验收：无 API Key、无 Milvus 时 `mvn clean verify` 可通过并启动核心应用；前端 lint/typecheck/test/build 全绿；全新数据库可由 Flyway 一次建成。

### Phase 1：身份、RBAC、课程与资源闭环

- 实现 BCrypt 密码、Redis Session、登录限流、注销、CSRF、审计和课程成员授权。
- 实现课程、教学班、学期、邀请码、成员、章节、知识点及公告/资源元数据。
- 课程原始资料写入 MinIO；此阶段只登记和下载，后续阶段接入解析。
- 建立管理员最小后台：账号、角色、课程审计；教师和学生共用统一门户。

验收：

- 管理员创建教师，教师创建课程、教学班和邀请码并上传资料。
- 学生注册、通过邀请码加入并查看授权课程和资源。
- 跨用户、跨课程和角色越权测试全部返回 403/404，篡改浏览器本地状态不能获得权限。

### Phase 2：AI Core 与可靠异步任务

- 建立 ModelRegistry、ModelRouter、AI Service Factory；业务仅声明 `FAST/REASONING/CODING/EMBEDDING`。
- 默认 DeepSeek 提供聊天/推理，DashScope 提供 Embedding 和备用聊天能力；业务代码禁止直接请求厂商 HTTP API。
- Prompt 改为仓库内版本化资源，AI Trace 记录模型、Prompt 版本、Token、耗时、Tool 调用和状态，默认不记录完整敏感 Prompt/答案。
- 基于当前 LangChain4j AI Services、ChatMemoryProvider、结构化输出和 TokenStream 重建接口；移除旧自由文本 Agent 路由和自定义“推理过程”输出。
- Agent Tool 只能调用经过授权的 Application Service，并携带不可由模型伪造的用户、课程、作业上下文。
- 使用 MySQL Outbox + Redis Streams 实现持久任务队列；同一代码库以独立 worker profile 运行。任务支持幂等、租约、取消、三次指数退避、死信和恢复。

验收：Fake Provider 可验证选模、重试、Fallback、结构化输出校验和 Tool 越权；worker/Redis 重启后任务不丢失；模型故障不会拖垮传统业务。

### Phase 3：统一文档摄取、课程 RAG 与 AI Assistant

- 建立统一摄取链：上传 → MinIO → 解析 → 标准化 → 切片 → Embedding → Milvus → 完成。
- 支持 PDF、PPT/PPTX、DOCX、Markdown、TXT；默认采用约 700 Token、100 Token overlap 的版本化切片策略。
- Chunk Metadata 至少包含 `courseId/documentId/chapter/page/section/source/parserVersion/embeddingVersion`。
- Milvus 按 embeddingVersion 使用版本化集合，所有查询强制下推 courseId Filter；生产环境禁止静默回退内存向量库。
- 支持删除、重索引、重试、重复上传幂等、孤儿索引对账和蓝绿索引切换。
- 重建 Conversation/Message：会话绑定 owner 和 course；数据库保存完整历史，模型上下文使用窗口、摘要与 Token 预算。
- CourseQA Agent 返回独立的答案和 Citation DTO，引用可定位至文件、章节和页码；无可靠依据时明确拒答。
- 前端实现文档任务状态、可靠 POST-SSE、取消/重试、引用卡片、答案反馈和会话历史；不展示内部 Chain-of-Thought。

验收：五种格式均能完成摄取；删除和重索引不留孤儿向量；A 课程查询绝不返回 B 课程内容；流式中断只有一个终态且不保存半条消息。

### Phase 4：作业系统与 AI Tutor

- 实现作业 `DRAFT/PUBLISHED/CLOSED/ARCHIVED` 生命周期及全部目标题型。
- 学生支持自动保存草稿、正式提交、教师配置的截止时间、最大提交次数和个别延期。
- Tutor 操作采用枚举：Hint、Explain、CheckReasoning、AnalyzeError、EvaluateDraft、FullSolution。
- 默认策略为提交前渐进提示、提交后或截止后开放完整解析；教师可按作业配置。
- Tutor Agent 通过 CourseKnowledgeTool、AssignmentTool、KnowledgePointTool、SubmissionTool 获取课程、题目、Rubric 和当前答案，禁止直接访问数据库。
- 草稿、提交、Tutor 记录和最终成绩分别存储，避免 AI 建议覆盖学生原答案。

验收：学生可完整完成“查看作业 → 获取合规辅导 → 保存 → 提交/重交 → 查看反馈”；刷新不丢草稿；Tutor 无法绕过教师设置返回完整答案。

### Phase 5：P1 Review、AI 辅助批改与代码评审

- Document Review 支持 SRS、设计说明、测试报告、README/API 文档，输出结构化的完整性、一致性、可验证性、清晰度和建议。
- Assignment Review 按 RubricItem 给出分项建议、证据、问题和反馈；AI 分数永远是建议值。
- 教师逐项确认或覆盖 AI 建议，并记录修改理由、操作者、模型和 Prompt 版本；只有教师确认后生成 Final Grade。
- Code Review 使用异步 SonarQube 检测，Agent 负责解释 Bug、Code Smell、复杂度、安全与重复问题；不在本阶段执行学生代码。
- 源码压缩包进行 MIME、大小、文件数、Zip Slip 与压缩炸弹检查，不允许扫描任务访问互联网。

验收：三类 Review 均产生可重试、可审计、可导出的结构化报告；模型或 SonarQube 故障不会产生假成功；AI 不能直接发布最终成绩。

### Phase 6：教师 Dashboard 与生产发布

- 基于作业、成绩、知识点、问答反馈和 Tutor 事件生成增量统计快照。
- Dashboard 提供完成率、平均分、成绩分布、知识点正确率、高频问题、薄弱知识点、AI Tutor 用量和错误趋势，并支持班级筛选和分页。
- 完成 Redis 限流、上传配额、Actuator 健康检查、Micrometer 指标、结构化日志、备份恢复、OpenAPI、运维手册和安全响应头。
- 完成 Nginx + frontend + API + worker + MySQL + Redis + MinIO + Milvus + SonarQube 的生产 Compose。
- 移除所有旧 SmartSE Controller、旧 RAG/Agent/图谱/爬虫实现、静态演示页面和不再需要的 Elasticsearch、Neo4j、Jena、Whisper/HanLP 默认依赖。

验收：在 Stub AI 下通过 200 个在线会话、50 个活跃用户、20 路 AI 流、5 个后台重任务的混合负载；传统 API P95 小于 500ms、错误率低于 1%，任务无丢失或重复终态。真实供应商健康时 AI TTFT P95 目标小于 5 秒。

## 四、测试与质量门禁

- 后端：领域单元测试、权限矩阵、Controller 契约、Flyway 空库测试、MySQL/Redis/MinIO Testcontainers、Milvus/SonarQube 发布集成测试、ArchUnit 依赖规则。
- 前端：Store/API/SSE 单元测试、核心组件测试，以及管理员建教师、教师建课上传、学生加入问答、作业提交、教师批改的 Playwright E2E。
- AI：Fake Provider 覆盖超时、429、无效 JSON、Tool 失败、Fallback；架构测试保证厂商依赖只出现在 AI Infrastructure。
- RAG：每门样例课程建立不少于 50 题的 Golden Set，默认要求 Recall@5 ≥ 0.80、引用命中率 ≥ 0.90、无依据问题拒答率 ≥ 0.90。
- 安全：IDOR、CSRF、会话固定、暴力登录、XSS、恶意文件、Zip Slip、跨课程检索和成本滥用测试。
- 故障恢复：注入模型、Redis、Milvus、MinIO、SonarQube 和 worker 故障，验证状态准确、可重试且不会误标 `COMPLETED`。
- CI 每次提交执行编译、静态检查、单元/集成测试和前端构建；完整 Compose E2E、RAG 评测与负载测试作为发布门禁。

## 五、已锁定假设与非首发范围

- 不保留 SmartSE 数据，不提供旧 API 兼容层；SEForge 从全新数据库初始化。
- 首发采用单机容器化、模块化单体和独立 worker，保留未来横向扩展能力。
- 允许课程资料和作业上下文发送至 DeepSeek/DashScope；实现脱敏、最小上下文、调用限额和审计。
- 使用 MySQL + Redis Streams + MinIO + Milvus；移除未实际使用的 Elasticsearch。
- 知识图谱、通用网页爬虫、学习画像、个性化建议、Teacher Agent、在线 Judge、GitHub/Sprint 集成属于 P2/P3，不进入本次“完成”定义。
- 每个 Phase 单独提交、部署和验收；上一阶段门禁未通过时不进入下一阶段。
