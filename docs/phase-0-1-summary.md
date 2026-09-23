# SEForge Phase 0 / Phase 1 阶段总结报告

> 审计日期：2026-09-22  
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
| 管理员最小后台；教师和学生共用统一门户 | PASS | 管理员账号/角色/课程审计入口已建立；统一 App Shell 根据路由元数据与服务端权限呈现管理员、教师和学生能力，没有复制三套前端。 |

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
2. 当前迁移仍位于大规模未提交工作树中，尚不是可回退的 Git checkpoint；在团队复核后应单独提交并打 Phase 1 baseline 标签。

后续正式开始 Phase 2 时，建议从以下位置继续：

1. 先在 Linux CI 或稳定 Docker 主机上补跑 MySQL/Redis/MinIO Testcontainers，固化 Docker 29 API 兼容参数，避免把本机 Windows selector 问题混入业务判断。
2. 以现有 `ai`、`job` 和 worker 代码为起点，对照 Phase 2 自身验收项重新建立独立审计清单。
3. 优先验证 Fake Provider 的选模/重试/Fallback/结构化输出/Tool 授权，以及 worker/Redis 重启后的不丢任务语义；通过后再进入 Phase 3。

本轮到此停止，不继续 Phase 2+ 开发。
