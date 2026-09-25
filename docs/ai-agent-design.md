# SEForge · UI / Agent / Tool 短设计审查

> 2026-09-24；依据当前源码、`docs/roadmap.md`、`codex.md`、Phase 1-E 交接及 Phase 2+ 现状报告。本文冻结设计边界，不代表 Phase 2～5 功能已验收，也不授权本轮修改源码。Phase 1-E 工程基线保留，用户验收仍以交接报告为准。

## 1. 审查结论

- **UI：沿用单应用、单共享 Shell。** `frontend/src/router/index.ts` 按工作区与课程能力导航，`WorkspaceShell.vue` 承载相同的侧栏、课程切换和用户菜单；Admin/Teacher/Student Layout 只是导航配置，不应演变成三套组件或视觉系统。`app.css` 已有少量颜色/容器变量与 `PageHeader`、`EmptyState` 等公共件，但表单、表格、反馈状态和语义色尚未形成完整规范。
- **Agent：按业务边界分工，不建“多 Agent 聊天室”。** 当前 `CourseQaStreamService`、`TutorService`、`ReviewExecutionService` 分别完成不同程度的业务编排；公共调用入口已有 `AiGateway`、`ModelRegistry`、`PromptCatalog`。未来只有跨职责编排确有独立收益时才增加 Orchestrator。
- **Tool：授权先于模型调用，服务端上下文不可被模型覆盖。** `AuthorizedTutorTools` 已展示 request-scoped、无身份 ID 入参的做法；但不能把目前所有检索或 worker 读数都称为“统一授权 Tool Runtime”。Course QA 目前由服务端预检索，Review worker 直接从领域仓储组装材料。两者仅记录现状，不能为了本设计审查提前重构 Phase 3～5。

## 2. UI 统一方案

**本阶段冻结的骨架**：Vue 3 + TypeScript + Pinia + Element Plus；保留 `/admin`、`/teacher`、`/student` 工作区和共享 `WorkspaceShell`。路由元数据、可见性和工作区切换只负责体验，所有课程成员/教学职责/管理员权限仍由 API 校验，不能从前端状态推导授权。

**公共规范建议**：在现有 `app.css` 变量上增补中性色、语义状态色、8px 间距节奏、字号层级、圆角与焦点态；复用 PageHeader、EmptyState、StatusBadge、CourseCard，后续新增页面优先复用统一的 loading、error、empty、success、disabled 模式。课程上下文应以 URL 中的 `courseId` 为页面事实来源，Store 仅缓存；教师/学生对同一课程页面继续复用视图，通过服务端返回的能力与课程角色决定操作入口。AI 页面展示答案、引用、任务状态和可恢复错误，不显示内部 Chain-of-Thought。

**现状与边界**：`TeacherLayout.vue` 与 `StudentLayout.vue` 仍各自维护相似课程导航，这是可控的配置重复；`WorkspaceShell.vue` 的 `enabled: false` 目前主要表现为样式，后续 UX 检查应确认键盘和点击行为。Phase 2 只允许公共规范和明显 UX 修正，不开展换主题、重排所有页面或大规模视觉重做；Course Assistant、Assignment、Review 页面稳定后再集中 polish。

## 3. Agent 职责契约

所有 Agent 都经业务应用服务发起，经公共 `AiGateway` 请求能力（`FAST` / `REASONING` / `CODING` / `EMBEDDING`），使用版本化 Prompt、受限上下文、结构化结果与 AI Trace。模型输出均是不可信建议；身份、课程、作业、提交、评分状态由服务端决定。Agent 不直接调用厂商 HTTP、Mapper/Repository 或绕过业务授权；不输出内部推理过程。

| Agent / 阶段 | 职责与输入 | 输出 | 允许 Tool / 数据来源 | 禁止行为 |
| --- | --- | --- | --- | --- |
| Course QA / Phase 3 | 已认证用户、课程内会话与问题；只用当前课程可用文档 | 答案、独立 Citation DTO、无依据拒答；流式唯一终态 | `CourseKnowledgeTool`/受权课程检索、`CourseContextTool`；会话历史经服务层构造 | 跨课程检索、编造引用、以文档内容当指令、泄露 CoT；无证据时不能硬答 |
| Tutor / Phase 4 | 当前作业、题目、学生草稿、提交状态与教师策略；六种明确操作 | 渐进提示或获准的完整解析、引用、交互审计 | `AssignmentTool`、`SubmissionTool`、`CourseKnowledgeTool`、`KnowledgePointTool`；仅服务端绑定当前学生/题目 | 提交前越过策略泄露参考答案、修改草稿/提交/成绩、读取他人答案 |
| Review / Phase 5 | 教师授权的文档或已提交作业、Rubric 与证据 | 文档四维结构化评审，或逐 RubricItem 的分项建议、证据与问题 | `CourseContextTool`、`RubricTool`、受权文档/提交读取服务 | 将建议分数写成最终成绩、遗漏 RubricItem、无证据断言、代替教师确认 |
| Code Review / Phase 5 | 安全校验后的源码包与 SonarQube 权威静态结果 | 逐 finding 的解释、影响、修复建议及报告 | `StaticAnalysisTool`/Sonar 网关的只读结果 | 执行学生代码、伪造/遗漏静态 finding、把 AI 判断冒充扫描结论、允许扫描进程任意出网 |
| Teacher Agent / 后续增强 | 教师授权的课程与聚合教学数据 | 待教师确认的题目草案、教学总结或建议 | `CourseContextTool`、`KnowledgePointTool`、`StatisticsTool`；按后续阶段另行批准 | 自动发布题目/公告、修改成绩或成员、输出可识别的学生个人画像；**本阶段不实现** |

