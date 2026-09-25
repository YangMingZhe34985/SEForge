# SEForge 开发过程记录书

本文沿开发顺序记录项目如何从 SmartSE 转为 SEForge，以及各阶段的设计取舍、落地结果和仍需注意的边界。阶段结论以每阶段最后一份总结及后续实际验证为准；旧缺口描述和已经修复的中间测试结果不作为当前状态。

“Engineering Verification: PASS”表示相应阶段的代码、测试或指定运行证据通过，不等于项目负责人的“User Acceptance”，也不表示已经部署到公网生产环境。

## SmartSE → SEForge：项目目标与技术底座

SmartSE 的起点是多智能体问答。SEForge 将目标扩展为软件工程课程教学与实践平台：学生有学号身份并加入课程，教师维护教学内容、作业与评阅，课程资料能为问答和 Tutor 提供有出处的证据，教师最终控制评分与课程权限。

总体采用 Vue 3 前端、Spring Boot 模块化单体 API、共用代码但独立进程运行的 AI/任务 worker，以及 MySQL、Redis、MinIO 和 Milvus。SonarQube 用于可选代码静态评审。数据库由 Flyway 管理；服务端 Principal 和课程授权是权限事实来源。阶段迁移从全新 SEForge Schema 开始，不提供 SmartSE 数据兼容层。

## Phase 0 / Phase 1：工程骨架与身份课程闭环

### 阶段目标

建立能够独立启动、测试和构建的 SEForge 骨架，再交付身份、角色、课程、班级、邀请码和课程资源的基础闭环。

### 主要设计决策

- 统一使用 `com.ustb.seforge`、`seforge-backend`、SEForge 数据库和配置命名；Java 21、Spring Boot 3.4.5 与 LangChain4j BOM。
- 新数据库 Schema 只由 Flyway 创建和演进。
- Vue 采用一个应用和共享 Shell，按 Admin、Teacher、Student 工作区呈现功能；路由与本地状态不授予服务端权限。
- 学生以学号注册和登录；教师与管理员由管理员建立，学生可凭邀请码入课。
- Spring Session 存在 Redis，使用 HttpOnly Cookie 与 CSRF。测试端口和默认 Compose 凭据用于本机/隔离环境，不可原样用于公网部署。

### 关键实现

创建身份、课程、教学班、成员、学期、邀请码、章节、知识点、公告和资源基础 API、服务及前端入口。课程文件写入 MinIO，数据库保存资源元数据。提供管理员创建教师的 Bootstrap 和最小账号/课程治理页面；统一 API envelope、前端 API client、Pinia 认证 Store、路由守卫和错误处理。旧初始化 SQL 和公开演示入口退出主线，CI 开始执行后端及前端基本门禁。

随后增加 Phase 1-E 的身份与角色工作区收口：补齐管理员账号治理与 CSV 导入、教师课程/教学班/成员/邀请码/章节/知识点/公告管理，以及学生资料页和学号入口。对存量空学号不猜测、不清库；通过原身份安全补录学号。管理员治理权限与教师的课程教学资格分开校验。

### 重要问题与解决方式

- 学生注册在 Docker 下遇到 403，根因是跨域 Origin 与前端转发端口不一致，修正 nginx Host 转发及允许 Origin 配置，保持 CSRF 开启。
- Vite 初期没有读取有效的 API 代理变量；加入 development mode 配置，默认代理到本机 API。
- 管理员工作区看似不存在，根因是空数据库没有 Bootstrap 管理员，而不是缺少页面。通过一次性初始化账号打破首次配置闭环；管理员创建成功后应移除初始化凭据。
- Bootstrap 凭据冲突曾使 API 启动失败；改为告警并继续启动，要求管理员排查配置，避免启动循环。
- 测试辅助工具曾污染共享 CSRF Filter 状态，导致测试顺序相关的 403；隔离测试上下文并恢复 Cookie CSRF repository，而不是放松生产安全规则。

### 最终验证结果

Phase 0 与 Phase 1 的最终总结判定验收通过。Phase 1-E 阶段记录了独立真实 Compose / MySQL / Redis Session / MinIO 和浏览器闭环；后续冻结回归还覆盖身份、课程授权、跨课程 IDOR 和审计。2026-09-25 Phase 6 最终 clean verify 中 PhaseOneAuthorizationIntegrationTest 的 2 项继续通过。

### 已知边界

