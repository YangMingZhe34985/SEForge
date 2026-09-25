# Roadmap Phase 4：Assignment + AI Tutor 阶段总结

日期：2026-09-25。范围只含 Roadmap Phase 4；Phase 1-E、2、3 公共契约继续作为冻结基线。没有启动 Phase 5 或 Phase 6。

Engineering Verification: PASS  
User Acceptance: PENDING  
Permission to start Phase 5: NOT GRANTED

## 接手审计与范围

接手基线为 `main` / `aae6e27f5fef73945719d4595eb6bbba6a090dba`。工作树在本轮开始前已有未提交的 Phase 2/3 改动，本轮保留原状、不把全部 `git diff HEAD` 归为 Phase 4。本轮阅读了 `docs/roadmap.md`、`docs/phase-2-summary.md`、`docs/phase-3-summary.md`、`docs/ai-agent-design.md`，以及 assignment/tutor、content、conversation、ai、job 和 frontend 的相关实现与测试。根目录 `AGENTS.md`、`codex.md` 为已有空文件；仓库没有 `.codegraph` 索引。

| Gap 分类 | 开工前现状 |
| --- | --- |
| IMPLEMENTED | 作业四态、七种题型、Rubric、提交与延期、六类 Tutor 操作、四个请求级授权 Tool、Phase 3 课程检索适配、教师/学生页面骨架均已存在。 |
| PARTIAL | 发布只查有题与 Rubric 总数；重复 POST 可耗尽次数；迟到 autosave 可误开新 attempt；策略更新可覆盖个别延期；未触发的 debounce 在刷新/离开时丢失；重交/权限/错误状态 UI 不明确；参考答案泄露仅靠 Prompt 约束。 |
| MISSING | Phase 4 真实后端 HTTP + MySQL + CSRF E2E、提交竞争与 IDOR 矩阵、浏览器刷新/离开 pending autosave 测试。 |
| NEEDS_RUNTIME_VERIFICATION | Flyway 前向迁移、并发提交、截止/延期、Tutor 故障审计、真实 Milvus 上的课程隔离、Phase 1-E/2/3 全量回归。 |

## 本轮定点实现

- 发布校验要求每题具有有效类型/题干/正分值，选择题至少两个不重复的非空选项；Rubric 已发布、分项非空、分项总分与题目总分一致。题目和 Rubric 仍只在草稿态编辑。
- 新增 V6 前向迁移 `submission_key` 与课程作业内的学生幂等唯一约束，不修改 V1～V5。重复提交同键、同答案返回同一正式 attempt；同键、不同答案返回 409。服务端按作业行悲观锁串行化草稿/提交，客户端携带 `expectedAttempt`；新草稿必须显式声明 `startNextAttempt`，迟到的 autosave 或代码附件不能在提交后偷偷创建下一次作答。
- 个别延期只由延期接口修改；更新 Tutor 策略保留已有学生延期。有效截止时间用于提交与 Tutor 策略判断，正式 attempt 保持不可修改。
- Tutor 仍经冻结的 `AiGateway.completeWithTools`，四个 Tool 以服务端绑定的 actor/course/assignment/question/submission 执行；模型不能通过 Tool 参数选择别的身份。禁止的 FullSolution 在调用 AI 前拒绝；受限阶段不向模型暴露教师参考答案，对明显回显的参考答案拒绝标记成功。Tool 失败使 TutorInteraction 为 FAILED，不生成假成功，也不写学生答案/成绩。
- 前端以用户+作业键在本地仅暂存尚未确认落库的答案，成功保存/提交后清理；刷新后恢复并补存，SPA 离开前等待保存，保存失败阻止导航。提交使用稳定幂等键；重交需显式点击“开始重交”。教师可配置六类操作并为尚未提交的学生设置延期。补足 citation、permission、error、retry、expired 状态。
- `ApiEnvelope` 的成功 `data` 字段在值为 null 时仍序列化；这是 `/submissions/me` 无草稿时保持既有前端 API 契约所需的最小兼容修正。

## 证据分层

### 代码证据

- `assignment/service/AssignmentService`、`SubmissionService`、`TutorService`、`SubmissionAttachmentService`；`assignment/domain/Submission` 与 V6；`common/api/ApiEnvelope`。
- `frontend/src/views/AssignmentsView.vue`、`assignmentDraftCache.ts`、`frontend/src/api/assignments.ts`。
- 没有重构 Phase 2 AiGateway/Tool Runtime/Job Runtime，也没有改动 Phase 3 Course QA/Milvus/RAG 正式实现；只在 Phase 3 真实集成测试中增加 Phase 4 Tool 复用断言。

### 自动化与真实运行证据

| 层级 | 验证内容 | 结果 |
| --- | --- | --- |
| 真实后端 Phase 4 E2E | `PhaseFourAssignmentIntegrationTest`：独立 MySQL 8.4.4 空库 Flyway V1–V6、真实 Spring HTTP、Session/CSRF、角色和课程授权。AI 与该用例中的检索返回为可控测试替身，但正式 TutorService/Tool 代码照常运行。 | PASS：教师建题/Rubric/策略/发布，学生七题型作答、六 Tutor 操作、草稿/提交/重交、个别延期、截止/关闭、IDOR、显式拒答、Tool 失败审计、重复提交及并发 autosave/submit/次数竞争。 |
| 真实 Phase 3 基础设施回归 | `PhaseThreeRuntimeIntegrationTest`：MySQL + MinIO + Milvus 2.5.14 + HTTP Embedding Stub；新增 CourseKnowledgeTool 经真实 Milvus 检索并对伪造 courseId 查询保持课程过滤。 | PASS：8 tests / 0 failures / 0 errors / 0 skipped。 |
| 后端全量 | Linux Java 21 Maven 容器，`mvn -Dapi.version=1.44 clean verify`。 | PASS：187 tests / 0 failures / 0 errors / 0 skipped；BUILD SUCCESS，耗时 08:46。 |
| 前端静态/单元/构建 | `npm run lint`、`npm run typecheck`、`npm test`、`npm run build`。 | PASS：11 个 Vitest 文件、32 项测试；lint/typecheck/build 通过。 |
| 浏览器 | Playwright 15 个范围内核心用例，其中 3 个 Phase 4：学生提交/Tutor、debounce 前刷新和离开、教师配置/发布。 | PASS；此层 API 受控模拟，不冒充真实后端浏览器联测。 |

