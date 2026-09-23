# SmartSE Codebase Standardization & Safe Refactoring

## 1. Task Overview

SmartSE 最初由约 5 名开发者协作完成。

由于早期开发阶段没有建立统一的工程规范，目前项目中可能存在：

* 文件命名风格不统一；
* Java 类、方法、变量命名不统一；
* Vue 组件及前端文件命名不统一；
* 包结构存在历史遗留问题；
* API、DTO、VO 等命名不一致；
* 相似功能采用不同代码风格；
* 重复代码；
* 异常处理方式不统一；
* 空值处理不健壮；
* 边界条件处理不足；
* 部分代码存在潜在 Bug；
* 部分实现存在明显稳定性或可维护性问题。

本阶段目标：

> 在保持 SmartSE 现有业务功能和整体架构基本不变的前提下，对整个项目进行一次系统性的代码规范化、安全重构和质量检查。

允许修改代码。

但必须遵循：

> **Preserve behavior unless fixing a confirmed defect.**

不要为了“代码看起来更现代”而改变正常业务行为。

---

# 2. Scope

扫描整个 SmartSE Monorepo：

```text
SmartSE/
├── backend/
├── frontend/
├── docs/
├── ...
├── README.md
└── CLAUDE.md
```

重点检查：

```text
backend/
frontend/
```

包括：

* 文件名；
* 目录名；
* Java package；
* Java class；
* method；
* variable；
* constant；
* Vue component；
* TypeScript / JavaScript 文件；
* API module；
* composable；
* utility；
* store；
* DTO / VO / Entity；
* Controller / Service / Repository / Mapper；
* 配置文件；
* 测试文件。

---

# 3. General Principles

所有修改遵循以下优先级：

```text
Correctness
    ↓
Stability
    ↓
Consistency
    ↓
Maintainability
    ↓
Readability
    ↓
Style
```

不要为了统一风格而牺牲正确性。

不要为了减少代码行数而降低可读性。

不要为了使用某种设计模式而增加不必要复杂度。

---

# 4. Naming Standardization

首先扫描整个项目，识别当前存在的命名风格。

在执行批量修改之前：

1. 分析当前主流命名方式；
2. 判断哪些属于合理命名；
3. 找出明显不一致的部分；
4. 建立统一规则；
5. 再进行修改。

不要机械重命名所有文件。

---

# 5. Backend Naming Convention

Spring Boot 后端统一采用 Java 常见命名规范。

## 5.1 Packages

package 使用全小写：

```text
controller
service
repository
mapper
entity
dto
vo
config
exception
security
util
```

避免：

```text
Controller
Services
Utils
DAO
serviceImpls
```

除非现有框架或项目结构确实需要。

---

## 5.2 Classes

使用 PascalCase：

```java
UserController
UserService
UserRepository
UserDTO
UserVO
UserEntity
GlobalExceptionHandler
```

避免：

```text
userController
user_controller
USERController
Usercontroller
```

---

## 5.3 Methods and Variables

使用 camelCase：

```java
getUserById()
createProject()
currentUser
projectId
userList
```

避免：

```text
get_user()
GetUser()
project_id
UserList
```

---

## 5.4 Constants

使用：

```java
MAX_RETRY_COUNT
DEFAULT_PAGE_SIZE
TOKEN_EXPIRE_TIME
```

即：

```text
UPPER_SNAKE_CASE
```

---

# 6. Spring Boot Layer Naming

尽量保持统一：

```text
XxxController
XxxService
XxxServiceImpl
XxxRepository
XxxMapper
XxxDTO
XxxVO
XxxEntity
XxxException
```

如果项目当前使用：

```text
Service + ServiceImpl
```

则保持这一体系。

如果当前并未使用 `ServiceImpl` 模式，不要为了形式统一强行引入。

统一应以：

> 当前项目主流架构 + Spring Boot 常见实践

为依据。

---

# 7. DTO / VO / Entity

检查以下常见问题：

```text
UserDto
UserDTO
userDTO
UserDataDTO
```

统一为：

```text
UserDTO
```

类似：

```text
UserVO
UserEntity
```

如果 Request / Response 模型已经存在，也可以保留：

