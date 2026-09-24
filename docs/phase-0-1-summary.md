# SEForge Phase 0 / Phase 1 阶段总结报告

> 审计日期：2026-09-22  
> 修订日期：2026-09-24（报告定稿后的联调修复、角色入口审计与前端信息架构重构见第 9 节；第 1～8 节保留原审计快照）  
> 本轮范围：仅 `docs/roadmap.md` 中的 Phase 0 与 Phase 1  
> 结论：**Phase 0 + Phase 1 验收通过**。Phase 2～Phase 6 的超前实现全部保留，本轮未继续开发、补全、重构或验收。

## 1. 范围与判定口径

- `PASS`：代码、配置及与该阶段直接相关的自动化门禁足以支持验收结论。
- `PARTIAL`：已有实现，但仍缺少阶段验收所必需的能力或证据。
- `FAIL`：缺失或存在阻断阶段验收的问题。
- 用户已明确允许 Docker 镜像或本机 Docker 环境导致的验证无法执行时跳过，因此这类项目单独记录为环境备注，不扩大为 Phase 2+ 修复任务。

## 2. Phase 0：工程基线与 SEForge 骨架

### 2.1 任务审计

| `roadmap.md` 任务 | 状态 | 依据 |
| --- | --- | --- |
| 全量命名迁移、POM 清理、依赖 BOM、`dev/test/prod` 配置、统一异常与日志 | PASS | Java 包、Maven artifact、前端包名、数据库及配置前缀已迁移至 SEForge；`backend/pom.xml` 使用 Java 21、Spring Boot 3.4.5 和 LangChain4j BOM；环境配置及统一 API 异常/traceId 日志已建立。 |
| 建立全新 Flyway V1 Schema，删除相互竞争的旧 SQL 初始化方式 | PASS | `backend/src/main/resources/db/migration/` 为唯一 Schema 来源，V1/V2/V3 可按顺序迁移；旧 `sql/init.sql`、`sql/smartse.sql` 等初始化入口已移除。一次有效 MySQL 8.4.4 容器运行已完成空库 V1→V3 迁移。 |
| backend、worker、frontend 容器化，Compose 固定镜像、健康检查、资源限制和非默认凭据 | PASS | 后端/前端 Dockerfile、worker profile 及生产 Compose 已存在；Compose 配置展开通过；MySQL、Redis、MinIO、Milvus、SonarQube 等均固定版本并配置健康检查和资源限制；凭据要求从环境变量提供。 |
| 启动不访问云模型或探测 Milvus；AI 不可用时 degraded 启动 | PASS | AI/向量能力由配置开关和健康状态隔离；无 API Key、无 Milvus 的测试配置可加载核心上下文，传统业务不依赖供应商可用性。 |
| 移除公开测试端点、天气 Tool、假用户数据、硬编码统计、失效入口和生产包 main 测试类 | PASS | 旧 SmartSE 测试 Controller、天气/演示入口、静态演示页及生产源码中的测试类均已移除；未发现仍暴露的旧测试 API。 |
| 建立后端 JUnit/Testcontainers/ArchUnit，前端 ESLint/TypeScript/Vitest/Vue Test Utils/Playwright 和 CI | PASS | 对应依赖、配置、测试目录及 `.github/workflows/` 门禁均已建立；ArchUnit 覆盖 Controller→Mapper 和业务模块→厂商 SDK 边界。 |
| 建立统一 API Client、错误处理、认证 Store、路由权限和代码分包 | PASS | 前端 TypeScript API client、Pinia auth store、路由 meta/守卫、统一错误处理及 Vite 分包均已建立。 |

### 2.2 Phase 0 验收

