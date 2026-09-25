# Roadmap Phase 6：Teacher Dashboard 与生产发布

日期：2026-09-25。接手 HEAD `aae6e27f5fef73945719d4595eb6bbba6a090dba`（main）。保留 Phase 1-E～5 工作树成果，未擅自提交。最终 clean verify 于 16:35:35（Asia/Shanghai）通过，耗时 14 分 27 秒。工程 PASS 仅覆盖本报告记录的隔离验收环境，不替代用户验收。

Engineering Verification: PASS  
User Acceptance: PENDING  
Roadmap Phase 1-E～6 Completion: NOT USER-ACCEPTED

## Gap Audit

已完整读取 roadmap、Phase 2/3/4/5 总结、Analytics/Job/AI/Security/Compose/前端相关实现及测试。根目录 AGENTS.md、codex.md 仍为空文件；无 CodeGraph 索引。

| 分类 | 接手情况 |
| --- | --- |
| IMPLEMENTED | 业务数据指标 SQL、快照 JobHandler、课程教学权限、Redis Lua API 限流、上传/附件配额、Actuator/Prometheus/OpenAPI、固定版本 Compose、运维和 k6 脚本。 |
| PARTIAL | 原快照是 hash 检测变化后全量重算，不是增量；历史 API 有分页但 UI 无入口；日志为文本；生产 TLS 依赖外部配置；限流 fallback 内存无界且每请求重试 Redis。 |
| MISSING | 本轮真实生产整栈、混合负载、备份恢复 RPO/RTO 和日志脱敏证据；增量删除/重放/晚提交验证。 |
| NEEDS_RUNTIME_VERIFICATION | 班级与课程隔离、快照并发/分页、真实限流降级、网络/资源限制、故障恢复、Phase 1-E～5 全量回归。 |

## 实施与边界

### Analytics

- V8 新增事务内触发器变更日志、逐来源记录贡献、分 scope 累计值和课程投影版本；V9 为事件记录 applied_revision，关联快照 sourceCursor。V1～V7 不修改。
- AnalyticsProjectionService 撤销旧贡献，再加入新贡献，累计值/贡献/事件处理标记与快照在任务完成事务中提交。重复事件不重复计数；不使用 MAX(eventId) 排除低 ID 晚提交事务。
- 普通 Tutor/反馈/消息/Review 活动定点重查记录；提交、成绩变更重查相关数据族；课程成员、班级、作业、Rubric 等结构变更需重查依赖数据族，处理 MySQL 不触发子级 trigger 的 FK 级联。这是**带结构变更依赖重查的差分物化**，不宣称所有操作均为 O(单事件)，也不把原先全量 SQL aggregate 改名冒充增量。
- API 读取不可变快照；worker 更新投影；课程投影锁防止并发重复应用。完成率按当前有效学生/作业资格去重，成绩按已确认 attempt 归一化；知识点来自最终分项分数；错误状态读取 AsyncJob 权威终态。
- Dashboard 提供班级筛选、10 项历史快照分页、可见错误/刷新重试，防止旧请求覆盖新课程。错误趋势明确表示“历史快照累计失败数”，不是区间新增事件或学生答题错误率。平均分包括每个已确认 attempt，不擅自改成“每学生只取最高分”。
- 高频问题是前 160 字相同问题的频次，不是额外 LLM 聚类。知识点正确率实际为 Rubric 分项得分率，保留既有业务口径。

### 生产工程

- SafeJsonLogFormatter 输出 JSON 字段白名单，省略自由文本、参数、异常消息和任意 MDC；保留时间、level、logger、事件码、安全异常类型、traceId/requestId。降低敏感内容泄露风险的代价是不能从生产日志直接读取完整异常正文；应使用授权审计与安全元数据定位。
- 每个 HTTP 请求生成 requestId；Prometheus 仅管理员可读；增加 Job pending/dead-letter 与 Analytics pending-change 低基数指标。不可用返回 -1，不伪装零积压。
- Redis API 限流保持原子 Lua；本地同步预算保证降级不会重新获得完整额度，5 秒冷却避免每次 Redis 故障都阻塞，10000 key 上限及过期回收。降级是进程级保护，不宣称多副本全局严格限额；Redis Session 故障不会绕过认证。
- compose.production.yml 提供 TLS 入口、关闭 DB 宿主端口；沿用固定版本、资源限额、healthcheck、worker internal-only 网络。证书由运维提供；演练使用仅本地信任的短期证书，HTTP 客户端验证该证书，Secure Cookie/CSRF/RBAC 不关闭。
- V8 触发器要求 MySQL `log_bin_trust_function_creators=1`，应用账号不授予 SUPER；这允许 schema DDL 账号创建 trigger，须限制迁移账号权限，不适用于不受信任多租户共享数据库。

### 冻结基线最小兼容修正

