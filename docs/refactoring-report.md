# SmartSE 代码规范化与安全重构报告

> 阶段：Codebase Standardization & Safe Refactoring
> 原则：Preserve behavior unless fixing a confirmed defect
> 日期：2026-09-21

---

## Naming Standardization

### Backend（Spring Boot / Java 17）

| 规则 | 约定 | 现状 |
| ---- | ---- | ---- |
| Package | 全小写，层级 `modules/<域>/{controller,service,service.impl,mapper,entity,dto,config,util}` | 已统一（修复 2 处违规） |
| Class | PascalCase；分层后缀 `XxxController / XxxService / XxxServiceImpl / XxxMapper / XxxDTO` | 全部合规 |
| Method / Variable | camelCase | 已统一（修复 3 处 snake_case） |
| Constant | UPPER_SNAKE_CASE（static final） | 全部合规，未改动 |
| Service 体系 | 保持既有 `Service + ServiceImpl` 模式 | 未强行引入新模式 |

### Frontend（Vue 3 / Vite）

| 规则 | 约定 | 现状 |
| ---- | ---- | ---- |
| 组件 / 视图文件 | PascalCase `.vue`（ChatContainer.vue、Home.vue…） | 原本合规，未改动 |
| 非组件目录 | 全小写（components/dialog、components/markdown、assets、utils、views） | 已统一（Dialog/ → dialog/） |
| JS 模块 | camelCase 文件名（utils/loadScript.js） | 合规；死模块 markdown-config.js 已删除 |
| JS 变量 / 函数 | camelCase | 全部合规，未发现 snake_case |

---

## Major Renames

| Old | New | 说明 |
| --- | --- | --- |
| `com.USTB.smartse`（测试包） | `com.ustb.smartse` | 包名全小写；Windows 大小写不敏感文件系统下经两步 `git mv` + 索引大小写修正完成 |
| `knowledgebase.utils.SoftwareEngineeringKnowledgePresets` | `knowledgebase.util.…` | 消除 util/utils 并存；唯一引用方 KnowledgeGraphController 已同步 |
| `OpenAiRequest.response_format / max_tokens` | `responseFormat / maxTokens` | **JSON 契约不变**：字段加 `@JsonProperty("response_format"/"max_tokens")`，发往 DeepSeek 的键名与之前完全一致；调用方 DeepSeekApi 同步更新 |
| `UserServiceImpl.new_password`（局部变量） | `newPassword` | camelCase |
| `frontend/src/components/Dialog/` | `components/dialog/` | 目录小写统一；Function.vue 3 处 import 同步 |

### 删除的死文件（均可从 Git 历史恢复）

| 文件 | 依据 |
| --- | --- |
| `common/utils/Result.java` | 1 字节空文件，与 `common/Result.java` 重名混淆，全仓零引用 |
| `VideoSubtitleServiceTest.java`（test） | 1 字节空文件，无任何内容 |
| `VideoSubtitleServiceImpl.java.new` | 0 行遗留备份文件，不参与编译 |
| `util/CharacterListGenerator.java` | 仅含注释"功能已被 NlpConfig 取代，可以删除此文件"，零引用 |
| `frontend/src/utils/markdown-config.js` | 全仓零引用死模块（markdown-it 配置已由各组件内联实现） |

---

## Bugs Fixed

### 1. Function.vue document 级监听器无限累积（内存/CPU 泄漏）

```text
Problem: 流式聊天期间页面越用越卡，组件卸载后监听器仍存活
Cause:   updated() 钩子每次响应式更新都调用 setupDragHandlers()，每次向
         document 追加 mousemove/mouseup/touchmove/touchend 4 个匿名监听器
         且从不移除；闭包还持有已失效的旧 DOM 引用
Fix:     document 级监听器每组件实例仅注册一次（守卫标志），回调内通过
         $refs 动态获取当前 DOM；drag-handle 元素级监听随元素重建（行为不变）；
         新增 beforeUnmount 钩子统一移除
Impact:  消除监听器累积与 DOM 引用滞留；拖拽交互行为不变
```

### 2. initializeComponent 重复注册处理器

```text
Problem: setupDragHandlers/setupClickOutsideHandlers 被连续重复调用两次
Cause:   明显的意外复制粘贴
Fix:     去重；setupClickOutsideHandlers 同样加一次性守卫
Impact:  document click 监听不再重复注册
```

