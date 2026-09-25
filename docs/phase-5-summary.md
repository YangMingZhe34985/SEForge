# Roadmap Phase 5：Review Agent、AI 辅助批改与 Code Review

日期：2026-09-25。接手 HEAD：`aae6e27f5fef73945719d4595eb6bbba6a090dba`（main）。Phase 2～4 已有未提交成果保留；本报告只将本轮 Review、Grade 及必要的提交锁接口修改归入 Phase 5。

Engineering Verification: PASS  
User Acceptance: PENDING  
Permission to start Phase 6: NOT GRANTED

## 开工读取与 Gap Audit

读取根目录 `AGENTS.md`、`codex.md`（当前均为空文件）、`docs/roadmap.md`、Phase 2/3/4 总结、`docs/ai-agent-design.md`，以及 Review、Grade、Assignment、AI、Job、Sonar 和前端相关实现与测试。仓库无 `.codegraph` 索引。旧报告中下一阶段未授权是历史交接状态，本轮以用户明确的 Phase 5 授权为准。

| 分类 | 接手发现 |
| --- | --- |
| IMPLEMENTED | 三类正式 JobHandler、documentKind 白名单、基本结构校验、逐项教师确认、课程权限、ZIP 校验、SonarScanner 网关、内部 worker 网络、Review/Grades 页面。 |
| PARTIAL | 文档缺少 issues/recommendations 或分项 evidence 仍可能通过；GradeSuggestion 入口未独立检查完整 Rubric；缺少持久 AI Trace 关联；幂等键未绑定课程；前端仅展示 AI 归纳，未完整展示权威 Sonar finding。 |
| MISSING | 三类真实 Redis worker 业务链路、Review 进程强杀恢复、真实 Sonar 容器扫描和运行时网络隔离证据。 |
| NEEDS_RUNTIME_VERIFICATION | 重试/死信/唯一报告、教师覆盖理由与并发确认、跨课程 IDOR、ZIP 攻击和清理、Phase 1-E～4 回归。 |

## 定点修改与代码证据

- `review/service/ReviewResultValidator`、`ReviewExecutionService`：严格拒绝未知/缺失/null/重复 JSON 字段及尾随内容；文档四维恰好一次、issue 完整性与 severity 白名单；作业 Rubric 恰好一次、分数范围、evidence、feedback 和总分验证。仅通过校验后原子提交报告。
- `GradeSuggestionService` 独立检查完整 Rubric；与 `GradeService.confirm` 共用 submission/grade 悲观锁，已确认成绩不能被 AI 覆盖。分数最多两位小数，避免数据库 DECIMAL 舍入后分项之和与总分不一致。教师逐项确认，修改总分或任何分项均需理由；重复确认返回冲突。确认记录 grader、原建议、最终分项、理由、model、promptVersion、AI Trace，并写 `GRADE_CONFIRMED` 审计。前端附带预期 AI Trace，阻止旧报告确认新版建议。
- **新增 V7 前向迁移**：`review_report.ai_trace_id`、`grade.ai_trace_id` 外键。没有修改 V1～V6。报告、导出与成绩 DTO 返回审计关联；AI 建议与教师分项反馈分别存储。
- `ReviewSubmissionService`：幂等键绑定课程，重复请求核对目标与配置；手工重试锁定 Review 行，记录当前重试操作者。Review handler 的失败写入受现有 Job 租约保护，迟到失败不能覆盖已完成/取消状态。对外错误使用安全摘要。查询状态与重试条件以持久 AsyncJob 为权威，包含 RETRY_WAIT；即使权限拒绝发生在 handler 之前，也不会向用户持续展示 QUEUED/PROCESSING。`review_job.status` 保留 handler 的状态快照，不宣称其单独覆盖所有 Runtime 失败。
- `SonarScannerGateway`：保持官方 Scanner CLI 及权威 API；同时读取 issues 和 Security Hotspots，分页/总数/重复键异常均失败，超过安全上限不静默截断。模型只能为固定 findingKey 集合逐条解释，权威 finding 原样单独保存；质量门禁 ERROR 是扫描结论，不代表扫描执行失败。
- `SecureArchiveValidator`：保留 50 MB ZIP、200 MB 展开、25 MB 单项、2000 entries、100 倍压缩比及路径/重复/保留 scanner 配置检查；补充伪装扩展名的嵌套归档 magic 检测与目录条目非法数据拒绝。临时目录在成功和失败后清理；不执行学生构建/源码。
- 前端 `ReviewsView`、`reviews.ts`、`GradesView`：权威 Sonar finding 与 AI 解释分开展示，显示模型/Prompt/Trace、文档 issue、任务失败/重试/加载错误；教师确认提交防重复；成绩查询错误可见并阻止跨课程旧响应覆盖。

冻结边界：没有重构 AiGateway、ModelRegistry、Tool Runtime、Job Runtime、RAG、Tutor 或 Assignment 生命周期。新增 submission 的锁定查询仅供批改互斥；未开展 Dashboard、Learning Profile、Teacher Agent、Phase 6 或负载测试。

