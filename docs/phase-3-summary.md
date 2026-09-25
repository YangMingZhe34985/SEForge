# Roadmap Phase 3：统一文档摄取、课程 RAG 与 AI Assistant

日期：2026-09-25。范围仅为 Roadmap Phase 3，不是产品优先级 P0/P1，不包含 Phase 4～6 验收。

Engineering Verification: PASS  
User Acceptance: PENDING  
Permission to start Phase 4: NOT GRANTED

## 1. 接手基线与范围

- Git HEAD：`aae6e27f5fef73945719d4595eb6bbba6a090dba`，分支 `main`。接手时工作树已有 Phase 2 未提交成果；本轮保留这些成果，不将整个 `git diff HEAD` 都归为 Phase 3 修改，也不自动提交。
- 已读取 `docs/roadmap.md`、`docs/phase-2-summary.md`、`docs/phase-2-plus-development-status.md`、`docs/ai-agent-design.md`，以及 content / conversation / ai / job / worker / frontend 的相关实现与测试。Phase 2 完成状态以最新 `phase-2-summary.md` 为准，较早的 Phase 2+ 现状报告不是本轮缺口判定依据。
- 根目录 `AGENTS.md`、`codex.md` 当前为 0 字节，保持原状；遵循本轮用户提供的 Context7、CodeGraph 与安全边界指令。仓库无 `.codegraph` 索引。
- 冻结 AiGateway、ModelRegistry、Tool Runtime、Job Runtime 公共契约；本轮没有重构或新增它们的业务能力。Course QA 继续采用服务端授权预检索，再调用 Phase 2 AI Runtime。
- 没有改动 Assignment、Tutor、Review、Code Review、SonarQube、Grade、Dashboard、Learning Profile 或 Teacher Agent 实现。

## 2. 开发前短 Gap Audit

| 分类 | 接手时证据与缺口 |
| --- | --- |
| IMPLEMENTED | 六种格式解析器、MinIO 适配器、约 700 token / 100 overlap 切片、版本化向量集合和 course filter、会话 owner/course 绑定、摘要与历史窗口、Citation DTO、文档与助手页面已存在 |
| PARTIAL | missing vector 只有报告没有修复；重索引幂等键可能复用已完成任务；取消与消息持久化存在竞态；历史读取未重新检查课程成员；按 ID 删除未验证 course ownership；对账可能碰到尚未提交的向量；文档错误被 UI 伪装为空列表 |
| MISSING | 独立真实 Milvus/MinIO 的完整摄取验收；每门课至少 50 题的可运行 Golden Set；真实 HTTP 断连与数据库结果的联合证据 |
| NEEDS_RUNTIME_VERIFICATION | Milvus 查询/删除可见性、六种实际文件字节、删除/重建/修复闭环、课程过滤、Session/CSRF 下的会话隔离、唯一终态和中断不保存半条消息 |

## 3. 本轮定点实现

### 文档与向量

- `content/service/CourseIndexWriteService`：课程行锁串行化索引最终发布与对账，避免把未提交向量当成孤儿清理。解析和常规 Embedding 不占用发布锁。
- `KnowledgeDocumentService`：上传在课程锁后再次检查 checksum；上传事务使用 READ_COMMITTED，避免 MySQL 旧快照导致并发重复上传。非终态重建复用当前任务，新的显式重建使用新的幂等键。删除先取消未完成摄取，再清理各版本向量、chunk 与对象。
- `KnowledgeIngestionService` / `IngestionPersistence`：发布前检查文档未删除与 worker 租约；chunk 替换先 flush 删除；任务完成与数据库索引发布保持原子性。晚到的失败/取消不能复活已删除文档。
- Metadata 包含 courseId、documentId、chapterId、page、section、source、parserVersion、embeddingVersion。无章节/页码使用 0 哨兵，不虚构页码。
- `MilvusVectorIndex`：集合按版本隔离；检索仍下推 courseId + embeddingVersion；按 ID 清理只删除所给课程拥有的 ID。真实运行发现逐批 Flush 触发 Milvus 默认 0.1 次/秒限流，改为不强制逐批 Flush，并对搜索和 ID 扫描显式使用 STRONG 一致性。没有内存降级或放宽服务端过滤。
- `VectorIndexReconciliationService`：删除孤儿；使用数据库权威 chunk 内容重新 Embedding，按原 ID 修复 missing vector；返回检测数与修复数；失败抛错，下次对账可重试，不标假成功。worker 定时对账保留，日志区分 detected/repaired。