```text
CreateUserRequest
UpdateUserRequest
UserResponse
```

不要为了统一名称而破坏已有清晰语义。

---

# 8. Frontend Naming Convention

SmartSE 前端为 Vue / Vite 项目。

优先遵循 Vue 社区常见实践。

---

# 9. Vue Components

Vue Component 文件统一使用 PascalCase：

```text
UserProfile.vue
ProjectList.vue
LoginForm.vue
NavigationBar.vue
```

避免同时存在：

```text
userProfile.vue
user-profile.vue
Userprofile.vue
user_profile.vue
```

对于组件目录：

```text
components/
views/
layouts/
```

保持整体一致。

---

# 10. TypeScript / JavaScript

变量和函数：

```text
camelCase
```

例如：

```ts
currentUser
projectList
fetchUserInfo()
createProject()
```

类型：

```text
PascalCase
```

例如：

```ts
User
Project
ApiResponse
UserProfile
```

常量：

```text
UPPER_SNAKE_CASE
```

或者如果项目已经大量使用：

```ts
const apiBaseUrl
```

则根据语义决定，不要机械转换所有 `const`。

只有真正的全局常量才需要：

```text
UPPER_SNAKE_CASE
```

---

# 11. Frontend Modules

API、utility、composable 等文件统一风格。

例如：

```text
api/
  user.ts
  project.ts

utils/
  request.ts
  formatDate.ts

composables/
  useAuth.ts
  useProject.ts
```

不要出现无意义名称：

```text
utils2.ts
common1.ts
test123.ts
aaa.ts
temp.ts
newFile.ts
```

如果发现此类文件，分析用途后重命名。

---

# 12. Rename Safety

任何文件、class、method 或 symbol 重命名时：

**必须同步更新所有引用。**

包括：

* Java import；
* Spring Bean 引用；
* package；
* Vue import；
* TypeScript import；
* router；
* store；
* API；
* test；
* configuration；
* reflection；
* serialization；
* JSON mapping；
* database mapping；
* component registration。

重命名后必须搜索旧名称：

```text
OldName
oldName
old_name
```

确认不存在遗漏引用。

特别注意大小写修改。

Windows 文件系统默认大小写不敏感。

例如：

```text
userProfile.vue
        ↓
UserProfile.vue
```

必要时使用临时名称完成：

```text
userProfile.vue
    ↓
__temp__.vue
    ↓
UserProfile.vue
```

避免 Git 无法正确识别大小写重命名。

---

# 13. Bug Detection

在规范化过程中，同时检查明显 Bug。

允许主动修复能够合理确认的问题，例如：

### Null / Undefined

```java
user.getName()
```

但 `user` 可能为空。

或者：

```ts
response.data.user.name
```

其中中间对象可能不存在。

---

### Boundary Conditions

例如：

* 空列表；
* 空字符串；
* null；
* undefined；
* 非法 ID；
* page < 0；
* size <= 0；
* 数组越界；
* 空查询结果。

---

### Exception Handling

检查：

```java
try {
    ...
} catch (Exception e) {
}
```

等吞异常行为。

避免：

* catch 后什么都不做；
* 返回错误的成功状态；
* 丢失关键异常信息；
* 向前端直接暴露 stack trace。

---

# 14. Backend Robustness

重点检查 Spring Boot 后端：

* Controller 参数校验；
* Service 空值处理；
* Repository 查询结果；
* Optional 使用；
* transaction；
* exception handling；
* resource closing；
* concurrency；
* authentication；
* authorization；
* SQL / ORM 查询；
* pagination；
* duplicate request；
* invalid request；
* HTTP status；
* API response。

对于明显问题可以修复。

---

# 15. Frontend Robustness

重点检查：

* API 请求失败；
* Promise rejection；
* loading 状态；
* undefined；
* null；
* 空数据；
* router 参数；
* localStorage；
* JSON parse；
* async/await；
* 生命周期；
* event listener；
* timer；
* component unmount；
* repeated requests。

避免：

```ts
try {
  ...
} catch (e) {
}
```

完全吞掉错误。

---

# 16. Async Code

检查异步代码是否存在：