## 自动化与真实运行证据

最终门禁于 2026-09-25 15:08:13（Asia/Shanghai）完成。后端独占构建目录运行，197 项测试全部通过，未跳过；完整耗时 11 分 53 秒。工程验证 PASS 仅覆盖下面记录的验收环境，不代替用户验收或生产部署验收。

| 层级 | 验证方法 | 当前结果 |
| --- | --- | --- |
| 真实 Review HTTP/worker | `PhaseFiveReviewIntegrationTest`：真实 Spring HTTP、Session/CSRF、MySQL 8.4.4、Redis 7.4.2、MinIO；生产 Outbox/RedisJobWorker/三类 handler；AI 经真实 Gateway/Trace 调用本地 HTTP Stub | 最终 PASS：6 tests / 0 failures / 0 errors / 0 skipped，238.2 秒；包含独立 worker JVM 强杀恢复。 |
| Sonar | 独立 SonarQube 10.7.0 与 Scanner CLI 镜像；生产 ProcessScannerRunner 生成命令和解析 metadata，测试 adapter 将进程调用送入 internal 网络扫描容器；正式 HttpSonarClient 读取结果 | 最终 PASS：EXECUTION SUCCESS（8.980 秒），权威 finding/解释集合一致、临时目录清理、公网连接拒绝。 |
| ZIP/结构化单测 | 路径穿越、嵌套归档/伪装、MIME/扩展名/大小/数量/压缩比、清理；文档、Rubric、伪造/重复/遗漏 finding | 最终 PASS：SecureArchiveValidatorTest 11 项、ReviewResultValidatorTest 5 项。 |
| 后端 | Java 21 Linux Maven 环境 `mvn -Dapi.version=1.44 clean verify` | 最终 PASS：BUILD SUCCESS；197 tests / 0 failures / 0 errors / 0 skipped；打包成功。 |
| 前端 | lint、typecheck、Vitest、build | 最终 PASS：11 files / 33 tests，lint/typecheck/build 通过。既有 Element Plus 大分包警告保留。 |
| 浏览器 | `core-flows.spec.ts --workers=1 --grep-invert Dashboard` | 最终 PASS：17/17，23.6 秒；API 受控，真实后端由独立集成测试覆盖。 |

核心回归：PhaseOneAuthorizationIntegrationTest 2 项、JobRuntimeIntegrationTest 4 项、PhaseThreeRuntimeIntegrationTest 8 项、PhaseFourAssignmentIntegrationTest 1 项均通过；其余身份、AI/Tool、SSE、Assignment/Tutor 单元与契约测试包含在 197 项中。Phase 3 真实 Milvus/MinIO 回归与 Golden Set 结果也保留在运行证据中。既有高阶段测试随全量执行不等于开展 Phase 6 验收。

本地原始证据位于 `backend/target/phase5-final/`（Git 忽略目录，不随源码提交）：

- `backend-clean-verify.log`、`surefire-reports/`：完整日志、逐测试结果。
- `phase5-sonar-scanner.log`、`phase5-sonar-result.json`：真实 Scanner 执行、Sonar 原始 findings 与独立 AI 解释。
- `phase5-scanner-network.txt`：运行态内部网络与公网连接拒绝证据。
- `phase5-worker-*.log`：独立 worker JVM 启动；强杀/租约恢复/单份报告断言见集成测试结果。
- `phase3-golden-results.json`：冻结阶段真实 RAG 回归评测。

前端复现：在 frontend 中依次执行 `npm run lint`、`npm run typecheck`、`npm run test`、`npm run build`，浏览器执行 `npm run test:e2e -- e2e/core-flows.spec.ts --workers=1 --grep-invert Dashboard`。

收尾：原始证据归档后仅移除本轮临时 Maven 验收容器 `seforge-phase5-verification`；未删除现有数据库或 volume。原 Compose 服务只读检查均 healthy，未重新部署，因此不把旧容器健康状态当成本轮新代码的部署证据。工作树保留所有既有及本轮改动，未擅自提交。

## 已知边界与复现

