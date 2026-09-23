# SEForge Phase 2+ 开发现状报告

> 审计日期：2026-09-22  
> 审计范围：`docs/roadmap.md` 中 Phase 2～Phase 6  
> 审计方式：只读检查源码、配置、测试与现有门禁资料；未修改任何业务源码、配置或测试。  
> 结论口径：本报告描述“当前已经开发到哪里”，不把代码存在等同于阶段验收通过。

## 1. 总体结论

| 阶段 | 当前状态 | 简要判断 |
| --- | --- | --- |
| Phase 2：AI Core 与可靠异步任务 | PARTIAL | AI 抽象、Fallback、结构化输出、授权 Tool、AI Trace、Outbox/Redis Streams worker 已实现；Embedding 未统一进入 ModelRegistry，真实 Redis 重启恢复与供应商集成未验收。 |
| Phase 3：文档摄取、课程 RAG 与 AI Assistant | PARTIAL | 摄取、解析、切片、Embedding、Milvus、会话、Citation、SSE 和前端主体已实现；真实 Milvus、完整索引修复、Golden Set 和全栈流中断验收缺失。 |
| Phase 4：作业系统与 AI Tutor | PARTIAL | 作业生命周期、全题型、草稿/提交、延期、Tutor 策略与授权工具、前端流程均已实现；目前验收主要来自单元测试与 mock E2E，尚无真实后端全流程验收。 |
| Phase 5：Review、AI 辅助批改与代码评审 | PARTIAL | 三类 Review、结构化校验、Sonar 网关、压缩包防护、AI 建议与教师最终确认已实现；真实 SonarQube、文档/作业 Review 全链路和评分审计集成未验收。 |
| Phase 6：Dashboard 与生产发布 | PARTIAL | Dashboard 聚合与快照、限流/配额/指标/OpenAPI/运维手册、生产 Compose 和负载脚本已存在；结构化 JSON 日志、完整恢复演练、正式负载结果和 TTFT 结果缺失。 |

当前没有足够证据将任一 Phase 2+ 阶段判定为完整 `PASS`。现状更接近“主体功能代码已提前落地，发布级集成和验收尚未完成”。

## 2. Phase 2：AI Core 与可靠异步任务

### 2.1 任务逐项状态

| Roadmap 任务 | 状态 | 当前实现 |
| --- | --- | --- |
| ModelRegistry、ModelRouter、AI Service Factory；业务声明能力类型 | PARTIAL | 已有 `ModelCapability`、`ModelRegistry`、`ModelRouter`、`AiServiceFactory` 和统一 `AiGateway`。`FAST/REASONING/CODING` 可路由；枚举虽然包含 `EMBEDDING`，但 ModelRegistry 未注册 Embedding endpoint，Embedding 仍由 content infrastructure 的 `DashScopeEmbeddingProvider` 单独提供。 |
| DeepSeek 默认聊天/推理，DashScope Embedding 与备用聊天；业务不直连供应商 HTTP | IMPLEMENTED | DeepSeek 注册为主要聊天候选，DashScope 注册为聊天备用；Embedding provider 位于 infrastructure。业务服务经 `AiGateway`/`EmbeddingProvider` 使用能力，未发现业务模块直接手写供应商 HTTP 请求。 |
| 版本化 Prompt、AI Trace、Token/耗时/Tool/状态记录，默认不保存完整敏感内容 | IMPLEMENTED | `resources/prompts/*/v1.txt`、code-review v2 已存在；`PromptCatalog` 与 `AiTraceService` 记录版本、provider/model、token、耗时、工具和状态，Trace 不持久化完整 Prompt/回答。 |
| LangChain4j AI Services、ChatMemoryProvider、结构化输出和 TokenStream | IMPLEMENTED | `AiServiceFactory` 使用 AI Services；`AiGateway` 支持同步、结构化 JSON 修复、TokenStream、流取消、Fallback 和 request-scoped memory；旧自由文本 SmartSE Agent 已移除。 |
| Agent Tool 仅调用授权 Application Service，并绑定不可伪造上下文 | IMPLEMENTED | Tutor 使用 request-scoped `AuthorizedTutorTools`，服务端绑定 assignment/question/course/user；测试证明模型参数不能覆盖授权上下文，并记录 Tool 调用。 |
| MySQL Outbox + Redis Streams worker，幂等、租约、取消、三次退避、死信和恢复 | IMPLEMENTED（未完成实机验收） | `AsyncJob`、`OutboxEvent`、publisher、worker profile、consumer group、lease heartbeat、取消、原子完成、指数退避、dead letter 与 recovery scheduler 均存在；领域/服务单元测试覆盖租约、重试、取消和 Redis 丢失后的重发建模。 |