自动化工程验证不替代负责人用户验收。Phase 1-E 的 2 条依赖独立真实环境的 Playwright 用例没有在 Phase 6 最终浏览器批次中重跑；真实后端授权集成则包含在最终后端门禁内。存量历史账号或旧测试数据清理由业务负责人另行安排。审计导出、邀请码吊销等后来列出的增强项不应误写成首个阶段已交付能力。

## Phase 2：AI Core 与可靠异步任务

### 阶段目标

统一模型接入、Prompt、Trace、结构化输出、流式状态与工具执行；让长任务能跨 worker/Redis 故障恢复，并在模型不可用时保留普通业务启动能力。

### 主要设计决策

- 业务按 `FAST / REASONING / CODING / EMBEDDING` 声明能力，经 ModelRegistry、ModelRouter 与 AiGateway 访问 LangChain4j Provider。
- Prompt 版本与代码同库管理。AI Trace 保留模型、版本、Token、耗时、Tool 与状态元数据，不存完整敏感 Prompt 或答案。
- ToolContext 由服务端创建，身份、课程和资源 ID 不可由模型覆盖；预算、授权和失败语义属于 Runtime 契约。
- MySQL 事务 Outbox 是任务持久来源，Redis Streams 用于分发；worker 租约、心跳、重试、取消、死信和恢复共同决定任务终态。
- 已输出部分流式内容后不切换供应商拼接两家模型的回答；完成、失败、取消互斥。

### 关键实现

完成 ModelRegistry/Router、AiServiceFactory、AiGateway、版本化 Prompt、AI Trace、ChatMemory、结构化 JSON 校验、TokenStream 和请求级 Tool Runtime。Provider 故障时有界重试和 fallback；不可信 JSON 需字段与领域 validator 均通过才成功。Outbox 与 worker 执行任务，并在每次处理时重新验证用户与课程权限。

### 重要问题与解决方式

- Embedding 最初独立于聊天模型入口；Phase 2 将其纳入统一 Gateway，同时保留 content 层适配器，不让业务依赖厂商 SDK。
- 早期 Redis 恢复测试没有证明真实重启后的恢复成功。使用独立 worker JVM 与真实 MySQL/Redis Testcontainers 强化断言，覆盖 Redis stream/group 丢失后从持久任务恢复。
- Redis 测试依赖相对可执行路径，曾使重启命令失败；改用容器内绝对可执行路径并检查命令返回。
- 一次 CSRF 集成测试问题来自测试上下文共享，不改动登录安全行为，而以测试隔离解决。

### 最终验证结果

Phase 2 总结记录 174 项后端测试全通过、前端及 Provider/Tool/结构化/流式测试通过，并以真实 Redis 与独立 worker JVM 验证故障恢复。后续 Phase 3～6 的全量门禁持续包含 Runtime 回归；Phase 6 最终 clean verify 共 200 项通过，其中 JobRuntimeIntegrationTest 4 项通过。真实 DeepSeek/DashScope 质量或 SLA 没有由 Stub 测试证明。

### 已知边界

错误处理能保护任务和 Trace 的终态，但不能让外部非事务副作用成为数学意义的 exactly-once。模型输出和工具调用的领域正确性仍由上层服务授权与校验。真实供应商额度、限流行为、质量和服务等级未在本文记录的最终门禁中验证。

## Phase 3：统一文档摄取、课程 RAG 与 Course Assistant

### 阶段目标

从课程资料上传建立到引用回答的完整链路，并确保索引、检索、会话和流式回答始终处于课程与用户授权边界内。

### 主要设计决策

- 维持服务端授权预检索，再将受限证据交给 AI Runtime；不要求模型自行决定权限或检索范围。
- 以课程 ID + Embedding 版本过滤 Milvus 查询。Citation DTO 是独立输出，位置和引文快照以数据库记录为准。
- 原文件放 MinIO；MySQL 保存文档、切片和任务权威状态；Milvus 向量可从文档重新构建。
- 无证据时由服务端拒答分支收敛，不让模型凭空作答。Prompt 将资料、引用、问题历史都视为不可信输入。
- 会话数据库保留完整历史；送入模型的窗口、摘要和 Token 预算受限制。SSE 只有一个终态，未完成答案不落成完整消息。

### 关键实现

摄取支持 PDF、PPT、PPTX、DOCX、Markdown 和 TXT：上传 → MinIO → 解析 → 标准化 → 约 700 token/100 overlap 切片 → Embedding → 按版本写入 Milvus。Metadata 保存课程、文档、章节、页码/section、来源、parser 与 embedding 版本。提供去重、删除、重建索引、重试、向量对账和缺失向量修复。Assistant 支持会话历史、引用卡片、反馈、取消和重试。