现有 `CourseQaStreamService` 是“服务端检索 + 流式回答”，并非模型自主调用 Tool；`ReviewExecutionService` 在 worker 中准备材料、调用模型并校验结果，也不是已统一的 Agent Tool 目录。保留这些现有实现，不将设计目标误写为已完成。

## 4. Tool 契约与授权边界

**调用链**：Controller/worker 获取可信 Principal 或持久 Job 身份 → Application Service 重新校验成员、资源归属、操作策略 → 为一次请求创建不可变 `ToolContext`（actor、course、assignment/question/submission、允许操作、trace/request ID）→ 只注册该请求获准的 Tool → Tool 调用受权 Application Service → 返回裁剪 DTO 并记录成功/失败、耗时、模型与 Prompt 版本。worker 重试时须重新校验数据状态；持久 Job 中的 ID 是定位线索，不是授权凭证。

| Tool Catalog | 最小能力与返回 | 授权条件 |
| --- | --- | --- |
| `CourseKnowledgeTool` | 限量检索当前课程证据与可定位引用 | 当前课程成员；课程 Filter 在检索层强制下推并复核 |
| `CourseContextTool` | 课程、章节与可公开的上下文 DTO | 当前课程成员；教师专有字段另行授权 |
| `AssignmentTool` / `SubmissionTool` | 当前可见题目、当前学生草稿/提交状态 | 题目可见性与当前 actor 归属；不能由模型选择其他学生 |
| `KnowledgePointTool` / `RubricTool` | 当前课程知识点、按用途裁剪的 Rubric | 课程范围；参考答案/评分细则服从 Tutor 与教师策略 |
| `StatisticsTool` | 已授权的聚合指标 | 教学人员权限、班级范围和最小样本保护；Teacher Agent 后续才开放 |
| `StaticAnalysisTool` | 指定 Review Job 的权威扫描 finding | 已授权任务、固定扫描结果；Code Review 阶段才开放 |

模型可填写的参数只限查询词、限量等业务输入；`userId/courseId/assignmentId/submissionId` 不在模型可写参数中。Tool 默认只读，设置输入长度、返回条数、Token/调用次数、超时和故障预算；异常以明确失败返回，不能降级成“成功但空结果”。任何写 Tool 必须单独获得业务授权、幂等键与审计，并由 Application Service 执行；当前目录不预授予写权限。Prompt、Tool 返回和学生文档均视为不可信数据，不回显密码、Session、密钥、完整敏感正文或不必要的他人信息。Trace 默认只保存元数据与安全错误码，不保存完整 Prompt/答案。

## 5. Phase 2 开工门槛与未决项

1. **只批准公共 Runtime，不借此补完业务 Agent。** 先确认 `AiGateway`/ModelRegistry/Router、Prompt、Trace、结构化输出与流式唯一终态的公共契约；`EMBEDDING` 枚举虽存在，但当前仍从 `content/infrastructure/DashScopeEmbeddingProvider` 走独立入口，须在 Phase 2 明确收口方案。
2. **冻结授权 Tool API 再实现共享机制。** 以现有 `AuthorizedTutorTools` 的服务端绑定模式为参照，定义 DTO、调用预算、审计与失败语义。当前 `AssignmentTool`、`SubmissionTool`、`KnowledgePointTool` 内部虽有授权检查，但自身直接使用 Repository；未来统一 Tool 边界应位于授权 Application Service 之上，不把 Repository 暴露给模型或通用 Tool Runtime。是否调整现有 Phase 4 代码须另按阶段授权，不在本轮处理。
3. **验收只看 Phase 2 证据。** Fake Provider 验证选模、Fallback、超时/429、无效 JSON、Tool 越权和模型不可伪造上下文；真实 Redis/worker 重启验证任务不丢失和唯一终态；AI 不可用时传统课程 API 仍可用。Phase 3～5 的 Milvus、Tutor 全流程和 SonarQube 缺口仍是后续阶段验收项，不作为本次设计审查的编码任务。

本设计审查结论：**方向可冻结，Phase 2 编码仍需项目负责人单独批准。** 本文仅明确职责与边界，不替代用户验收或运行测试。