### 2.2 已有测试证据

- AI/job 相关后端测试约 26 项。
- Fake/Scripted model 覆盖主模型超时或 429 后 Fallback、结构化输出无效后的修复与拒绝、流式启动失败 Fallback、取消传播、唯一终态、授权 Tool 必须调用。
- Job 测试覆盖三次失败进入 dead letter、取消优先、租约所有权、过期租约恢复、重复完成幂等、事务提交后事件发布。
- `ModelRegistryTest` 验证 AI 禁用或缺少凭据时保持 degraded，且不会创建可用远程候选。

### 2.3 尚未达到的验收证据与风险

- `EMBEDDING` 尚未真正纳入统一 ModelRegistry/ModelRouter，公共能力声明与实际实现仍有两套入口。
- 未使用真实 Redis 完成“worker/Redis 重启后任务不丢失”的发布级测试。本机 Redis Testcontainers 曾被 Windows JDK/Netty selector 环境问题阻断。
- 未对真实 DeepSeek/DashScope 验证限流、超时、供应商协议差异、Token 统计和流式取消。
- 没有完整的 API + Redis + worker 进程级故障注入结果；当前可靠性结论主要来自领域与 mock 测试。

**Phase 2 当前结论：PARTIAL。** 建议正式继续开发时，先统一 Embedding 路由，再在 Linux CI/稳定 Docker 环境完成 Redis/worker 恢复验收。

## 3. Phase 3：统一文档摄取、课程 RAG 与 AI Assistant

### 3.1 任务逐项状态

| Roadmap 任务 | 状态 | 当前实现 |
| --- | --- | --- |
| 上传 → MinIO → 解析 → 标准化 → 切片 → Embedding → Milvus | IMPLEMENTED | `KnowledgeDocumentService` 创建持久任务，worker 中的 `KnowledgeIngestionService` 打开对象、解析、切片、Embedding、写向量并原子提交 chunk/状态。部分失败向量会尝试清理。 |
| PDF、PPT/PPTX、DOCX、Markdown、TXT；约 700/100 切片 | IMPLEMENTED | `DocumentParserService` 支持上述格式；`DocumentSplitters.recursive(700, 100, ...)` 已配置；解析测试覆盖 PDF、DOCX、PPTX、旧 PPT、Markdown 和 TXT。 |
| 完整 Chunk Metadata | IMPLEMENTED | chunk/向量 metadata 包含 courseId、documentId、chapterId、page、section、source、parserVersion、embeddingVersion。 |
| 版本化 Milvus 集合，查询强制 courseId Filter，生产不静默回退内存 | IMPLEMENTED（未实机验收） | `MilvusVectorIndex` 按 embedding version 建集合，add/search/delete/list 全部要求 courseId；生产 bean 为 Milvus 实现，内存索引仅存在于测试支持代码。 |
| 删除、重索引、重试、重复上传幂等、对账和蓝绿切换 | PARTIAL | 删除、重索引、通用任务重试、checksum 幂等、active/write version 和孤儿向量删除均已实现。对账发现“数据库存在但索引缺失”的向量时只报告 missing IDs，并未自动重建，因此完整自愈仍未闭环。 |
| Conversation/Message、完整历史、窗口/摘要/Token 预算 | IMPLEMENTED | 会话绑定 owner/course；历史、消息、引用和反馈持久化；`ConversationContextBuilder` 使用滚动摘要、连续窗口和保守 Token 预算。 |
| CourseQA 返回答案与 Citation，无依据拒答 | IMPLEMENTED | 检索结果以独立 citation 事件/DTO 返回；空 evidence 使用固定拒答文案，不调用模型；引用包含文件、章节、页码/section。 |
| 前端任务状态、POST-SSE、取消/重试、引用、反馈、历史；不展示 CoT | IMPLEMENTED | Courses 页面监控摄取任务并支持取消/重建索引；Assistant 页面支持 POST-SSE、取消、重试、Citation 卡片、反馈和历史。未发现内部 Chain-of-Thought 展示。 |