### 重要问题与解决方式

- Milvus 对逐批强制 Flush 限流；调整为常规刷盘并显式采用强一致读，避免重试把正确摄取拖成失败。
- Missing vectors 起初只被检测；补上从数据库权威切片重新 Embedding、原 ID 回填并可重试的路径。
- 课程删除和重建可能与对账交错；使用课程索引发布锁，避免尚未提交的向量被判成孤儿。
- Conversation owner、课程成员和流断连都需多处重新校验；服务端在读取/保存处校验，客户端断连时终止回调并且不写入半条回答。
- 历史 Citation 原先通过切片外键级联删除；新增前向 V5 迁移保留引用快照并将切片链接置空。
- Golden Set 与回答使用固定 HTTP Stub；评测证明真实存储、检索过滤、Citation 与拒答工程链路，不作为语义质量成绩。

### 最终验证结果

Phase 3 最终后端 clean verify 183/183 通过，真实 MySQL、MinIO、Milvus 2.5.14 与 Runtime HTTP Embedding 测试通过；六种格式及删除/重建/对账、课程隔离、会话隔离、SSE 中断与唯一终态均在报告中记录。两个受控样例课程共 100 题，各自 Recall@5、Citation hit 和无依据拒答率均为 1.00。Phase 6 最终回归的 PhaseThreeRuntimeIntegrationTest 8 项及仓库 RAG controlled results 继续通过。

### 已知边界

100 题来自受控课程和确定性向量/聊天 Stub，不等于盲测或真实课程语义质量。真实供应商 TTFT 未测。PDF 不做 OCR；部分 Office 来源位置精度限于文件或 section。不存在 API 提交与客户端实际收到 SSE 字节的分布式原子事务承诺。

## Phase 4：Assignment 与 AI Tutor

### 阶段目标

交付教师可发布且可按规则作答的课程作业，以及不越过作业策略的 AI 辅助学习流程。

### 主要设计决策

- 作业采用 `DRAFT / PUBLISHED / CLOSED / ARCHIVED` 生命周期；题型、答案与 Rubric 分开验证。
- 草稿、正式提交 attempt、TutorInteraction、AI 评分建议和最终 Grade 分别表达，正式提交后不可改写。
- 使用幂等键与作业级悲观锁处理重复提交、重交次数、截止点与自动保存竞争。
- Tutor 的六种动作按教师策略开放；提交前拒绝 FullSolution 不依赖单靠 Prompt 指令。四类授权 Tool 的身份/作业上下文由服务端绑定，CourseKnowledgeTool 复用 Phase 3 RAG。
- 自动保存在页面本地暂存待确认变更，路由离开前等待写入；稳定幂等键用于正式提交。

### 关键实现

支持七类选择、判断、简答、分析、软件设计和代码题；发布时验证题干/选项/正分值和 Rubric 细项总分。学生可自动保存、提交、在允许次数内重交，教师可配置截止时间、逾期政策、最大次数和个别延期。Tutor 支持 Hint、Explain、CheckReasoning、AnalyzeError、EvaluateDraft 和 FullSolution。教师能设置可用操作与完整解析条件。Tool 包括 Assignment、Submission、CourseKnowledge、KnowledgePoint。

### 重要问题与解决方式

- debounce 前刷新会丢失最后一次输入；增加按用户/作业隔离的本地草稿缓存、离开守卫与失败时阻止导航，并在返回页面时补存。
- 同一提交请求有重试和 JSON 字段顺序差异；数据库增加 V6 幂等键，按 JSON 语义比对答案，服务端锁定 attempt 并拒绝旧 attempt 写入新草稿。
- 教师修改 Tutor 策略曾可能覆盖个人延期；把延期设置和策略更新分成不同写操作。
- Tutor 工具错误不能降级为空成功；失败 Interaction 明确记录，学生答案与成绩保持独立。

### 最终验证结果

Phase 4 clean verify 187 项通过。真实后端 HTTP/CSRF/MySQL 测试覆盖教师创建并发布、题目与 Rubric、学生作答、Tutor 六类操作、策略拒绝/开放、重交/延期/截止竞争、IDOR 和失败语义；真实 Milvus CourseKnowledgeTool 回归 8 项通过。前端静态检查、32 项单测和 15 条范围浏览器用例通过。Phase 6 冻结回归中的 PhaseFourAssignmentIntegrationTest 继续通过。

### 已知边界

