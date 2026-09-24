# SEForge Phase 1-E 阶段实施与工程验收报告

> 日期：2026-09-24（Asia/Shanghai）  
> 基线：`main` / `a54d5c8a288dd4f4befa3c88346e030755d75e46`  
> 范围：项目负责人批准的 *Identity & Role Workspace Closure*；不是产品优先级 P0/P1，也不是 Roadmap Phase 2～6 验收。  
> 状态：**Engineering Verification: PASS**；**User Acceptance: PENDING**；**Permission to start Phase 2: NOT GRANTED**。

## 1. 完成内容

| 闭环 | 本轮结果 |
| --- | --- |
| 身份与入口 | `/login` 明确选择学生/教师，`/login/admin` 独立管理员入口；入口选择只作为认证意图，服务端按账号类型/ADMIN 角色验证。学生按唯一 `studentNo` + 密码登录，教师维持用户名/邮箱登录；Bootstrap 管理员为 `PLATFORM` 账号，不自动取得教师资格。 |
| 存量学生兼容 | 保留原有 `student_no IS NULL` 记录，不猜测学号、不清库；凭原用户名/邮箱和密码可安全补录学号，管理员亦可在账号详情补录。邮箱仍为联系字段，不再作为学生登录标识。 |
| 管理员 | 真实教师/学生/课程/当前学期统计，账号创建、搜索、分页、详情、启停、基础资料、课程班级归属与一次性凭据重置；学期创建/修改/状态；全局课程查询、编辑、归档/恢复与负责人调整；审计筛选和分页。账号、学期、课程在管理页以标签分区展示。 |
| 受控导入 | UTF-8 CSV 限 1 MB/1000 行；`上传 → 服务端校验/预览 → 确认同一 SHA-256 摘要文件 → 逐行写入 → 逐行结果`。文件内/数据库内重复学号、用户名、邮箱均报告；重导入跳过已有账号。随机初始密码仅在成功结果中展示，可下载结果后经安全渠道交付。 |
| 教师 | 课程创建/修改/归档恢复；教学班创建/修改/关闭及成员容量校验；成员查询/角色及班级调整/移除；学生邀请码创建/查看/吊销；章节及知识点 CRUD；原始资料经 MinIO 上传、下载和软删除；公告创建/编辑/撤回。 |
| 学生 | 注册必须提供学号；学号登录、邀请码入课、我的课程、授权章节/知识点/资源/公告、个人资料页面。 |
| 服务端授权 | 平台 ADMIN 可治理但不能凭 ADMIN 身份创建课程或执行教学写入；教师资格与具体课程 TEACHER/TA/STUDENT 成员角色分别校验。跨课程读取和课程对象 IDOR 由服务端拒绝，非成员不能读取私有资源。与既有高阶段页面的 ADMIN 教学旁路仅作最小安全兼容移除，未补全高阶段功能。 |

## 2. 数据与兼容安全

- 新增 `V4__phase_one_e_course_class_status.sql`，为教学班增加可关闭/恢复状态；已有班级默认保持可用。历史 Flyway V1～V3 未修改。学号唯一约束沿用 V1 已有 `uk_user_profiles_student_no`，原有空学号保持空值。
- 现有 SEForge 数据库、数据卷、Compose 项目和 `.env` 未重置或覆盖。验证使用独立 `seforge-phase1e` Compose 项目及独立卷，本机端口 `8187/3317/6387`；验收后仅停止该项目容器，卷仍保留。生产默认安全 Cookie/CSRF 未放宽；`SESSION_COOKIE_SECURE=false` 只用于独立本地 HTTP 验收进程。
- 由于 Dockerfile 前置镜像元数据解析缓慢，隔离验收使用本机已有固定运行镜像，按 `backend/docker/phase1e-verify.override.yml` 只读挂载本轮打包 JAR 与构建后的前端产物。已证明 Compose 运行态，不把这项结果冒充“新镜像完整重建成功”。