### 3.2 已有测试证据

- content/conversation 相关后端测试约 20 项。
- 多格式解析均有单元测试。
- 对账测试证明只删除当前 course/version 的孤儿向量并报告缺失向量。
- Conversation 安全测试验证 owner/course 隔离；上下文测试验证连续窗口、摘要水位和 Token 预算。
- SSE 服务测试验证 provider 重复回调不能产生第二个终态。
- 前端有 API/SSE 单元测试，以及 mock 场景的带引用问答 E2E。

### 3.3 尚未达到的验收证据与风险

- 没有真实 Milvus 发布集成结果，未证明真实集合过滤、删除、分页列举和蓝绿切换在目标版本上全部工作。
- missing vector 目前不会自动触发修复或文档重索引，对账闭环不完整。
- 没有每门课程不少于 50 题的真实 Golden Set 结果；仓库只有示例数据与执行器。
- 没有真实全栈证明“A 课程查询绝不返回 B 课程内容”；当前证据主要来自过滤实现和内存测试替身。
- 流式中断代码会取消 provider 且只在完成回调保存 assistant message，但缺少断网/代理中断下的数据库集成测试。
- 真实 Embedding、Milvus 和模型组合下的 Recall@5、引用命中率、拒答率均未知。

**Phase 3 当前结论：PARTIAL。** 功能链路主体已存在，关键短板是向量基础设施、检索质量和中断一致性的发布验收。

## 4. Phase 4：作业系统与 AI Tutor

### 4.1 任务逐项状态

| Roadmap 任务 | 状态 | 当前实现 |
| --- | --- | --- |
| 作业生命周期与目标题型 | IMPLEMENTED | `DRAFT/PUBLISHED/CLOSED/ARCHIVED` 状态机已实现；支持单选、多选、判断、简答、分析、设计和代码题。发布要求至少一个题目及已发布的非空 Rubric。 |
| 自动保存草稿、正式提交、截止时间、最大次数和个别延期 | IMPLEMENTED（存在前端边界风险） | 后端草稿 upsert、完整性校验、提交尝试次数、迟交策略和学生延期均已实现；前端 900ms debounce 自动保存。组件卸载时会取消尚未触发的 timer 而不主动 flush，极快刷新/跳转仍可能丢失最后 900ms 内修改。 |
| Tutor 操作枚举 | IMPLEMENTED | 六类操作全部进入后端枚举、API 类型和前端选择器。 |
| 提交前渐进提示、提交/截止后完整解析，教师可配置 | IMPLEMENTED | 默认策略禁止提交前完整答案，提交或截止后开放；教师可配置 enabled operations、完整解析条件、迟交与延期。 |
| Tutor Agent 通过四类 Tool 读取授权上下文，不直连数据库 | IMPLEMENTED | `TutorService` 注册一个绑定服务端上下文的 `AuthorizedTutorTools`，要求调用 Assignment、Submission、CourseKnowledge、KnowledgePoint 四类工具；工具内部调用 application services/repositories 的封装，模型不能提交 user/course 标识覆盖上下文。 |
| 草稿、提交、Tutor 记录和最终成绩分开存储 | IMPLEMENTED | Submission/SubmissionAnswer、TutorInteraction、Grade/Feedback 为独立模型；Tutor 建议不会写回学生原答案。已提交 attempt 不可修改，新 attempt 单独创建。 |