### Course QA、会话与 SSE

- Controller 在打开流前检查 owner/course/成员授权；Service 在历史读取和最终保存时也重新授权。
- `CourseKnowledgeSearchService` 从向量过滤候选后，再检查课程、版本、document 状态及 chunk/document 关系。Citation 的内容和位置取数据库权威行，不信任向量里的正文/位置。
- 新增 `prompts/course-qa/v2.txt`：文档、文件名、section 与历史均为不可信数据；不得变更课程/权限、执行指令或展示内部推理。无 evidence 分支直接拒答，不调用聊天模型；检索本身仍需 Embedding。
- `CourseQaStreamService` 用同一锁决定终态与完整消息提交，取消/断连先进入终态再取消 Provider；晚到的 delta/complete/error 不产生第二终态或半条 assistant 记录。取消沿用既有 `error` + `CANCELLED`，不另造 Phase 2 事件契约。
- Conversation Controller 对已断开连接的异步异常不再尝试写第二个 JSON 响应。对外错误不回显底层 Provider/内部异常。
- 新增 **V5** `preserve_historical_citation_snapshots`：旧 FK 会在重建 chunk 时级联删除历史 Citation；改为 nullable chunk link + ON DELETE SET NULL，保留文件名、quote、页码、section 快照。V1～V4 不变；V5 不能恢复升级前已经被级联删除的引用。

### 前端

- 修正流式临时消息的 Vue 响应式更新；CRLF 跨网络分片解析；遇到第一个终态立即停止并释放 reader；EOF 无终态显示中断，不当成成功。
- 取消可见、可重试；切换课程/会话清理旧的重试问题；失败的临时回答不开放评分反馈。
- 知识文档查询失败显示错误，其他 Phase 1 课程资源继续可用；原有上传、状态轮询、重建/重试、删除入口保留。
- 更新 RAG HTTP 评测脚本的 portal 登录与登录后 CSRF 刷新，密码推荐从环境变量读取。

## 4. 自动化与真实运行证据

最终门禁于 2026-09-25 11:03（Asia/Shanghai）完成。Maven BUILD SUCCESS，退出码 0；独立最终 XML 汇总为 183 tests / 0 failures / 0 errors / 0 skipped。不把代码存在或测试跳过等同于通过。

| 验证层 | 证据 | 当前结果 |
| --- | --- | --- |
| 后端定点单测 | 文档、对账、会话授权、SSE 唯一终态；新增取消抢先于晚到完成、无 evidence 不调用模型 | 定点通过；CourseQaStreamServiceTest 3 项 |
| 真实基础设施 | `content/infrastructure/PhaseThreeRuntimeIntegrationTest`：独立 MySQL 8.4.4、MinIO RELEASE.2025-04-22T22-12-26Z、Milvus 2.5.14 + etcd 3.5.18 | 最终 7 项通过，0 跳过，177.8 秒；包含 V5 重建/删除后快照保留断言 |
| Provider 协议 | `PhaseThreeProviderStub` 经真实 HTTP、Phase 2 AiGateway/Trace 调用，不注入内存向量库 | 最终六格式与 100 题 Golden Set 通过 |
| 前端单元门禁 | lint / typecheck / Vitest / build | 最终复跑全通过，10 test files / 30 tests |
| 浏览器 | `core-flows.spec.ts`，Phase 1-E + Course QA，新增文档故障/重试/删除、助手中断/重试/反馈 | 最终 12 项通过（14.5 秒），排除 Tutor/Rubric/最终成绩；API 受控，不冒充全栈 E2E |
| 全量后端 | `mvn -Dapi.version=1.44 clean verify` | PASS：183 项全通过，0 跳过，8 分 23 秒；JAR 打包成功 |

真实集成逐项覆盖：

1. 六种文件真实字节上传 Service → MinIO（逐字节回读）→ Parser → Splitter → Runtime HTTP Embedding → Milvus → READY/COMPLETED。
2. checksum 重复及双线程并发重复上传；重建时复用运行任务、完成后可再次重建，旧向量移除且数据库索引不重复。
3. 插入孤儿、删除有效向量，实际对账清理/补回，再次对账无 missing；两个版本集合切换及全版本删除。
4. B 课程放入与 A 相同检索词的私有 chunk；直接 Milvus course filter 和业务检索均隔离，借用 A 的 deleteIds 不会删除 B 向量。
5. 真实 Session/CSRF 登录；同课程跨 owner 为 404，跨课程/已移除成员为 403/404。
6. POST-SSE 取消、客户端断开、Provider 非法流和新 requestId 重试；检验唯一终态及数据库无半条 assistant。
7. 真实 multipart HTTP 上传/下载、答案反馈落库、Prompt v2 Trace、重建/删除后 Citation 快照保留。