- AI 使用可控 HTTP Provider，不证明真实供应商的评审语义质量；没有提交密钥或调用真实 DeepSeek/DashScope。模型输出不被当作权威扫描结论或最终成绩。
- HTTP 测试保留 CSRF/RBAC，使用 Servlet Session；Redis 用于真实任务队列。三类 worker 业务测试通过真实 Redis 消费，在测试 JVM 内调用生产 worker；额外强杀独立 worker JVM 验证 Review 恢复。二者分别记录，不能将内嵌调用称为独立部署。
- Sonar 测试管理连接与扫描网络分开。扫描器仅一个 internal 网络；Sonar 服务额外管理网络用于测试 API 读取。不会将测试适配器称为生产容器部署验收。
- 本轮真实样例返回 `python:S1764`（BUG）和 `python:S4790`（SECURITY_HOTSPOT）各一项，解释 key 集合与权威结果一致。网络实测 `internal=true; networks=1`，Sonar 可达，公网 `1.1.1.1` 连接失败（curl exit 7）。
- Source-only 扫描不编译学生 Java，因此依赖字节码的规则可能无法给出完整结论；超限 finding 会明确失败，需要缩小提交范围后再评审，不会生成截断的成功报告。
- 文档/作业上下文继续使用既有 60,000/80,000 字符上限，超出部分带截断标记；本轮不将大文档全量语义审查或模型评分准确率列为已验证结果。
- 不宣称自然语言模型永不出现错误解释；保证权威 finding 原文独立、key 集合一致、AI 解释标识清楚、最终成绩受教师确认约束。
- 在 Java 21 + Docker 的 backend 中执行 `mvn -Dapi.version=1.44 -Dtest=PhaseFiveReviewIntegrationTest test` 与 `mvn -Dapi.version=1.44 clean verify`。Windows 本机沿用前阶段 Linux Maven 容器以避开 HTTP Stub 的已知 Windows JDK 问题。
- Context7 用于核对 SonarScanner 参数/认证、Security Hotspots 与 Testcontainers 容器网络/文件传输。测试数据在独立容器，不清空现有数据库/volume，不关闭鉴权。
- 保留既有 Flyway 对 MySQL 8.4 的支持版本提示和 Element Plus bundle 大小警告；它们不是本轮新增故障，不扩展到基础设施版本升级或 UI 分包重构。

## 失败历史

1. 新测试初版出现 Container import 歧义与不存在的 GenericContainer.withUser；仅修正测试 API 用法。
2. 初次故障测试人为提前发布延迟 Outbox，但 job 尚未到重试时间，导致测试消息被消费后未运行。去掉测试时钟干预，按正式退避时间重跑；没有修改 Job Runtime。
3. Sonar 仅连接 internal 网络时 Docker 未提供宿主端口映射，Testcontainers 在端口检查失败。测试 Sonar 增加管理网络，扫描容器保持 internal-only。
4. 原错误消息测试期望内部异常全文；生产改为安全失败摘要后同步调整断言，仍检查 SONAR_FAILED 与无成功报告。
5. 前端初轮 Vitest 因 Windows sandbox 拒绝 esbuild 读取路径而未启动；使用正常权限重跑，33 项通过。
6. 最终全量首次编译发现测试 DirtiesContext 注解缺少 import，补充后重跑。另一次中间全量为纳入最终 Runtime 状态投影修正主动停止，不计作完成证据。
7. 集成测试手工关闭 Spring Context，导致 afterTestClass 回调报错；删除手工关闭，交由 `@DirtiesContext(AFTER_CLASS)` 管理。一次重跑过早启动 clean，造成上一轮后续测试 classpath 文件被移除；该轮作废，不据此判断冻结阶段存在业务回归。最终证据只采用后续独占构建目录的完整运行。

## 完成条件映射

| # | 条件 | 证据 |
| --- | --- | --- |
| 1 | Document Review 可靠结构 | 五类 documentKind 真实 HTTP/worker 成功；非法 kind 和缺字段结果拒绝；四维/issue 校验单测。 |
| 2 | Rubric 严格一致 | 真实 worker 的缺失、重复、越界、总分错误、非法结构死信且无报告/成绩；分数精度单测。 |
| 3–4 | 建议与最终成绩隔离 | AI_REVIEWED 对学生不可见；教师确认后 FINAL；AI 重评无法替换已确认成绩。 |
| 5 | 覆盖与审计 | 分项确认、相同总分下的分项覆盖也需理由、grader/原分/最终分/Prompt/Trace、GRADE_CONFIRMED 审计；并发确认一次成功一次冲突。 |
| 6–7 | 真实 Sonar 与模型职责 | 真实 scanner/server、权威 findings 独立保存、逐 key 解释、拒绝伪造/遗漏/重复；源码不执行。 |
| 8 | ZIP 安全 | 攻击样例、大小/条数/压缩比、扩展名/MIME、伪装嵌套归档、路径与清理。 |
| 9 | worker/retry/失败 | 真实 Redis 消费、三次退避死信、手工重试、重复投递一份报告、独立 JVM 强杀恢复、权限失效在 handler 前被拒绝。 |
| 10 | 权限与 IDOR | 跨课程 Submission/Review/报告导出；学生/同学/其他教师/TA 的确认权限；学生只看本人已确认成绩。 |
| 11 | 后端 clean verify | PASS：197/197，0 failures/errors/skipped，BUILD SUCCESS。 |
| 12 | 前端四门禁 | PASS：lint/typecheck/33 单测/build；额外 17 浏览器回归。 |
| 13 | Phase 1-E～4 核心回归 | PASS：上述真实基础设施套件与完整后端回归、前端核心回归全部通过。 |
| 14 | 阶段总结 | 本报告，区分代码、自动化、真实运行及未验证边界。 |

Engineering Verification: PASS  
User Acceptance: PENDING  
Permission to start Phase 6: NOT GRANTED