1. RedisJobWorker 增加可配置读取批量，默认仍为 5。5 个重任务并发验收设为 1、部署 5 个 worker，避免单实例先领取全部批次后串行处理。
2. 混合负载真实发现 Outbox 范围锁与上传课程锁死锁；只将 RedisOutboxPublisher 发布事务改为 READ_COMMITTED，保留悲观行锁、持久 Outbox、租约与状态机。未重写 Job Runtime。
3. 全量 MySQL 测试容器仅同步上述 trigger 创建参数；冻结阶段的业务断言不降低。

没有开发 Learning Profile、Teacher Agent、Online Judge、GitHub/Sprint、新业务 Agent 或无关 UI 美化。

## 验证记录（持续更新）

| 层级 | 结果 |
| --- | --- |
| Analytics 真实 MySQL | 定向 PASS：新增/修改/删除、重复事件、低 ID 晚提交、班级隔离、权限、分页和并发调用；不是 mock 数据库。 |
| Redis / JSON | 定向 PASS：真实 Redis 跨实例限额及故障本地预算；敏感 message/argument/exception/MDC 不进入 JSON。 |
| Frontend | 最终 lint/typecheck/33 单测/build PASS；Playwright 18 PASS、2 SKIPPED（需独立 Phase 1-E 环境的真实浏览器用例），不把 mock 浏览器测试冒充真实后端 E2E。 |
| Production Compose | 最终停启复验 PASS：14 个常驻容器 Healthy，5 worker internal-only、全部具资源限额；真实 HTTPS/Redis Session、CSRF、metrics/OpenAPI、学生 403 检查通过；API + 5 worker 共 1664 条日志全部为 JSON 且不含生成凭据。 |
| 混合负载 | PASS：200 个已认证会话、50 API 用户，121.849 秒，5811 次传统 API 请求，P95=46.56ms，0 错误；实测 20 AI streams / 5 heavy embedding tasks 同时在途。20 流各一个 done 且带 citation，5 摄取任务 COMPLETED。 |
| 故障恢复 | PASS：worker SIGKILL 后第 2 次尝试完成；Redis stop/start + 隔离 transport stream 丢失后持久任务完成；AI 503 进入 DEAD_LETTER，恢复后新重索引任务完成。无永久未终止任务，终态复查稳定。 |
| 备份恢复 | PASS：2026-09-25T08:25:47Z 停写备份，3.598 秒；恢复实测 RTO=135.757 秒，RPO=0（仅停写窗口）。完整 SQL 数据摘要一致，14 对象 SHA-256 一致，配置解密一致，13 文档在新 Milvus 中重建并以真实授权 Course QA 验证 Citation/唯一 done。 |
| backend clean verify | PASS：Java 21 / Maven 3.9.9，`mvn -Dapi.version=1.44 clean verify`，200 tests / 0 failures / 0 errors / 0 skipped，BUILD SUCCESS；另于独立目录配额补验 6/6 PASS（含新增 1 项超限测试及增强的恰好上限断言），未改业务代码。 |
| 真实供应商 TTFT | UNVERIFIED：本轮无真实供应商调用；Stub 指标不能证明真实 TTFT SLA。 |

## 复现与证据

隔离验收脚本 `docs/release/verify.mjs`，固定项目 `seforge-p6-verify`，恢复项目 `seforge-p6-restore`，不会操作原 `seforge` 栈。依次执行 configure、build、up、seed、inspect、load、recovery、backup。seed 仅用于新环境；backup 要求新的恢复项目（允许初始化失败但数据库仍空、未创建 API 的基础设施重试），拒绝覆盖非空恢复数据库。所有凭据与产物存放在 Git 忽略的 `backend/target/phase6-release/`，不可将该目录提交或分享；备份含个人/业务数据及密码哈希，应受限保存。

负载脚本在 200 个真实 Redis Session 登录完成后，运行 50 个传统 API 用户、20 个真实 POST-SSE 和 5 个真实解析/Embedding/Milvus 后台摄取任务。机器可读结果保留延迟、错误、逐流终态、逐任务结果和 Provider 实测并发峰值。不以 5 个空统计任务代替重任务，不使用旧 k6 脚本关闭 Secure Cookie 的建议。

已检查的非敏感机器输出副本位于 `docs/release/results/`。原始 MinIO 中有 14 个对象、13 个文档记录：备份完整保留对象集，包括首轮失败上传产生的未引用对象；没有为获得一致性结论删除它。向量恢复以有效文档为准，原始对象垃圾回收不在本轮扩大实现。

后端完整日志和 JUnit XML 归档在 `backend/target/phase6-final/`；全量构建在独占 `/tmp/phase6` 中执行，补充配额测试用独立 `/tmp/phase6-quota`，没有并发清理同一构建目录。200 项全量与 6 项补验有 5 项重叠，不合并宣称 206 项独立测试。后端回归包括 PhaseOneAuthorization 2、JobRuntime 4、PhaseThreeRuntime 8、PhaseFourAssignment 1、PhaseFiveReview 6；其余身份、Tool、AI、SSE、权限、安全与契约测试包含在全量中。