| 验收项 | 结果 | 证据与备注 |
| --- | --- | --- |
| 无 API Key、无 Milvus 时 `mvn clean verify` 通过并启动核心应用 | PASS | 完整门禁结果为 139 tests、0 failures、0 errors、3 skipped，BUILD SUCCESS；跳过项为 Docker 不可用时的 Testcontainers 集成测试。核心 Spring 上下文、安全与业务测试均在 stub/disabled AI 配置下通过。 |
| 前端 lint/typecheck/test/build 全绿 | PASS | ESLint 通过；TypeScript typecheck 通过；Vitest 8 files / 18 tests 通过；Vite production build 通过。存在 Element Plus vendor chunk 约 850.57 kB 的非阻断警告。 |
| 全新数据库可由 Flyway 一次建成 | PASS | 本轮有效容器日志确认在空 MySQL 8.4.4 上校验并顺序应用 3 个迁移，Schema 达到 V3。修正测试 profile 的数据源驱动优先级后，最终复跑因 Docker daemon 已停止而被 Testcontainers 跳过；按本轮约定不继续追查。 |

**Phase 0 结论：PASS。** 当前不存在阻断 Phase 0 验收的代码问题。

## 3. Phase 1：身份、RBAC、课程与资源闭环

### 3.1 任务审计

| `roadmap.md` 任务 | 状态 | 依据 |
| --- | --- | --- |
| BCrypt、Redis Session、登录限流、注销、CSRF、审计和课程成员授权 | PASS | 密码哈希、Spring Security/Session、Cookie CSRF、登录限流、注销、全局/课程角色授权和审计链路均已实现；HTTP 权限矩阵覆盖伪造身份头、本地角色篡改、跨课程 IDOR 和教师操作越权。 |
| 课程、教学班、学期、邀请码、成员、章节、知识点及公告/资源元数据 | PASS | 对应实体、Repository、Application Service、Controller 和 Flyway 表结构完整；公告创建及资源上传/删除审计已补齐并有服务测试。 |
| 课程原始资料写入 MinIO，本阶段只登记和下载 | PASS | 上传、对象存储、元数据登记、鉴权下载和删除链路已实现；MinIO Testcontainers 方法执行通过。本轮未把 Phase 3 的解析/索引作为 Phase 1 验收条件。 |
| 管理员最小后台；教师和学生共用统一门户 | PASS | 管理员账号/角色/课程审计入口已建立；前端按路由元数据与服务端权限呈现管理员、教师和学生能力，没有复制三套前端。（2026-09-24 修订：统一 App Shell 已重构为 `/admin`、`/teacher`、`/student` 三个角色工作台，仍共享同一 Shell 组件、API Client 与认证体系，见第 9.3 节。） |

### 3.2 Phase 1 相关公共契约

| 契约 | 状态 | 依据 |
| --- | --- | --- |
| `/api/v1`、统一 `ApiEnvelope`、分页及正确 HTTP 错误状态 | PASS | Controller 契约测试和全局异常处理覆盖成功/错误响应及 traceId。 |
| 身份仅来自认证 Principal，不信任业务请求中的 `userId` | PASS | 新增 HTTP 集成测试证明伪造 `X-User-Id`、角色及 authorities 不能提权。 |
| 注册、登录、注销、当前用户；Session Cookie 与 CSRF | PASS | 安全集成测试覆盖登录会话、Cookie CSRF 和身份读取。生产配置启用 Redis Session 与 Cookie 安全属性。 |
| 全局 `ADMIN/USER` 与课程 `TEACHER/TA/STUDENT` | PASS | 全局和课程角色均进入持久化模型及服务端授权判断，前端状态只影响显示、不授予权限。 |

### 3.3 Phase 1 验收

| 验收项 | 结果 | 自动化证据 |
| --- | --- | --- |
| 管理员创建教师；教师创建课程、教学班、邀请码并上传资料 | PASS | Phase 1 Playwright 场景通过；后端管理员、课程、班级、邀请码、上传服务及权限测试通过。 |
| 学生注册、通过邀请码加入并查看授权课程和资源 | PASS | Phase 1 Playwright 场景通过；HTTP 集成测试还验证了被邀请用户加入后可以读取同课程资源。 |
| 跨用户、跨课程、角色越权返回 403/404；本地状态篡改不能提权 | PASS | `PhaseOneAuthorizationIntegrationTest` 与 `SecurityIntegrationTest` 合计 5 tests 通过：普通用户伪造管理员为 403，课程外访问为 403，学生伪造教师操作为 403，跨课程资源 IDOR 为 404，同课程资源为 200。 |