* 未处理 Promise；
* 忘记 await；
* 不必要的串行 await；
* race condition；
* 重复请求；
* loading 状态无法恢复；
* exception 后状态未清理。

例如：

```ts
loading.value = true

try {
  await request()
} finally {
  loading.value = false
}
```

如果现有代码存在明显状态恢复问题，可以修复。

---

# 17. Security Issues

如果扫描过程中发现明显安全问题，可以修复。

重点关注：

* 明文密码；
* hard-coded token；
* API Key；
* SQL Injection；
* 未校验用户输入；
* JWT 校验错误；
* 权限绕过；
* 敏感信息日志；
* 前端保存不应保存的敏感信息；
* CORS 明显错误配置；
* 文件上传缺乏基本校验。

对于可能影响现有认证体系的重大安全修改，不要直接重构整个认证架构。

记录问题并在最终报告中说明。

---

# 18. Code Duplication

可以消除明显重复代码。

例如多个 Controller / Service 中存在完全相同的：

```text
parameter validation
response conversion
date conversion
error handling
```

可以提取公共方法。

但是：

> 不要为了 DRY 而过度抽象。

只有重复逻辑明显、语义一致时才提取。

---

# 19. Dead Code

识别：

* 未使用 import；
* 未使用变量；
* 不可达代码；
* 已确认没有引用的方法；
* 明显遗留的调试代码；
* console.log；
* System.out.println；
* 注释掉的大段旧代码。

可以安全删除：

```text
unused imports
unused local variables
obvious debug output
```

对于无法确认是否仍有业务意义的 class / API / component：

**不要删除。**

在最终报告中记录即可。

---

# 20. Comments

不要大量添加解释显而易见代码的注释。

例如不要：

```java
// 获取用户
User user = getUser();
```

注释应该解释：

> Why

而不是简单重复：

> What

可以清理：

* 失效注释；
* 与代码不一致的注释；
* 大段注释掉的历史代码；
* 无意义 TODO。

对于仍然有效的 TODO，应保留。

---

# 21. Logging

检查后端是否存在：

```java
System.out.println(...)
e.printStackTrace()
```

如果项目已经使用日志框架，统一使用现有 logger。

例如：

```java
log.info(...)
log.warn(...)
log.error(...)
```

不要在日志中输出：

* password；
* JWT；
* token；
* secret；
* private information。

---

# 22. Formatting

如果项目已经存在：

```text
ESLint
Prettier
Checkstyle
Spotless
EditorConfig
```

优先使用现有工具。

不要随意引入大量新的 formatter 或 lint dependency。

如果没有统一工具，本阶段优先完成代码本身的规范化，不要为了格式化引入复杂工程依赖。

---

# 23. Refactoring Permission

本次允许进行：

```text
Rename
Move
Extract Method
Simplify Condition
Remove Duplication
Improve Null Safety
Improve Exception Handling
Improve Async Handling
Fix Confirmed Bug
Remove Dead Local Code
Improve Type Safety
```

---

# 24. Refactoring Boundaries

未经必要性证明，不要：

* 重写整个模块；
* 改变数据库 Schema；
* 修改 API contract；
* 更换 ORM；
* 更换状态管理框架；
* 更换 UI framework；
* 重构整个 authentication system；
* 引入微服务；
* 引入新的复杂架构；
* 大规模升级 dependency；
* 改变业务规则；
* 删除无法确认用途的功能。

如果发现需要上述操作才能解决的问题：

**记录，而不是擅自实施。**

---

# 25. API Compatibility

尽可能保持现有 API contract。

包括：

```text
URL
HTTP Method
Request Body
Query Parameters
Response Structure
Status Code
```

如果发现 API 本身存在确定性 Bug，可以修复。

但必须在最终报告中明确记录：

```text
Before
After
Reason
Impact
```

---

# 26. Database Compatibility

默认：

> 不修改数据库 Schema。

除非存在极其明确且无需 migration 即可解决的问题。

不要擅自：

```text
DROP TABLE
DROP COLUMN
RENAME COLUMN
ALTER DATA TYPE
```

如果发现数据库设计问题，在最终报告中记录。

---

# 27. Incremental Workflow