范围说明：本集成测试直接 claim 持久任务并调用正式 Ingestion JobHandler，验证整个业务处理链，但不冒充独立 Redis worker 摄取 E2E。HTTP 应用使用 test profile 的 Servlet Session，未关闭 CSRF/RBAC；不冒充生产 Redis Session 验收。Phase 2 的真实 Redis/独立 worker 恢复测试在最终全量 verify 中 4 项通过（142.4 秒），作为冻结基线回归。没有连接现有 SEForge 数据库或清空任何既有 volume。

## 5. Golden Set

输入：`backend/src/test/resources/phase3/software-engineering.json`、`database-foundations.json`。各 10 份课程资料，覆盖六格式；各 50 题，40 有依据 + 10 无依据。

最终输出：`backend/target/phase3-final/phase3-golden-results.json`，保存逐题 recall/citation/refusal 判定；仓库留档副本：[phase3-controlled-results.json](rag-evaluation/phase3-controlled-results.json)。不是手工填写预期值。

| 样例课 | 题量 | Recall@5 ≥ .80 | Citation hit ≥ .90 | Unsupported refusal ≥ .90 |
| --- | --- | --- | --- | --- |
| software-engineering | 50 | 1.00 | 1.00 | 1.00 |
| database-foundations | 50 | 1.00 | 1.00 | 1.00 |

**这些是可控工程指标，不是语义质量评分。** HTTP Stub 按公开课程术语生成固定 32 维向量，聊天输出固定 `[C1]` 答案；检索、过滤、排序、阈值、引用和拒答走真实代码及基础设施。样本不是独立盲测，不能证明复杂中文提问、真实供应商回答正确性、Prompt Injection 的模型层鲁棒性或 SLA。真实课程上线需要教师审核的独立题库与有限供应商 smoke test。本轮未调用真实 DeepSeek/DashScope，未提交密钥。

## 6. 运行边界与交接

- PDF 仅支持可提取文本，不含 OCR；Office 文件当前定位到文件/section，PDF 有页码。无页码不应理解为第 0 页。
- 数据库保存完整消息；既有前端默认加载最新 100 条消息和最多 100 个会话。本轮不宣称超出此窗口的历史浏览 UI 已完整验收。
- 网络已物理断开时无法把终态交付给客户端；保证服务端收敛到一次终态、客户端 EOF 判为中断、取消/断连抢先时不提交部分回答。若完整回答已经先提交、随后 socket 才断开，完整消息可留存；不承诺网络交付与数据库提交是分布式原子事务。
- 索引修复是最终一致闭环；外部系统故障时需要恢复服务再对账。课程级发布/修复锁有吞吐成本，本轮未执行压力容量验收，不因此扩展 Phase 6。
- V5 只新增前向变更；部署需正常 Flyway 执行、备份并验证迁移。禁止通过修改旧 checksum、清库或关闭鉴权来过门禁。
- 后端使用 Linux Java 21 Maven 容器执行，规避本机已知 Windows JDK HTTP Stub 问题；测试容器使用随机映射端口和测试凭据。生产 Compose 未替换部署。
- 本轮使用 Context7 技能核对 LangChain4j 1.12.1 Milvus、Testcontainers 生命周期及 Spring MVC 流式异常处理文档，指导最小接入修正。

验收后停止在 Phase 3。Phase 4 未授权。

## 7. 复现与失败历史

Java 21 + Docker 环境，在 backend 目录：

```sh
mvn -Dapi.version=1.44 -Dtest=PhaseThreeRuntimeIntegrationTest,CourseQaStreamServiceTest test
mvn -Dapi.version=1.44 clean verify
```

本机最终后端采用 `maven:3.9.9-eclipse-temurin-21-alpine`，只挂载 backend、Maven 缓存及 Docker socket；`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`。源码复制到容器临时目录后执行 Maven，避免 Windows bind mount 类加载过慢。最终报告单独回传 `backend/target/phase3-final/`，不与前次定向运行报告混算；保留 Maven 原始退出码。

