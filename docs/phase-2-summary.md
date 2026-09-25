# Phase 2：AI Core 与可靠异步任务交接

日期：2026-09-24。基线提交：`aae6e27f5fef73945719d4595eb6bbba6a090dba`；本次改动在工作树中，尚未提交。Roadmap Phase 不等于产品优先级 P0/P1。

本报告依据用户最新 Phase 2 编码授权；`ai-agent-design.md` 末尾的“编码需单独批准”是设计审查时的历史状态，现已获批准。Agent / Tool 职责未改变。原有 `AGENTS.md`、`codex.md` 和设计文档的用户改动保持不动。

## 1. 审计与定点实施

| 开工分类 | 发现 | 本次处理 |
| --- | --- | --- |
| IMPLEMENTED | Registry / Router / Gateway / AI Services、版本化 Prompt、Trace、窗口记忆、持久任务状态机已经存在 | 保留，不重新实现 |
| PARTIAL | EMBEDDING 枚举与 content 的独立供应商入口并存 | 统一到 Registry → Router → Gateway；旧 provider 类仅作为接口兼容适配器 |
| PARTIAL | 结构化解析不能充分拒绝缺字段/null/业务约束不符 | 严格反序列化、Bean Validation、调用方 validator；验证成功才记录成功；最多一次结构修复 |
| PARTIAL | Streaming 缺少明确取消终态与静默超时兜底 | DONE / ERROR / CANCELLED 互斥；有界重试、启动前 fallback、deadline、忽略迟到 callback |
| MISSING | 缺少可复用的 immutable ToolContext、公共失败码和预算机制 | 新增 ai/tool 公共运行时；不迁移或补完业务 Agent |
| PARTIAL | 重复 Outbox 投递可能覆盖运行态；Redis 丢失 group 后需恢复；持久 Job 不能替代当前权限 | 限制 markQueued 状态、锁定 Outbox 批次、恢复 group、每次 worker 执行重新授权 |
| NEEDS_RUNTIME_VERIFICATION | 既有单元测试不足以证明进程崩溃/真实 Redis 重启恢复 | 新增独立 JVM worker + MySQL / Redis Testcontainers 故障测试 |

## 2. 公共契约与代码证据

源码路径以下均相对于 `backend/src/main/java/com/ustb/seforge/`。

1. **统一能力**：`ai/infrastructure/ModelRegistry.java`、`ModelRouter.java`、`ai/application/AiGateway.java` 接收 FAST / REASONING / CODING / EMBEDDING。聊天默认 DeepSeek，备用 DashScope；客户端构造失败不阻断 Spring Context。Embedding 使用 DashScope，同一版本/维度内重试，不跨不兼容向量空间做假 fallback。
2. **兼容边界**：`content/infrastructure/DashScopeEmbeddingProvider.java` 保留原接口，内部委托 Gateway。不改摄取、检索、Milvus、索引版本切换业务，也不声称 Phase 3 已验收。
3. **Prompt / Trace**：`PromptCatalog` 校验资源名与版本；保留仓库版本化模板。`AiTrace` 记录模型、Prompt 版本、Token、耗时、Tool 元数据与安全错误类型，不记录完整 Prompt/答案。终态不可再次覆盖。关闭 SDK RetryUtils 的 WARN 重试正文日志，避免供应商错误响应泄露敏感内容。
4. **结构化结果**：`AiGateway.completeJson` 在成功前检查 JSON 对象、缺失/null/未知字段、尾随数据、Bean Validation 与可选业务 validator；失败有限修复后明确报错。业务必须提供其领域约束，不宣称通用 JSON 校验能替代所有业务校验。
5. **流式结果**：`AiStreamHandle` 明确唯一终态；Gateway 对 provider 重复回调、取消、无响应超时及中断收敛。已输出 delta 后不换供应商拼接答案；不启用内部 reasoning 输出。
6. **ChatMemory / Service Factory**：保留 request-scoped 窗口记忆和固定 memoryId 校验；工厂新增公共 Session → LangChain4j ToolSpecification / ToolExecutor 绑定。历史业务工具调用路径兼容保留，不借此重构 Tutor。
7. **Tool 安全**：`ai/tool/ToolContext` 不可变，服务端绑定 user/course/assignment/question/submission、allowedTools、requestId/traceId。模型入参递归拒绝安全上下文字段。Session 限制调用数、输入长度、结果数量/长度与超时；fallback 不重置预算。ToolDefinition 必须提供当前授权检查及受控 Application Service 读取，默认只读。超时取消是协作式中断，不能用于不支持中断的危险写操作。
8. **Tool 失败码**：INVALID_ARGUMENT / FORBIDDEN / NOT_FOUND / BUDGET_EXCEEDED 为 non-retryable；TIMEOUT / DEPENDENCY_FAILURE 为 retryable。Trace 的兼容字段 `name` / `latencyMs` 分别对应 toolName / duration，另含 toolVersion、status、resultCount、errorCode、retryable；不存原始参数/结果。旧工具标识为 legacy，不伪造新版元数据。
9. **任务可靠性**：保留 `AsyncJobService` 的事务 Outbox、行锁 claim、lease heartbeat、指数退避、最大三次尝试、恢复与原子业务提交。重复 dispatch 不重置 RUNNING/终态；取消或 lease 失效拒绝晚到业务提交。`JobExecutionAuthorizer` 每次执行重查账号可用性与当前课程教学权限，具体资源状态仍由原 handler 重新加载。