### 4.2 已有测试证据

- assignment 相关后端测试约 28 项。
- 测试覆盖生命周期、发布时间/次数校验、全部题型完整性、提交后不可修改、附件授权/类型/大小/Zip Slip/累计配额。
- Tutor 测试覆盖提交前禁止完整解析且不调用 AI、授权 Tool 对象、旧提交不能错误解锁当前草稿、默认渐进策略和个别延期。
- 前端 mock E2E 覆盖学生保存草稿、Tutor Hint、正式提交，以及教师配置题目/Rubric/Tutor 策略并发布。

### 4.3 尚未达到的验收证据与风险

- 目前没有连接真实后端、数据库、Session、worker 和模型 Stub 的作业全流程 E2E；Playwright 场景主要 mock API。
- 没有刷新发生在 debounce 等待窗口内的草稿持久性测试；现有卸载逻辑可能丢掉尚未发送的最后一次编辑。
- 没有针对所有 Tutor 操作组合、作业关闭/重新发布、迟交与多次重交并发竞争的完整 HTTP 权限矩阵。
- AI Tutor 的真实 Tool Calling、Prompt 注入防护和模型越权拒绝仍仅由 fake model/服务单元测试支撑。

**Phase 4 当前结论：PARTIAL。** 领域功能较完整，但需要真实后端 E2E，并应特别验证自动保存离开页面时的最后一次写入。

## 5. Phase 5：Review、AI 辅助批改与代码评审

### 5.1 任务逐项状态

| Roadmap 任务 | 状态 | 当前实现 |
| --- | --- | --- |
| Document Review：SRS、设计、测试报告、README/API 文档及四维结构化输出 | IMPLEMENTED（测试覆盖不完整） | 前端提供五类文档类型；后端解析原文并要求 completeness、consistency、verifiability、clarity 四个维度、issues 和 recommendations。documentKind 服务端仅做长度限制，没有枚举白名单。 |
| Assignment Review 按 RubricItem 产生建议、证据、问题和反馈；AI 仅给建议 | IMPLEMENTED | 结构化模型和 validator 要求每个 RubricItem 恰好出现一次、分数不越界且合计一致；`GradeSuggestionService` 只写 AI 建议与 AI feedback，不能确认最终成绩。 |
| 教师逐项确认/覆盖，记录理由、操作者、模型和 Prompt；确认后才生成最终成绩 | IMPLEMENTED（直接测试不足） | `GradeService.confirm` 要求逐项确认，覆盖 AI 分值时强制填写 reason，记录 grader/final score；学生只可见 CONFIRMED grade。模型和 Prompt 版本保存在 Grade。缺少 GradeService 的直接单元/HTTP 集成测试。 |
| Code Review 使用异步 SonarQube，Agent 解释质量/安全问题，不执行学生代码 | IMPLEMENTED（未实机验收） | worker 调用固定 SonarScanner executable，轮询 SonarQube authoritative result，再让 AI 逐 finding 解释；超时、非零退出、服务失败不会生成成功报告。未发现执行学生程序的路径。 |
| ZIP MIME/大小/文件数/Zip Slip/压缩炸弹检查，扫描任务无互联网 | IMPLEMENTED（网络隔离未运行验证） | `SecureArchiveValidator` 检查 MIME、扩展名、签名、大小、数量、嵌套 archive、路径穿越和压缩比；临时 workspace 自动删除。Compose worker 仅在 internal data network，通过固定 allowlist egress proxy 访问模型。 |

### 5.2 已有测试证据