frontend 目录：

```sh
npm run lint
npm run typecheck
npm test
npm run build
npm run test:e2e -- e2e/core-flows.spec.ts --workers=1 --grep-invert 'Tutor|Rubric|最终成绩'
```

失败记录不抹除：

- 新测试初版 import `Container` 歧义，以及 JUnit PER_CLASS 导致 DynamicPropertySource 早于容器启动；修复测试生命周期，没有改业务配置。
- 首次真实 Milvus 链路触发逐批 Flush 限流，运行很慢；改为 Strong 查询 + 服务端常规刷盘，后续真实链路通过。
- 首次 5 场景集成中 4 项通过，重试场景失败：Stub 对整段历史扫描故障关键字，导致故障在重试中重复触发；改为仅扫描本次用户请求。修复后 7 项通过，不修改 Phase 2 Gateway 规避。
- 新浏览器文档用例先遇到刷新未完成时上传的测试时序问题及默认确认按钮名为 `OK`；等待加载完成并匹配实际按钮后通过，没有跳过断言。
- 真实断连触发框架的 AsyncRequestNotUsableException，旧全局 handler 又尝试输出 JSON。增加仅 Conversation Controller 的无输出处理；最终断连回归通过，不全局改变异常契约。

已有构建警告（Element Plus chunk 大小、Mockito 动态 agent、PDF fallback 字体、Flyway/MySQL 8.4 支持提示）不通过无关依赖重构消除。

## 8. 完成条件逐项对应

| # | 用户定义的工程条件 | 最终结论及依据 |
| --- | --- | --- |
| 1 | 文档摄取全链路 | PASS；真实 MinIO + 正式 JobHandler + Runtime HTTP Embedding + Milvus，READY 与 COMPLETED 断言 |
| 2 | 所有目标格式 | PASS；PDF/PPT/PPTX/DOCX/Markdown/TXT 实际字节，20 份样例课程资料 |
| 3 | 真实 Milvus 集成 | PASS；2.5.14 容器真实插入、查询、过滤、删除、对账，不是内存实现 |
| 4 | courseId 检索隔离 | PASS；跨课程同关键词文档、直接向量检索与业务引用的双重断言 |
| 5 | 删除/重建/对账 | PASS；孤儿清理、missing 重嵌入修复、重复重建、版本切换与删除验证 |
| 6 | Conversation owner/course | PASS；真实 Session/CSRF，跨 owner/课程及成员撤销检查；既有上下文窗口/摘要测试回归 |
| 7 | 可靠 Citation DTO | PASS；引用 chunk/source/quote 与实际检索一致；V5 保留历史快照 |
| 8 | 无 evidence 拒答 | PASS；20 道无依据题不增加 chatCalls，无 Citation；独立单测验证无模型交互 |
| 9 | 唯一终态 | PASS；正常、取消、错误、晚到回调测试，HTTP/单元双层验证 |
| 10 | 中断无半条 assistant | PASS；实际 socket 关闭、显式取消、Provider 非法流后数据库 assistant 行为 0 |
| 11 | Golden Set | PASS（可控工程评测）；2 × 50 题，三项指标均 1.00；语义质量边界见第 5 节 |
| 12 | backend clean verify | PASS；183 / 0 / 0 / 0，BUILD SUCCESS，固定产物 `backend/target/seforge-backend.jar` |
| 13 | frontend 四门禁 | PASS；lint/typecheck/test/build，30 单测；额外 12 浏览器用例 |
| 14 | Phase 1-E / Phase 2 回归 | PASS；全量身份/权限/课程/Runtime/Tool/Job 测试，含真实 Redis 与独立 worker 故障恢复；既有高阶段单测仅兼容性回归 |
| 15 | 阶段交接报告 | PASS；本报告区分代码、自动化、真实运行、受控 Provider 与未验证边界 |

最终证据目录：`backend/target/phase3-final/surefire-reports/`、`backend/target/phase3-final/phase3-final-verify.log`、同目录 Golden Set 与 Phase 2 worker 日志。`git diff --check` 通过；历史 Flyway 无改动。

本轮临时测试环境在完成后清理；现有 Compose 服务及业务数据卷保留。未部署替换生产 Compose，未自动提交 Git。接下来只等待用户验收，不自行进入 Phase 4。

Engineering Verification: PASS  
User Acceptance: PENDING  
Permission to start Phase 4: NOT GRANTED