**Phase 1 结论：PASS。** 本轮唯一完成定义“Phase 0 + Phase 1 验收通过”已经满足。

## 4. 本轮为 Phase 0/1 验收所做的最小修正

- 为课程公告创建、课程资料上传和删除补齐审计事件，并增加对应服务测试。
- 增加基于真实登录 Session 的 Phase 1 HTTP 权限矩阵测试。
- 补齐 Phase 1 Playwright 流程：教师建课/教学班/邀请码/上传资料，学生注册/加入/查看资源。
- 修正 MySQL Flyway Testcontainers 测试的 test profile 和 JDBC driver 覆盖。
- 将 MinIO 与 `mc` 镜像切换到可解析的官方 Quay 固定版本；Compose 配置校验通过。

除此以外，本轮未继续实现、优化或主动修复 Phase 2～Phase 6。

## 5. 已超前实现的 Phase 2+ 内容（仅记录，不纳入本轮验收）

| 阶段 | 当前工作树中可见的超前实现 |
| --- | --- |
| Phase 2 | ModelRegistry/ModelRouter/AI gateway、DeepSeek/DashScope provider、版本化 Prompt、AI Trace、结构化输出/TokenStream/ChatMemory、授权 Tool 上下文、Outbox + Redis Streams worker、重试/租约/死信等基础代码。 |
| Phase 3 | 多格式文档摄取、切片与版本元数据、MinIO/Milvus 适配、课程过滤、重索引/删除/对账、Conversation/Message/Citation、SSE 协议及课程 Assistant 前端。 |
| Phase 4 | 作业生命周期、题目/Rubric、草稿/提交/重交、Tutor 操作与策略、Tutor 工具及作业前端。 |
| Phase 5 | 文档/作业/代码 Review、结构化报告、教师确认成绩、SonarQube 异步适配、压缩包安全检查及 Reviews 前端。 |
| Phase 6 | Analytics snapshot/Dashboard、指标与健康检查、配额/限流、安全响应头、运维文档、RAG 评测与负载测试目录、完整生产 Compose。 |

“已存在”不等于这些阶段已达到其各自验收标准；本报告不对 Phase 2+ 完成度作 PASS 判定。

## 6. Phase 2+ 已知但本轮未处理的问题

- 本轮没有运行 Phase 2～Phase 6 的 gap check、strict audit、完整验收、故障注入、RAG Golden Set 或混合负载门禁。
- DeepSeek/DashScope、Milvus、SonarQube 及完整生产 Compose 的真实外部集成未在本轮确认。
- 现有较高阶段前端 E2E 以 mock API 为主，不能替代真实供应商和全栈环境验收。
- 已观察到的课程 Assistant 下载异常传播、SSE 401 全局未认证通知等高阶段前端改进点均保留原状，未在本轮修复。
- Phase 2+ 即使仍有测试、可靠性或架构改进空间，只要不阻断 Phase 0/1，本轮均未处理。

## 7. 测试与质量门禁汇总

> 下表为 2026-09-22 当轮审计的门禁结果快照；2026-09-24 修订后的最新门禁数字见第 9.4 节。

| 门禁 | 结果 |
| --- | --- |
| 后端 `mvn clean verify`（无 API Key、无 Milvus） | PASS：139 tests，0 failures，0 errors，3 skipped；BUILD SUCCESS。 |
| 公告/资源审计定向测试 | PASS：6 tests。 |
| Phase 1 HTTP 权限与安全定向测试 | PASS：5 tests。 |
| 前端 ESLint | PASS。 |
| 前端 TypeScript typecheck | PASS。 |
| 前端 Vitest | PASS：8 files / 18 tests。 |
| 前端 production build | PASS；仅有 vendor chunk 非阻断警告。 |
| Phase 1 Playwright | PASS：3/3 场景。 |
| `docker compose ... config --quiet` | PASS。 |
| MySQL/Flyway 空库迁移 | PASS：一次有效容器运行完成 V1/V2/V3；最终重复运行因 Docker daemon 停止而 skipped。 |
| MinIO Testcontainers | PASS：对象上传/读取集成方法通过。 |
| Redis Testcontainers | 环境未确认：Redis 7.4.2 容器和镜像正常，但当前 Windows JDK 21/Netty 在创建 NIO selector 时触发 `Unable to establish loopback connection`；不是业务断言失败，也不是镜像下载失败。 |
| `git diff --check` | PASS；只有 Git 的 LF→CRLF 提示。 |