- review 相关后端测试约 24 项。
- 结构化 validator 覆盖四个文档维度、Rubric 完整性/总分、Sonar finding 不得遗漏或伪造。
- Code Review 服务测试覆盖持久化 authoritative findings、Sonar 失败不生成报告。
- Sonar gateway 测试覆盖 disabled、编排、服务失败、固定 executable、Token 不进入命令参数、超时终止、非零退出、缺少 binary 和无启动探测。
- Archive 测试覆盖安全解压、Zip Slip、嵌套压缩包、学生自带 scanner 配置、对象变更、非 ZIP MIME、压缩炸弹和文件数限制。
- 前端 mock E2E 覆盖教师查看 AI 建议并确认最终成绩。

### 5.3 尚未达到的验收证据与风险

- 没有真实 SonarQube 容器集成和完整 code scan 报告产物。
- Document Review 与 Assignment Review 缺少从提交任务、worker、AI Stub、报告导出到重试的完整集成测试；当前 ReviewExecutionService 的深入测试集中在 Code Review。
- 教师逐项确认、覆盖理由、最终成绩可见性缺少直接 GradeService/HTTP 集成测试。
- 文档类型后端没有枚举限制，调用方可提交 UI 之外的任意短字符串作为 documentKind。
- worker 无互联网策略仅完成 Compose 网络设计，尚未通过运行态 egress 测试证明 scanner 子进程不能访问公网。

**Phase 5 当前结论：PARTIAL。** 主要实现已经存在，但真实 Sonar、评分确认和三类 Review 的端到端验收尚未完成。

## 6. Phase 6：教师 Dashboard 与生产发布

### 6.1 任务逐项状态

| Roadmap 任务 | 状态 | 当前实现 |
| --- | --- | --- |
| 基于业务事件生成增量统计快照 | PARTIAL | Analytics worker 使用涵盖成员、作业、知识点、提交、成绩、问答、Tutor、Review 等表的 source cursor；数据未变化时复用快照，变化时生成带 period 的新快照。当前属于“增量检测 + 全量重算当前范围”，不是逐事件/逐行增量聚合。 |
| Dashboard 指标、班级筛选和分页 | PARTIAL | 完成率、平均分、分布、知识点正确率/薄弱点、高频问题、Tutor 用量、问答反馈和错误趋势已实现；后端快照历史支持分页，前端支持班级筛选但固定读取最近 30 条，尚无可操作的历史分页控件。 |
| 限流、配额、健康、指标、日志、备份恢复、OpenAPI、安全响应头 | PARTIAL | Redis API 限流带本地降级、文档/附件配额、Actuator/Prometheus、OpenAPI、traceId/requestId 日志 pattern、CSP/frame deny、运维与备份恢复手册均已存在。应用日志仍是文本 pattern，不是结构化 JSON；备份恢复仅有手册，未见演练产物。 |
| 完整生产 Compose | IMPLEMENTED（未整栈启动验收） | Compose 包含 frontend、API、worker、MySQL、Redis、MinIO、etcd/Milvus、可选 SonarQube/PostgreSQL、AI egress 和 k6 profile，并有固定镜像、健康检查、资源限制和 internal data network。 |
| 清除旧 SmartSE/RAG/Agent/图谱/爬虫及多余依赖 | IMPLEMENTED | 工作树中的旧 SmartSE 包、Controller、Neo4j/Jena/爬虫/静态演示文件已删除；当前源码模块为 SEForge 新结构。 |

### 6.2 已有测试与资料

- analytics 相关后端测试约 10 项。
- Dashboard SQL 测试覆盖完成率、成绩、知识点、Tutor、反馈、失败数、课程/班级范围隔离。
- Snapshot 测试覆盖 worker-only 生成、请求线程只读 materialization、cursor 未变复用、cursor 变化后生成新快照。
- Cursor 测试覆盖插入、修改、删除以及其他课程变更不污染当前课程。
- 前端 analytics API 测试覆盖 long 数值标准化、班级参数、分页契约和错误趋势生成。
- 已提供 `docs/load-test/mixed-load.js`、RAG evaluator、示例 Golden Set 和运维手册。