公共 Runtime 不自动授予权限。未来业务 Tool 必须通过受控 Application Service 返回裁剪 DTO；注册不等于执行授权。当前 JobKinds 均为教学人员提交；将来新增学生任务或系统任务时必须显式设计授权策略，不能直接复用教学人员策略。

## 3. 验证记录

最终后端门禁于 2026-09-24 22:47（Asia/Shanghai）完成：`mvn -q -Dapi.version=1.44 clean verify` 退出码 0，174 项测试、0 failures、0 errors、0 skipped。Surefire 报告已回传至 `backend/target/surefire-reports/`；下列结果为本轮证据，不累计重复运行次数。

- 前端 lint、typecheck：通过。
- Vitest：26 项通过；生产构建通过，现存 Element Plus chunk 大小警告保留。
- Phase 1-E Playwright：9 项通过（路由/API mock 核心回归），覆盖角色入口、学号校验、管理员建教师、教师建课上传、学生加入查看资料、审计及归档；不等于真实部署用户验收。
- 无 key 降级启动：`AiDegradedStartupTest` 已通过，真实 Spring Context + H2/MockMvc 验证学生注册、学号登录、课程 API、health；不是生产 MySQL 的全业务部署证明。
- Provider 协议：`AiRuntimeProtocolTest` 使用本地 HTTP Stub，覆盖三类聊天能力、429 重试后 fallback、超时、供应商不可用及 embedding 协议，无真实供应商费用或密钥依赖。
- Tool / Structured / Streaming：`AiGatewayTest`、`AuthorizedToolRuntimeTest`、`ModelRegistryTest`、`AiTraceServiceTest`；worker 权限由 `JobExecutionAuthorizerTest` 验证。
- 真实任务恢复：`job/runtime/JobRuntimeIntegrationTest` 使用隔离的 MySQL 8.4.4、Redis 7.4.2 与独立 worker JVM。最终 4 项通过（180.7 秒），包括 run_id 变化、stream 丢失和运行中 worker 重建 group。
- 失败历史透明记录：早期定向运行虽 4 项通过，但缺少重启成功强断言，不作为 Redis 重启有效证据。首次全量门禁 174 项中 1 项失败：新增 run_id 断言发现 Redis 相对可执行路径导致 DEBUG RESTART 失败。测试容器改用绝对可执行路径并检查命令错误后，真实恢复通过。下一次全量运行暴露既有测试顺序依赖：csrf() 辅助器改变共享 CsrfFilter 的 token repository，使 AdminAuditLogIntegrationTest 的真实 SPA Cookie/header 登录返回 403。仅新增 BEFORE_CLASS 测试上下文隔离，不改生产 CSRF 或登录逻辑；最终该测试与全量门禁均通过。
- `git diff --check` 通过。MySQL 空库 V1～V4 迁移与既有 Redis/MinIO Testcontainers 测试均实际执行，无 Docker 跳过项。

### 完成定义逐项映射