环境备注：本机 Docker 29.8 与当前 Testcontainers/docker-java 组合在可用时需要 JVM 参数 `-Dapi.version=1.44`；最终重复运行时 Docker daemon 已停止。该兼容性备注不改变本轮按约定作出的 Phase 0/1 业务验收结论。

## 8. Baseline 判定与后续起点

当前项目已经形成**可编译、可测试、可构建、具备完整身份/RBAC/课程/资源闭环的稳定 Phase 1 代码 baseline**。需要区分两点：

1. 功能与代码质量 baseline 已形成，Phase 0/1 可停止开发。
2. （2026-09-24 更新）原“大规模未提交工作树”问题已消除：基线迁移与后续修复均已提交至 `main`，形成可回退的 Git checkpoint（修订提交序列见第 9 节）；Phase 1 baseline 标签可按团队约定补打。

后续正式开始 Phase 2 时，建议从以下位置继续：

1. 先在 Linux CI 或稳定 Docker 主机上补跑 MySQL/Redis/MinIO Testcontainers，固化 Docker 29 API 兼容参数，避免把本机 Windows selector 问题混入业务判断。
2. 以现有 `ai`、`job` 和 worker 代码为起点，对照 Phase 2 自身验收项重新建立独立审计清单。
3. 优先验证 Fake Provider 的选模/重试/Fallback/结构化输出/Tool 授权，以及 worker/Redis 重启后的不丢任务语义；通过后再进入 Phase 3。

本轮到此停止，不继续 Phase 2+ 开发。

## 9. 报告后修订记录（2026-09-23 / 2026-09-24）

本节记录本报告定稿之后的变更；第 1～8 节保留原审计快照不回改。所有修订均限定在 Phase 0/1 范围与联调缺陷内，未开发 Phase 2+ 功能。

### 9.1 联调缺陷修复（2026-09-23）

- **BUG-01：Docker 部署下学生注册返回 403。** 根因是 CORS 而非 CSRF：前端 nginx 以 `$host` 转发（丢失公共端口）且 Compose 从未转发 `SEFORGE_ALLOWED_ORIGINS`（prod 默认白名单 `https://localhost`），Spring `CorsFilter`（顺序先于 CSRF 过滤器）将携带 `Origin: http://localhost:8081` 的同源 POST 误判为跨域并拒绝。修复：nginx 改用 `$http_host` 转发（保留端口，同源请求走 `CorsUtils.isCorsRequest` 同源豁免）、Compose 转发白名单变量、`.env`/`.env.example` 配置实际 Origin。CSRF 保持完全启用。（提交 `f2e8df2`）
- **BUG-02：Vite dev server 无法连接 API。** 根因是 `SEFORGE_API_PROXY` 从未被设置（仓库仅有 Vite 不读取的 `.env.example`），代理不存在，`/api/v1/*` 全部落在 dev server 自身。修复：提交 `frontend/.env.development` 默认启用代理指向本机后端 `http://127.0.0.1:8080`，机器级覆盖使用 `.env.development.local`。（提交 `b5f7c00`）
- 安全回归测试：新增“非白名单 Origin + 有效 CSRF 仍 403”“白名单 Origin + 真实 Cookie CSRF 流注册 201”两个用例；同时修复 spring-security-test `csrf()` 永久替换共享上下文 `CsrfFilter` 仓库导致的测试顺序脆弱性（每个用例前恢复配置的 `CookieCsrfTokenRepository`）。（提交 `1abc3ff`；文档 `0895ef3`）