## 3. 当前测试与质量门禁

| 门禁 | 当前结果 | 证据边界 |
| --- | --- | --- |
| 后端 `mvnw.cmd -q verify` | **PASS**：155 tests，0 failure，0 error，3 skipped | 3 项属于 Flyway/MySQL、Redis/MinIO Testcontainers，在本机 Windows Testcontainers Docker provider 发现阶段跳过；不是实测通过。 |
| 前端 lint / typecheck / Vitest / build | **PASS**：Vitest 26/26；生产构建成功 | Element Plus 厂商包仍有 >500 kB 的非阻断 chunk warning，未在本轮做 Phase 2+ 性能优化。 |
| 前端模拟 Playwright | **PASS**：13 条通过；2 条真实栈用例在无凭据的默认运行中按设计跳过 | 覆盖入口、表单校验、管理员账号/CSV、教师基础课程、学生入课与 403 提示；保留的高阶段路由用例亦未回归。 |
| 隔离 Compose 真实运行 | **PASS**：MySQL、Redis、MinIO、API、frontend 均达到 healthy；Flyway 从空库应用 V1–V4 | 采用当前 JAR/前端产物只读挂载，不依赖外部 AI、Milvus、SonarQube。 |
| 真实 API 闭环与反向矩阵 | **PASS**：`frontend/e2e/phase1e-real.spec.ts` 2/2 | 管理员建教师；学生学号注册/登录；教师建课、建班、邀请码、MinIO 原始资料、公告；学生入课/下载/查看；CSV 预览确认及重导；TA 权限；停用账号旧会话与新登录拒绝；学生/非成员/其他教师/纯管理员越权拒绝。 |
| Docker 与 Vite 真实页面入口 | **PASS** | Docker 前端的管理员与学生登录、Vite 代理同一隔离 API 的教师登录均进入正确工作台。 |
| 差异检查 | **PASS** | `git diff --check` 无空白错误；工作树保留本轮未提交改动。Git 在 Windows 给出 LF→CRLF 提示，不影响检查结果。 |

## 4. 仍需如实记录的边界

1. 用户验收尚未发生；自动化和本轮实机工程验证不能代替项目负责人亲自验收。`User Acceptance` 保持 `PENDING`。
2. 管理审计导出是原指令允许延期的非阻断增强项；查询、筛选、分页与详情已可用。
3. 受控导入返回初始凭据仅一次；管理员必须安全保存并交付。导入结果中的失败或跳过行不会回滚先前成功行，符合部分失败结果报告要求。
4. 本机 Testcontainers 的 3 项跳过仍需后续 CI/环境配置复核；本轮已用独立真实 Compose 对 MySQL/Flyway、Redis Session、MinIO 上传/下载主链路作替代实测。
5. Phase 2～6 已有实现全部保留、未纳入本轮验收。既有 `docs/phase-2-plus-development-status.md` 仍是高阶段现状依据；本轮未对其缺口开展补全或发布审计。

## 5. 阶段结论与交接

**Phase 1-E 已形成可运行、可复验的工程基线**：管理员治理、教师基础教学管理、学生学号身份及入课资源流程在独立真实栈闭环；服务端权限边界与浏览器入口均通过正反向验证。实施改动尚未提交，等待项目负责人审阅与用户验收。未经明确批准，不进入 Phase 2，也不把高阶段模拟测试通过解释为高阶段验收。

复验时先以独立 Compose 项目启动 `backend/docker/phase1e-verify.override.yml` 所示挂载，再设置 `SEFORGE_REAL_PHASE1E_BASE_URL`、`SEFORGE_REAL_ADMIN_USERNAME`、`SEFORGE_REAL_ADMIN_PASSWORD` 和指向独立栈的 `SEFORGE_API_PROXY`，运行 `npx playwright test e2e/phase1e-real.spec.ts --workers=1`。只允许在隔离数据库上运行会创建账号/课程的真实测试。