浏览器刷新恢复使用受控 API 场景；后端存储由真实 HTTP 集成测试另外证明，两者未在同一浏览器与生产拓扑做组合验收。Tutor 使用可控 AI 测试，不证明模型对所有自然语言暗示都不会推导答案。现有草稿和正式 attempt 在 Submission 模型中使用不同状态，不是两张物理表。

## Phase 5：Review、AI 辅助批改与 Code Review

### 阶段目标

让 AI 评审输出可验证、可追踪的建议；教师对正式成绩负责；源码评审以隔离 SonarQube 扫描为权威来源。

### 主要设计决策

- Document Review 与 Assignment Review 使用结构化 DTO 和严格领域校验，非法或缺项报告不得标记成功。
- AI 分数是 suggestion。教师逐 RubricItem 确认/覆盖、填写修改理由后才发布 Final Grade；报告、评分者、Prompt/Model/Trace 与审计互相关联。
- Sonar finding 独立保存；AI 只能解释固定 finding 集合，不得创造或删掉权威发现。
- 代码评审只做静态分析，不执行学生代码。ZIP 检查在一次性目录进行，扫描进程仅连 internal 网络中的 Sonar 服务。
- 重试状态以持久 AsyncJob 为权威，Review 不能对失败 Job 显示假成功。

### 关键实现

支持白名单文档类型及完整性、一致性、可验证性、清晰度、问题和建议；作业评审逐 RubricItem 输出建议分数、证据、问题与反馈。教师确认接口需理由、调用者、原建议、最终分值、模型和 Prompt 版本。Code Review 通过 Sonar Scanner 和 SonarQube API 读取 findings，再生成解释报告。压缩包检查包括 MIME、扩展名、体积、条目数、Zip Slip、嵌套归档及压缩比；扫描不构建、不执行学生项目。

### 重要问题与解决方式

- 初版可接受 Rubric 缺项/重复或文档评审少字段；将 validator 收紧为结构、每项恰好一次、分数范围、总分和 evidence 一致校验。
- 同分项覆盖仍应留下理由；评分逐项确认并做并发锁，增加 grade/report 对 AI Trace 外键。
- 模型解释曾容易被误读为 Sonar 结论；前端将原 finding 与 AI 注释分开展示。
- Sonar 测试管理 API 需要额外网络，而 scanner 应无公网访问；测试分离管理端与扫描网络，并在运行态验证连接被拒。
- ZIP 中可伪装嵌套归档或带恶意 scanner 配置；按签名、保留路径和压缩限制拒绝并清理临时目录。

### 最终验证结果

Phase 5 后端 clean verify 197 项通过；真实 MySQL/Redis/MinIO Review worker 测试 6 项通过，涵盖三类 Review、重试和独立 worker 恢复。SonarQube 10.7.0 与 Scanner CLI 实际扫描通过，权威 finding 与解释 key 集一致；扫描网络无法直连公网。ZIP/结构化 validator、教师成绩确认审计及前端 33 项单测、17 条范围浏览器用例通过。Phase 6 冻结回归中的 PhaseFiveReviewIntegrationTest 6 项及真实 Sonar 扫描也通过。

### 已知边界

真实 Sonar 测试在 Phase 5 独立验收栈执行；Phase 6 Stub production stack 的可选 Sonar 服务未启用。AI 解释不是对所有源码的完美判断保证；bytecode 相关静态规则可能需要完整构建上下文，当前扫描不执行学生构建。真实 DeepSeek/DashScope 评分准确性未验证。

## Phase 6：Teacher Dashboard 与生产发布

### 阶段目标

把 Dashboard 统计建立在业务事件的可重放增量投影上，补齐单机发布的监控、安全、资源限制、负载验证和真实备份恢复证据。

### 主要设计决策

- Analytics 触发器记录事务内变更事件。投影保存各业务来源贡献和按课程/班级划分的累计总数；替换来源先撤销旧贡献再写入新贡献。重复事件不重复计数，事件应用 revision 和 Dashboard 快照 cursor 关联。
- 常规 Tutor、反馈、消息及 Review 更新单条贡献；提交、评分更新相关数据族；课程结构变化重查依赖族以应对 MySQL 外键级联不触发子表 trigger 的行为。因此这是差分物化，不声称任意课程结构操作都是 O(1) 单事件。
- 生产日志允许的字段采用白名单，不输出自由文本、参数、异常正文或任意 MDC。Redis 故障时，API 限流降级到有界的单进程预算；Redis Session 仍失败关闭。
- 生产 Compose 通过 TLS 覆盖关闭 DB 宿主端口，并限制每个服务资源。worker 在 internal data network；模型只通过固定 egress proxy 出站。
- 机器负载以 Stub Provider 评估真实 API、SSE、文档摄取/Embedding/Milvus 和 worker 并发；供应商 TTFT 必须另行实测。
- 备份恢复采用停写窗口，导出 schema/data/triggers 分层，避免恢复期间触发器重复生成 Analytics 事件。MinIO 对象逐个算摘要；Milvus 从有效文档重建。