### 6.3 尚未达到的验收证据与风险

- 未发现 `load-test-summary.json` 或等价结果，不能确认 200 会话、50 活跃用户、20 AI 流、5 重任务的门禁已经执行。
- 没有传统 API P95、错误率、任务重复终态或真实供应商 TTFT P95 的测量结果。
- k6 脚本明确不测真实流式 TTFT，仓库也没有专用 TTFT 采集产物。
- 日志尚未达到结构化 JSON 输出；现有格式只是带 MDC 字段的文本日志。
- 没有备份恢复演练报告、RPO/RTO 记录或完整 Compose 的健康截图/机器可读结果。
- Dashboard 的“增量”是变更检测后全量重算；大数据量下的扫描成本和快照生成耗时未评估。
- 真实课程 Golden Set 尚不存在；示例文件不足 50 题，不能用于发布验收。

**Phase 6 当前结论：PARTIAL。** 发布骨架和大部分业务统计已实现，但性能、恢复、可观测性和完整生产环境仍缺少运行证据。

## 7. 当前 Phase 2+ 测试覆盖概览

| 范围 | 后端测试数量（按 `@Test` 静态计数） | 当前测试特征 |
| --- | ---: | --- |
| Phase 2 AI/job | 26 | Fake provider、结构化输出、Fallback、Tool、取消、job 状态机/租约/恢复。 |
| Phase 3 content/conversation | 20 | 多格式解析、权限、对账、上下文摘要、SSE 唯一终态；缺真实 Milvus。 |
| Phase 4 assignment/tutor | 28 | 生命周期、题型、提交、附件、Tutor 策略/工具；缺真实 HTTP 全流程。 |
| Phase 5 review | 24 | Validator、Archive、Sonar gateway、Code Review；Document/Assignment Review 与 Grade 集成不足。 |
| Phase 6 analytics | 10 | 聚合 SQL、班级隔离、cursor 与 materialized snapshot；缺负载/恢复实测。 |

前一轮完整后端门禁为 139 tests、0 failures、0 errors、3 Docker-related skipped；前端为 8 files / 18 tests 通过，production build 通过。该结果说明现有代码在 stub/test profile 下可编译运行，但不能替代 Phase 2+ 的真实外部基础设施验收。

## 8. 建议的后续开发顺序

本节仅给出建议，不代表本轮继续开发：

1. **正式收口 Phase 2**：统一 Embedding 的 ModelRegistry/Router 契约，在 Linux CI 完成 Redis/worker 重启与任务不丢失测试，并用 Stub HTTP provider 做进程级故障注入。
2. **验收 Phase 3 基础设施**：真实 Milvus 集成、missing vector 自动修复、跨课程检索测试、每门课程 50+ Golden Set，以及网络中断下的唯一终态/不保存半条消息。
3. **补齐 Phase 4 全栈 E2E**：真实后端覆盖自动保存、刷新、提交/重交、延期、Tutor 策略；修正离开页面前 pending autosave 的持久化策略。
4. **验收 Phase 5**：真实 SonarQube、三类 Review 的 worker 全链路、GradeService 权限/审计/覆盖理由测试、运行态网络隔离。
5. **最后收口 Phase 6**：结构化 JSON 日志、完整 Compose、备份恢复演练、RAG 评测、混合负载与 TTFT 专用采集。

## 9. 最终判断

- Phase 2+ 并非空白：主要领域模型、API、worker、前端页面和大量单元测试已经存在。
- 当前最成熟的是“代码结构和领域逻辑”，最薄弱的是“真实外部依赖、进程级故障恢复、非 mock E2E、质量评测和性能发布证据”。
- 在正式宣布某一 Phase 完成前，应按阶段逐个执行其 Roadmap 验收，不能用当前 Phase 0/1 门禁或 mock E2E 代替。
- 本轮只新增此报告，没有修改 Phase 2+ 代码。