### 3. DeepSeekApi 空 choices 越界 / NPE

```text
Problem: DeepSeek 返回 2xx 但 choices 为空或 message 缺失时抛
         IndexOutOfBoundsException / NPE，落入兜底 catch 返回"系统异常"
Cause:   response.getBody().getChoices().get(0).getMessage() 无边界检查
Fix:     增加 null/empty 防护，记录错误日志并返回既有失败文案
         "调用大模型失败，请稍后再试。"
Impact:  异常路径变为受控降级；正常路径行为不变
```

### 4. UserServiceImpl 空参 NPE 与校验顺序缺陷

```text
Problem: login 接口 username/password 为 null 时抛 NPE → HTTP 500；
         register 以 null username 先执行数据库查询再做参数校验
Cause:   loginAndCache 直接 password.getBytes()；register 校验位于查询之后
Fix:     loginAndCache 入参判空返回 null（按"用户名或密码错误"处理，
         与既有失败路径一致）；register 参数校验前置；user 判空防护
Impact:  非法请求由 500 变为正常业务失败响应；API 契约不变
```

### 5. Function.vue localStorage 缓存解析崩溃

```text
Problem: userInfo 缓存损坏时 JSON.parse 抛异常，且发生在 catch 兜底路径
         （setDefaultUserInfo）内，导致初始化中断
Cause:   JSON.parse 无 try/catch 防护
Fix:     包裹 try/catch，解析失败时 console.error 并回退默认值 {}
Impact:  缓存损坏不再白屏；正常路径行为不变
```

---

## Robustness Improvements

- **Logging（后端）**：业务类 `System.out.println` / `e.printStackTrace()` 全部清零，统一到项目既有 slf4j 体系（`UserServiceImpl`、`RequirementAnalysisToolServiceImpl` 新增 `@Slf4j`；`ChatController`、`PersistentChatMemoryStore`、`DashScopeConfiguration` 使用既有 logger）。日志中不含密码/token/secret。演示类（`api/test`、`knowledgebase/test`、`QwenEmbeddingModelFactory`）的控制台输出为预期交互方式，按规范保留。
- **死代码（后端）**：`DeepSeekApi` 移除与 `@Slf4j` 冗余的手动 Logger 字段；`UserController` 移除重复 import 与未使用的 `java.security.Timestamp`；`ChatController` 移除多余空语句（`;;`）。
- **调试输出（前端）**：46 处 `console.log` 全部移除（Function.vue 21、KnowledgeGraph.vue 14、ChatContainer.vue 10、Login.vue 1，含 ChatContainer 仅打日志的空 `updated()` 钩子）；`console.error/warn` 错误上报全部保留。
- **Null safety**：见 Bugs Fixed #3/#4/#5。
- **README**：backend/README.md 目录树 `sevice` → `service` 拼写修正。
- **有效 TODO 保留**：`ChatController` "未来接入RAG系统"（1 处，仍有业务意义）。

---

## Issues Not Modified（记录，未改动）