### 9.2 角色端入口审计与管理员引导（2026-09-24）

- 审计结论：ADMIN/TEACHER/STUDENT 的前端页面、路由与后端 API 在本报告定稿时**均已存在**；“看不到管理员端/教师端入口”的根因是 `.env` 中 `SEFORGE_BOOTSTRAP_ADMIN_EMAIL/PASSWORD` 为空，`AdminBootstrap` 从未创建任何 ADMIN 账号（触发条件为“不存在 ADMIN 角色用户”），角色化导航按设计对非管理员隐藏入口，形成死锁。
- 处置：本地 `.env` 配置引导凭据并重建 api 容器，任务指定的默认管理员可正常登录；此项为零代码修改（`.env` 不入库）。
- 实机验收：HTTP 全链路（ADMIN 登录 → 创建学期 → 创建 TEACHER → TEACHER 登录 → 创建课程/教学班/邀请码 → STUDENT 注册/加入/查看课程）23/23 PASS，含学生/教师/外部用户对 `/admin/**`、建课、建学期、跨课程读取的越权矩阵（均 403）；真实浏览器三角色入口冒烟 20/20 PASS。

### 9.3 前端信息架构重构与定点修复（2026-09-24）

- 前端由“统一 App Shell + 扁平路由”重构为 `/admin`（管理概览、用户与学期、审计日志）、`/teacher`、`/student` 三个角色工作台，共享 `WorkspaceShell` 基座、API Client 与认证体系；原 `CoursesView` 巨石页拆解为工作台首页（课程创建/邀请码加入）+ 共享 `CourseWorkspaceView`（课程 tab、教学管理、文档摄取监控）；登录与根路径按 `roles/accountType` 分流，旧扁平 URL 全部按角色重定向。（提交 `dc63828`）
- `AdminBootstrap` 加固：引导用户名/邮箱冲突或密码不合规时降级为告警日志，不再以未捕获异常阻断 API 启动（原为崩溃循环，需人工清空环境变量）；新增 5 个单元测试。（提交 `1997d54`）
- 审计日志补读取侧：新增 `GET /api/v1/admin/audit-logs`（分页、时间倒序、HQL join 补全操作者用户名，复用 `/api/v1/admin/**` 的 ADMIN 规则），前端新增 `api/admin.ts` 与管理端审计页/概览页最近事件；配套集成测试验证管理员可读、学生 403。（提交 `7f8fa89`）
- 课程更新/归档 UI：接通既有 `PATCH /api/v1/courses/{id}`（此前后端存在但前端未调用），教师课程工作区与管理员课程审计表均可编辑名称/简介并在 ACTIVE/ARCHIVED 间切换。

### 9.4 最新门禁数字（2026-09-24）

| 门禁 | 结果 |
| --- | --- |
| 后端 `mvn clean verify` | PASS：147 tests，0 failures，0 errors，3 skipped（跳过项为与原基线一致的 Testcontainers 环境项）。 |
| 前端 ESLint / typecheck / production build | PASS（vendor chunk 警告与基线一致）。 |
| 前端 Vitest | PASS：9 files / 21 tests。 |
| Playwright e2e（mock） | PASS：9/9 场景（原 7 场景迁移至新工作台路由，另新增管理员审计页、教师归档课程 2 个场景）。 |
| 实机验收（Docker 栈 + 真实浏览器） | PASS：三工作台业务闭环与越权矩阵 27/27；审计 API admin 200 / student 403。 |

### 9.5 修订后仍已知未处理的问题

- 审计日志无筛选（action/outcome/时间）与导出；无删除用户、重置密码 API；邀请码无吊销端点；教学班无更新/删除；课程无删除。
- ARCHIVED 课程对既有成员仍完全可见（仅阻止新成员加入），归档语义未在 Phase 0/1 范围内进一步定义。
- 实机验收在数据库中留有测试数据（`probe-*`、`*227151`、`ui-*` 前缀的账号、学期与课程）。
- Testcontainers 3 项跳过（Windows Docker 兼容性）与 Element Plus vendor chunk 体积警告维持基线状态。