验收环境：Docker Desktop Linux，Docker 29.8.0，24 vCPU / 16619589632 bytes 可用 Docker 内存；每个 Compose 服务另外受各自资源上限约束。测试镜像 `seforge/*:phase6-verify` 来自本次工作树，不冒充已提交 release tag。两个专用测试项目验收后均已停止，全部卷保留；原 `seforge-*` / `devflow-*` 栈未被修改，仍 Healthy。

## 完成条件对照

| 条件 | 结果与证据 |
| --- | --- |
| 1. 真实 Dashboard 指标 | PASS：真实业务表贡献，完成率/平均分/分布/知识点/高频问题/弱项/Tutor/错误趋势，口径见上文。 |
| 2. 班级隔离、筛选、分页 | PASS：真实 MySQL scope/权限/分页断言 + Dashboard Playwright。 |
| 3. 增量语义 | PASS：事务事件、旧贡献撤销/新贡献累加、applied_revision、删除/重放/晚提交/并发测试。 |
| 4. Redis 限流/配额 | PASS：真实 Redis 跨实例与故障预算；课程存储恰好上限/超限测试，既有附件用户/课程配额回归。 |
| 5. health/metrics/OpenAPI | PASS：真实生产 HTTPS HTTP 200；未授权 metrics/教学统计 403。 |
| 6. JSON 与脱敏 | PASS：敏感输入单测及 API/worker 1664 条运行日志核验。 |
| 7. Production Compose | PASS：真实整栈 14 常驻容器、TLS、安全 Cookie、internal network、资源限制、停启健康。 |
| 8. 备份恢复 | PASS：全表/对象摘要一致、配置解密、全有效文档重索引、恢复后 QA；RPO/RTO 已记录。 |
| 9. 混合负载 | PASS：200/50/20/5，P95 46.56ms、0% 错误，机器结果已保存。 |
| 10. 任务恢复与唯一终态 | PASS：真实 worker/Redis/AI 故障演练，20 流唯一终态；Job Runtime 集成重复投递仅一个结果、租约与取消断言通过。 |
| 11. 后端门禁 | PASS：clean verify 200/200，打包成功；配额补验 6/6。 |
| 12. 前端门禁 | PASS：lint/typecheck/test(33)/build；额外 18 Playwright PASS，2 环境专用用例 SKIPPED。 |
| 13. 冻结基线回归 | PASS：Phase 1-E～5 核心后端回归全绿，包含真实 Milvus、MinIO、Sonar 扫描；两项 Job 修正保持契约。 |
| 14. 阶段报告 | PASS：本报告区分代码、自动化、真实运行与未验证边界。 |

## 未验证边界与发布注意

- 真实 DeepSeek/DashScope TTFT、真实课程语义质量及外部供应商故障 SLA：UNVERIFIED。本轮固定 Embedding / Stub 验证工程正确性，不证明语义检索质量；既有 Phase 3 Golden Set 在回归中单独验证。
- 使用生产 profile/Compose/TLS 的本机隔离演练不是实际公网生产部署。正式发布还需受信证书、域名、秘密管理与用户验收。
- 负载为约 2 分钟既定混合门禁，不是长时间 soak、无限历史规模或多节点容量承诺；5 重任务并发使用 5 个 worker、批量读取设为 1。
- SonarQube 是可选 production profile；本轮完整 Stub 发布栈未启用，真实 Sonar 容器扫描由冻结 Phase 5 集成回归覆盖。未声称演练了可选 Sonar 数据备份恢复。
- Redis 故障下本地限流是单实例预算保护；会话不可用时认证仍失败关闭，不绕过 RBAC。已应用 Analytics 事件保留，需按运维手册做归档与容量管理。
- 恢复验证通过的是停写 SQL/对象备份与索引重建。未测在线持续备份、跨机房恢复或公网大文件量 RTO；恢复环境数据库、对象与索引卷保留，不自动清空。
- 前端既有 Element Plus 大分包警告保留；2 项另需 Phase 1-E 环境的真实浏览器用例未重跑，真实业务集成与权限由后端全量回归覆盖。

## 失败历史

- V8 初轮 MySQL 无创建触发器权限，按显式 server 配置修正；没有给应用 SUPER。
- 新集成 fixture 初版使用错误的 attempt/type/points 字段，修正为真实 schema 字段后通过。
- 前端捕获 nullable courseId 时缺少局部变量 narrowing，修正后 typecheck/build 通过。
- Dashboard 浏览器 locator 将 Element Plus 的 placeholder 文本当成 input 属性，改为实际可见文本，定向通过。
- 首轮负载中真实 Outbox gap-lock 死锁，定位后仅调整发布事务隔离级别；首次没有完整性能汇总，不报告性能通过。
- 恢复脚本初轮将成功退出的 minio-init 一次性任务纳入常驻 healthcheck，Compose 提前退出；改为独立执行初始化，确认目标数据库仍为空后重试，未清空任何数据库或卷。

Context7 用于核对 MySQL FK cascade/trigger 语义与 Spring Boot 3.4 StructuredLogFormatter；据此明确处理级联删除并使用官方日志扩展接口。

Engineering Verification: PASS  
User Acceptance: PENDING  
Roadmap Phase 1-E～6 Completion: NOT USER-ACCEPTED