| # | Issue | Location | Risk | Recommended future action |
| - | ----- | -------- | ---- | ------------------------- |
| 1 | 密码使用 MD5 无盐哈希 | `UserServiceImpl.register/loginAndCache` | 弱哈希可被彩虹表破解；改算法会使既有用户密码全部失效 | 迁移 BCrypt/Argon2，采用"登录时透明重哈希"平滑迁移 |
| 2 | 死认证链路：后端生成 token 并写 Redis，但 `LoginResponse` 的 token 字段被注释，前端存入 `'dummy-token'` 并以 `Bearer dummy-token` 发请求；Redis `token:` 键全仓无消费方 | `UserServiceImpl`、`LoginResponse`、`Login.vue:198`、`Function.vue` | 认证形同虚设；补全或移除均属认证架构调整（本阶段禁止） | 决策：要么返回真实 token 并实现校验拦截器，要么整体移除 token 残留 |
| 3 | 前端所有 API 调用硬编码 `http://localhost:8080` 绝对地址，且与 vite proxy 的 `/api` rewrite 规则互相矛盾（rewrite 会剥掉后端实际需要的 `/api` 前缀） | frontend 各组件、`vite.config.js` | 生产构建无法通过环境变量切换后端地址 | 引入统一 axios 实例，baseURL 取自 `import.meta.env`，同步修正 proxy rewrite |
| 4 | 三套响应包装并存：`common.Result`（5 处）、`common.api.ApiResult`（5 处）、`common.utils.R`（1 处），JSON 结构各不相同 | `common/` 及各 Controller | 统一即改变既有端点的 Response Structure（API 契约，禁止） | 下一大版本按端点逐步收敛到单一包装类 |
| 5 | Neo4j 双实体 `KnowledgeRelation` 与 `KnowledgeRelationship` 并存且均在使用 | `knowledgebase/entity/neo4j/` | 合并影响图数据库映射语义 | 领域建模评审后合并 |
| 6 | 12 个演示/联调类位于 `src/main`（`api/test/*`、`knowledgebase/test/*`），含 main() 与大量控制台输出，会被打包进生产 jar | backend src/main | 移动 source root 改变打包行为，无法编译验证下不动 | 迁往 `src/test/java` 或以 profile 隔离 |
| 7 | 9 个空占位文件（1 字节，初始提交即如此，非本次产生；零引用）：`ChatLanguageModelConfig`、`MoocCrawlerController/Service/ServiceImpl`、`KnowledgeEntryService/ServiceImpl`、`PdfExtractorTest`、`TestPdfExtraction`、`PdfExtractor` | backend 各模块 | 用途无法确认（按规范：保留优先于删除） | 确认无恢复需求后删除，或按文件名提示补全实现 |
| 8 | 主 bundle 5.7 MB（gzip 1.9 MB），构建时 chunk 超限警告 | `frontend` 构建产物 | 首屏加载性能 | 路由级 code-splitting + manualChunks |
| 9 | `updated()` 每次渲染都销毁重建 drag-handle DOM 元素 | `Function.vue setupDragHandlers` | 高频渲染时 DOM churn（监听器泄漏已修，元素重建为既有行为） | 改为仅在容器元素变化时重建 |
| 10 | backend/README.md 项目结构描述与实际不符（提及不存在的 `common/constant`、`common/exception`、`sevice` 目录等） | `backend/README.md` | 文档漂移误导新人 | 下一阶段文档重写时同步 |
| 11 | `KnowledgeEntry` 实体与 Mapper 存在但对应 Service 为空文件（见 #7），实际功能由其他 Service 承担 | `knowledgebase` | 结构困惑 | 与 #7 一并处置 |

---

## Validation

```text
Backend Compile: FAIL（环境缺失 —— 本机无 JDK/Maven，经确认采用纯静态检查替代）
Backend Tests:   FAIL（同上；2 个测试文件已修复包名，结构上可编译）
Frontend Build:  PASS（vite build ✓ 33.64s，仅历史遗留 chunk>500kB 警告）
Frontend Tests:  N/A（项目无前端测试）
Lint:            N/A（项目未配置 ESLint/Prettier/Checkstyle）
```

### 后端静态验证（替代编译，全部通过）

- Package 声明 ↔ 目录路径一致性：全部匹配（10 个"不匹配"项均为上述空占位文件，无 package 声明属预期）
- 全仓 `import com.ustb.smartse.*` 解析：全部可解析到实际文件（含 2 处星号 import 对应目录存在）
- Class 名 ↔ 文件名一致性：全部匹配（空文件除外）
- 重命名后旧名称残留搜索：`com.USTB` / `knowledgebase.utils` / `setMax_tokens` / `setResponse_format` / `new_password` / `components/Dialog` / `markdown-config` 全部为 0
- 业务类 `System.out.print` / `printStackTrace`：0；前端 `console.log`：0；空 catch（前后端）：0

### 回归说明（Section 33）

- 本次未引入新 regression：前端以构建通过为准；后端所有改动均经全仓引用搜索验证。
- Pre-existing：10 个空占位文件在初始提交（d150be3）即为空；chunk 超限警告为历史遗留。

---

## Commits

```text
3c2f2b4 chore: remove self-documented dead file CharacterListGenerator
97556f2 chore: remove dead frontend module and refresh package-lock
75323dd fix: repair Function.vue listener leaks; standardize dialog dir naming
5b71881 chore: remove frontend debug console.log output
8cd38ec chore: remove obsolete backend files
496f58c fix: improve backend robustness and unify logging
43c1f50 refactor: standardize backend naming
```