| # | 条件 | 代码与验收证据 |
| --- | --- | --- |
| 1 | 四种能力统一 Runtime | ModelRegistry / ModelRouter / AiGateway；AiRuntimeProtocolTest |
| 2 | 业务无直接供应商调用 | content 兼容适配器；ArchitectureTest 模型客户端依赖规则 |
| 3 | Prompt 版本化 | PromptCatalog 与 resources/prompts 仓库资源；Spring Context 启动与调用测试 |
| 4 | Trace 脱敏元数据 | AiTrace / AiToolCall；AiTraceServiceTest、AuthorizedToolRuntimeTest |
| 5 | 结构化错误不假成功 | completeJson；AiGatewayTest 的无效、缺字段、修复、业务 validator 场景 |
| 6 | Streaming 唯一终态 | AiStreamHandle / AiGateway；重复 callback、中断、静默超时、cancel 测试 |
| 7 | ToolContext 不可伪造 | ai/tool；六种安全字段伪造、越权、预算和重新授权测试 |
| 8 | disabled / 无 key 降级 | ModelRegistryTest、AiDegradedStartupTest |
| 9 | 传统业务不依赖 AI 可用 | AiDegradedStartupTest；既有 Phase 1 API 测试与前端回归 |
| 10 | Outbox / Streams / worker 幂等恢复 | AsyncJobTest、AsyncJobRecoveryServiceTest、AsyncJobCompletionServiceTest、JobRuntimeIntegrationTest |
| 11 | worker / Redis 重启实测 | 独立 JVM 强杀；Redis 进程重启并丢失内存队列，再从数据库恢复 |
| 12 | cancel / retry / dead-letter / lease / recovery | 真实恢复 4 场景与任务领域/服务测试 |
| 13 | backend clean verify | PASS：174 项，0 失败/错误/跳过，Maven 退出码 0 |
| 14 | Phase 1-E 不回归 | 后端全量 verify + 前端 26 单测、9 项 Phase 1-E 浏览器回归 |
| 15 | 阶段交接 | 本报告；用户验收与 Phase 3 授权另行处理 |

环境说明：本机 Windows JDK 的 HTTP Stub 初始化曾出现 loopback/WEPoll 错误，因此后端最终门禁改用本地已有 Maven / Temurin 21 Linux 镜像运行。Docker Desktop 容器 stop/start 会改变随机映射端口；Redis 故障测试改为隔离测试容器内 `DEBUG RESTART` 重启真实 Redis 进程并丢失 stream/group，保持网络端点不变。该 debug 选项只存在测试容器，不修改生产 Redis。worker 使用就绪标记独立等待启动，不延长实际恢复断言时限。

可复现命令：后端在 Java 21 + 可用 Docker 的环境执行 `mvn -Dapi.version=1.44 clean verify`。本机实际采用 `maven:3.9.9-eclipse-temurin-21-alpine`，backend 挂载到 `/workspace`，Maven 缓存挂载到 `/root/.m2`，挂载 Docker socket，设置 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`；仅对测试进程开放宿主 Docker。前端执行 `npm run lint`、`npm run typecheck`、`npm test`、`npm run build`；浏览器定向命令为 `node node_modules/@playwright/test/cli.js test e2e/core-flows.spec.ts --workers=1 --grep-invert '问答|Tutor|Rubric|最终成绩'`。

最终运行先将 backend 原样复制到容器临时目录 `/tmp/phase2`，在那里 clean verify，减少 Windows bind mount 类加载开销；结束后仅回传测试报告与 worker 日志。附加的 JAR 回传通配符未匹配项目固定产物名 `seforge-backend.jar`，所以本轮不交付宿主机 JAR；这发生在 Maven 成功退出后，不影响验证结果。运行命令保留 Maven 原始退出码，未跳过打包或测试。剩余警告为 Flyway 的 MySQL 8.4 支持提示、Mockito 动态 agent、PDF fallback 字体及前端 chunk 大小，未在本阶段扩大依赖升级范围。

本轮使用 Context7 技能核对 LangChain4j 1.12.1 的 AI Services / TokenStream / ToolExecutor 与 Spring Data Redis Streams 文档；仅用于公共接入契约，不改变已冻结的业务 Agent 职责。

## 4. 未验证边界与停止条件

- 不调用真实 DeepSeek/DashScope；协议 Stub 不证明供应商当前 SLA、账号额度或真实回答质量。
- 不验收 Milvus、RAG Golden Set、Course QA UI、Tutor、Review、SonarQube、Grade、Dashboard、Teacher Agent 或负载指标。现有 Phase 3～6 代码保留。
- 不修改历史 Flyway、不清空已有数据库/volume、不关闭 CSRF/RBAC、不更改 Phase 1-E 前端或账号契约。
- 测试使用隔离容器和测试专用 handler，不触碰当前 SEForge 业务数据。进程故障证明数据库幂等提交，不宣称外部非事务副作用具备 exactly-once。
- Phase 3 的建议起点：经负责人另行批准后，将 Course QA 保持服务端授权预检索，使用本阶段 Gateway/Trace/Job 契约；再单独进行课程隔离与 RAG 验收。不是本轮继续开发的授权。

Engineering Verification: **PASS**  
User Acceptance: **PENDING**  
Permission to start Phase 3: **NOT GRANTED**