不要一次性对整个项目进行大量修改后才测试。

按照以下方式执行：

```text
1. Scan
   ↓
2. Establish naming rules
   ↓
3. Backend naming cleanup
   ↓
4. Backend validation
   ↓
5. Frontend naming cleanup
   ↓
6. Frontend validation
   ↓
7. Robustness / bug fixes
   ↓
8. Full validation
```

每个阶段尽可能保持项目可构建。

---

# 28. Git Strategy

本次允许修改大量文件。

但修改应按照逻辑组织。

如果需要创建 commit，推荐：

```text
refactor: standardize backend naming

refactor: standardize frontend naming

fix: improve backend robustness

fix: improve frontend error handling

chore: clean obsolete code
```

不要把所有无关修改混进一个难以审查的 commit。

如果当前 Git 环境不适合自动 commit，可以仅修改文件并保留 working tree。

不要 push。

---

# 29. Backend Validation

根据项目实际情况执行：

```bash
cd backend
mvn test
```

或者：

```bash
./mvnw test
```

必要时：

```bash
mvn clean test
```

如果测试覆盖不足，至少执行：

```bash
mvn compile
```

确保：

```text
Compilation       PASS
Tests             PASS / Known Failure
Imports           PASS
Package Structure PASS
```

---

# 30. Frontend Validation

执行：

```bash
cd frontend
npm run build
```

如果存在：

```bash
npm run lint
npm run test
```

也执行相应检查。

确保至少：

```text
Type Check PASS
Build      PASS
Lint       PASS / Known Failure
Tests      PASS / Known Failure
```

---

# 31. Search After Refactoring

完成重命名后，对整个仓库再次搜索：

* 旧 class name；
* 旧 component name；
* 旧 filename；
* 旧 import；
* TODO；
* FIXME；
* console.log；
* System.out.println；
* printStackTrace；
* suspicious empty catch。

确认没有因为重构产生悬空引用。

---

# 32. Full Regression Validation

最终至少完成：

```text
Backend compile
Backend tests

Frontend build
Frontend lint/type check（如果项目支持）

Git status
```

如果项目存在现成的 integration test / E2E test，也应执行。

---

# 33. Existing Failures

如果在修改前或修改过程中发现项目原本就存在失败：

不要为了获得：

```text
PASS
```

而进行与本次任务无关的大规模修改。

应该区分：

```text
Pre-existing failure
Regression introduced by refactoring
Bug discovered during refactoring
```

**本次修改不得引入新的 regression。**

---

# 34. Final Report

完成后生成：

```text
docs/refactoring-report.md
```

报告保持简洁，但至少包含以下内容。

## Naming Standardization

记录最终采用的：

* Backend naming convention；
* Frontend naming convention；
* 文件命名规则；
* package 规则；
* component 规则。

## Major Renames

记录重要：

```text
Old Name -> New Name
```

无需记录每一个局部变量。

## Bugs Fixed

每个 Bug 使用：

```text
Problem:
Cause:
Fix:
Impact:
```

## Robustness Improvements

记录：

* null safety；
* exception handling；
* async handling；
* validation；
* logging；
* security；
* type safety。

## Issues Not Modified

对于风险较高或需要架构级修改的问题，记录：

```text
Issue
Location
Risk
Recommended future action
```

## Validation

最终给出：

```text
Backend Compile: PASS / FAIL
Backend Tests:   PASS / FAIL
Frontend Build:  PASS / FAIL
Frontend Tests:  PASS / FAIL / N/A
Lint:            PASS / FAIL / N/A
```

---

# 35. Final Goal

本阶段结束后的 SmartSE 应满足：

```text
Consistent Naming
        +
Consistent Code Style
        +
Improved Robustness
        +
Known Bugs Fixed
        +
No Unnecessary Architecture Changes
        +
No Regression
```

最终代码应该让新的开发者进入项目后，可以较容易理解：

```text
一个文件应该叫什么
一个类应该放在哪里
一个组件应该如何命名
一个 Service 应该如何组织
错误应该如何处理
API 应该遵循什么基本规范
```

本阶段完成后停止。

不要继续进行新的业务功能开发或大规模架构升级。