### 关键实现

新增 V8/V9 前向迁移、analytics_change、analytics_contribution、analytics_total 与 revision。Dashboard 增加班级筛选、10 条分页、真实业务指标和状态反馈。新增安全 JSON formatter、traceId/requestId、运维及备份步骤、生产 TLS Compose、隔离的发布验收 Provider/Runner 和机器可读结果。稳定 Job Runtime 只作两个已证明的兼容调整：worker 可配置读取批量，发布 Outbox 使用 READ_COMMITTED 消除混合负载中观测到的课程行锁与 outbox 范围锁死锁。

### 重要问题与解决方式

- 原“检测 cursor 变化后重算整个 Dashboard”不是增量。改用事件与贡献差值，并用 MySQL 集成测试覆盖新增、更新、删除、重放、低 ID 晚提交和并发生成。
- 源事件只按自增 ID 高水位会跳过先分配 ID、后提交的事务；每轮寻找未应用事件而非仅依赖 `MAX(id)`。
- 限流在 Redis 故障时原本每请求重试、内存无界；加入 Redis 失败冷却、本地镜像预算、过期清理和 key 数量上限。
- 生产日志必须防止框架异常或模型响应正文泄密；用 JSON 字段白名单，只留下事件码、安全异常类型及 Trace/Request ID，并实际检查 API 与 worker 日志。
- 首轮真实混合负载发现 Outbox 间隙锁死锁；在隔离发布事务使用 READ_COMMITTED 后重复运行通过，没有降低业务断言。
- 恢复脚本误把正常退出的 minio-init 一次性任务当成常驻健康服务；修正初始化与服务启动顺序，只在确认恢复库为空时重跑，没有清空既有数据。

### 最终验证结果

截至 2026-09-25，Phase 6 阶段总结判定 Engineering Verification PASS，User Acceptance 仍为 PENDING。后端 clean verify 200/200、前端 lint/typecheck/33 单测/build 通过；浏览器 18 项通过、2 项因未配置 Phase 1-E 独立真实环境而跳过。Production profile 隔离栈包含 14 个 Healthy 常驻容器；API 和 5 worker 的 1664 条日志通过 JSON/凭据检查。

真实混合负载运行 121.849 秒：200 个已认证会话、50 个传统 API 用户、20 个 POST-SSE、5 个并发重摄取任务。5811 次传统 API 请求 P95 为 46.56 ms、0 错误；20 个流均有 citation 和唯一 done，5 个文档摄取任务完成。worker 强杀后任务第 2 次尝试完成；Redis 重启及隔离 stream 丢失后从数据库恢复；AI 503 进入 DEAD_LETTER，服务恢复后重索引完成。

备份恢复实演停写 3.598 秒，RPO 为 0（仅对该 quiesced 窗口成立）；RTO 为 135.757 秒。SQL 全表数据与 14 个 MinIO 对象校验一致，恢复 13 条有效文档并重建全新 Milvus 索引，之后真实登录完成带 Citation 的 Course QA。

### 已知边界

真实供应商 TTFT 与真实课程语义质量为 UNVERIFIED；确定性 Stub Golden Set 是工程链路证据，不是语义准确率。生产 TLS 使用本地验收证书，不代表公网域名和 CA 证书已部署。负载结果对应一次约两分钟的单机环境，不构成长期 soak、多副本或更大数据规模容量承诺。RPO=0 仅适用于本次停写恢复演练。测试专用生产和恢复 Compose 均已停止，卷保留；原开发栈未改变。

## 全项目完成与后续边界

Roadmap Phase 1-E～6 的工程验收报告均给出了 `Engineering Verification: PASS`，但各阶段 `User Acceptance` 仍为 `PENDING`。这份记录书汇总工程证据，不替用户验收，也不证明已在正式用户环境部署。Phase 6 机器证据和执行边界可参见[发布结果索引](release/results/README.md)。

学习画像、Teacher Agent、Online Judge、GitHub/Sprint 集成不属于本文记录的交付范围。