真实后端测试特别断言：非成员不能访问作业/Tutor；学生只能读自己的草稿，跨学生 submission ID 被拒绝；前端/输入里的 `userId/courseId/assignmentId/submissionId` 不能覆盖服务端绑定；预提交 FullSolution 不调用模型；提交后按教师策略开放，教师禁用后再次拒绝，截止后按策略开放；重复同键只占一次，竞争重交只允许剩余名额内一次；旧 attempt 原答案不变；迟到 autosave 不能生成新草稿；延期先于策略更新设置后仍生效；注入检索异常返回错误并记录 FAILED。

最终后端门禁日志和 Surefire 明细保存在 `backend/target/phase4-final/`（本地构建产物，不纳入源码提交）。一次中间复跑曾因 MySQL JSON 字段序列化形式与原请求字符串不一致，导致同幂等键、同答案重放误判为冲突；改为按解析后的 JSON 语义比较后，定向测试和上述最终全量 clean verify 均通过。现有 Compose API、worker、MySQL、Redis、MinIO 和 Milvus 容器检查时均为 Healthy；这不替代独立 Testcontainers 验证。

## 未验证边界与跨阶段风险

- 浏览器刷新/离开可靠性由 Playwright 在真实浏览器、受控 API 下验证；MySQL 后端存储由独立真实 HTTP E2E 验证。尚未以“同一浏览器连接同一真实后端”的组合运行该瞬时刷新场景。浏览器本地缓存只用于待确认草稿，按用户+作业键隔离并在确认后删除；多设备同步和恶意脚本读取本机存储不在此轮验收内。
- Tutor 模型在 E2E 中是可控替身，证明的是服务端授权、策略拒绝、Tool 故障与明显参考答案回显拦截；没有声称真实供应商对所有自然语言暗示绝对不会推导出答案。真实 DeepSeek/DashScope 未调用，也未提交密钥。
- Phase 4 HTTP E2E 使用 test profile Servlet Session、真实 MySQL 与 CSRF；生产 Redis Session/独立 worker 与 Phase 3 Milvus 分别在冻结基线回归中验证，没有伪称单个 E2E 覆盖完整生产拓扑。
- 作业草稿与正式作答是同一 `submission` 表内不同状态的 attempt；正式提交后行和答案不可修改，下一次草稿创建新 attempt。TutorInteraction、AI 建议和成绩使用各自领域持久化，不由 Tutor 写入正式答案或成绩。若产品验收要求物理上独立的草稿表，需另行明确，不在本轮扩大迁移。
- 未做 Phase 5 的 Document/Assignment Review、AI 辅助批改、Final Grade、Code Review/SonarQube，也未做 Phase 6 Dashboard/负载验收；已有更高阶段代码仅随全量单测兼容性回归。

## 完成条件对照

| # | 条件 | 结果与证据 |
| --- | --- | --- |
| 1–3 | 生命周期、七题型、Rubric/发布校验 | PASS；真实 HTTP 全流程与无效选择题/缺少 Rubric 的 409。 |
| 4 | pending autosave 刷新/离开不丢最后修改 | PASS（分层）；浏览器恢复并补存、路由守卫等待；真实后端另测保存。组合联测边界见上。 |
| 5–6 | 提交/重交/截止/次数/延期，正式 attempt 不可改 | PASS；真实 MySQL/HTTP 幂等与并发矩阵，原始答案数据库断言。 |
| 7–9 | 六 Tutor 操作、策略、不可伪造 ToolContext | PASS；真实 HTTP + 可控 AI/检索，服务端预拒绝与伪造身份输入测试。 |
| 10 | CourseKnowledgeTool 复用课程 RAG/隔离 | PASS；真实 Milvus 集成回归 8/8，Tool 经 Phase 3 搜索服务，伪造 courseId 仍由服务端过滤。 |
| 11–13 | Tutor 不写答案/成绩、真实后端 E2E、并发/权限矩阵 | PASS；TutorInteraction 单独持久化、正式答案不变、真实 MySQL/HTTP 竞争测试。 |
| 14–16 | 后端 clean verify、前端四门禁、Phase 1-E/2/3 回归 | PASS；后端 187/187，前端 lint/typecheck/test/build，范围内 Playwright 15/15。 |
| 17 | 阶段报告 | PASS；本报告区分代码、自动化、真实运行与未验证边界。 |

没有清空现有 SEForge 数据库/volume，没有关闭 CSRF/RBAC，没有使用 `git reset --hard` 或写入真实 API Key。验证只使用独立 Testcontainers/临时 Maven 容器。Context7 文档用于核对 Spring Data JPA 锁、Spring 6.2 测试 Bean 覆盖与 Vue Router 离开守卫的正确用法。

Engineering Verification: PASS  
User Acceptance: PENDING  
Permission to start Phase 5: NOT GRANTED
