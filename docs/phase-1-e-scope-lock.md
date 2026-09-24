# Phase 1-E · Identity & Role Workspace Closure 范围锁

> 批准依据：项目负责人于 2026-09-24 提供的《SEForge Phase 1-E：Identity & Role Workspace Closure》任务指令。
> 基线：`main` / `a54d5c8a288dd4f4befa3c88346e030755d75e46`；实施开始时工作树干净。
> 阶段依据：`docs/roadmap.md` 的 Phase 0、Phase 1；本任务是 Phase 1 产品化增强，不是产品优先级 P0/P1 全量开发。

## 批准范围

- 身份：学生唯一学号注册、登录、存量空学号安全补录；教师与管理员资格验证、账号治理、状态及凭据管理。
- 入口：`/login` 的学生/教师选择、独立 `/login/admin`，共享现有认证 API、Session、CSRF、Store 和 Shell。
- 管理员：真实基础统计、教师/学生管理、受控学生 CSV 导入、学期、全局课程治理及审计查询。
- 教师：课程、班级、成员、邀请码、章节、知识点、原始资源和公告的基础教学管理。
- 学生：学号登录、课程加入与授权内容、个人资料。
- 服务端权限、前后端测试、当前 Phase 0/1 门禁、隔离环境真实链路验收和最终报告。

## 排除与安全边界

- Phase 2～6 的 AI、RAG、作业、Tutor、Review、Analytics 功能补全及发布验收不在范围内；已有代码保留，仅允许必要的编译/路由兼容调整。
- 不删除现有数据库/卷，不改历史 Flyway migration，不猜测或自动生成存量学生学号；新增 Schema 只使用新的 Flyway 迁移。
- 不关闭或放宽 Session、Cookie、CSRF、限流和服务端 RBAC；前端角色选择仅表达入口意图。
- 未经项目负责人验收，最终状态只能为 `User Acceptance: PENDING`，不得开始 Phase 2。

## 验收与回退

每个纵向闭环执行定向测试；最终执行后端 verify、前端 lint/typecheck/test/build、服务端正反权限矩阵，以及隔离环境的 Docker/Vite 真实认证与课程资源链路。Docker 环境不可用时明确记为阻断而非通过。代码回退以本基线及本轮差异为依据；数据库迁移采用前向修正，不清库回退。
