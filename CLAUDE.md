# SmartSE Repository Restructuring Instructions

## 1. Task Overview

当前 SmartSE 项目由 Spring Boot 后端与 Vue/Vite 前端组成。

本次任务的目标是对项目进行一次 **仓库级重构与目录重新编排**，将前后端统一整理到同一个 Git 仓库中，为后续 SmartSE 功能重构、Java/Spring Boot 学习以及 GitHub 项目展示做好准备。

本阶段重点是：

1. 重新整理项目目录；
2. 将前端、后端纳入统一仓库；
3. 清理旧 Git 环境；
4. 初始化全新的 Git 仓库；
5. 完善仓库级配置文件；
6. 保证整理后的前后端仍能够独立开发和运行。

**本阶段原则上不要修改业务逻辑。**

---

# 2. Target Repository Structure

请将项目整理为类似以下结构：

```text
SmartSE/
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── frontend/
│   ├── src/
│   ├── public/
│   ├── package.json
│   ├── vite.config.*
│   └── ...
│
├── docs/
│   └── ...
│
├── .gitignore
├── README.md
└── CLAUDE.md
```

如果当前项目中存在 Docker、Nginx、部署脚本等内容，可根据实际情况保留，例如：

```text
SmartSE/
├── backend/
├── frontend/
├── docs/
├── deploy/
├── scripts/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
└── CLAUDE.md
```

不要为了严格匹配上述示例而破坏当前项目已有的合理结构。

应首先检查现有文件，再决定最终目录组织方式。

---

# 3. Frontend

识别现有 Vue/Vite 前端项目，并统一移动到：

```text
frontend/
```

需要保留：

* `src/`
* `public/`
* `package.json`
* package lock file
* Vite 配置
* TypeScript/JavaScript 配置
* ESLint 等工程配置
* 前端运行所需的其他文件

不要保留：

* `node_modules/`
* `dist/`
* 临时构建文件
* IDE 缓存
* 明显无用的开发临时文件

移动后检查路径引用以及配置文件。

如果因为目录移动导致配置中的相对路径失效，可以进行必要的 **路径修复**，但不要借此修改业务逻辑。

---

# 4. Backend

识别现有 Spring Boot 后端项目，并统一移动到：

```text
backend/
```

需要保留：

* `src/main`
* `src/test`
* `pom.xml` 或 Gradle 配置
* Maven Wrapper / Gradle Wrapper（如果存在）
* application 配置
* 后端运行所需的其他资源

不要保留：

```text
target/
build/
*.class
```

以及其他可重新生成的构建产物。

如果目录移动导致配置文件中的相对路径失效，可以进行必要修复。

除此之外：

**不要修改 Java 业务逻辑。**

---

# 5. Git History Reset

本次重构需要 **完全清除旧 Git 信息**。

用户已经明确确认并授权执行此操作，无需再次询问。

删除当前项目及被合并进来的前后端项目中的所有旧 Git 元数据，包括可能存在的：

```text
.git/
```

尤其注意检查：

```text
SmartSE/.git
frontend/.git
backend/.git
```

以及其他子目录中可能存在的嵌套 Git 仓库。

目标是：

> SmartSE 最终只能存在一个新的根级 Git Repository。

旧 Git commit history、branch、remote、tag 等信息均不需要保留。

---

# 6. Initialize New Git Repository

完成目录整理并确认旧 `.git` 已清除后，在 SmartSE 根目录重新初始化 Git：

```bash
git init
```

默认分支统一设置为：

```text
main
```

例如：

```bash
git branch -M main
```

不要自动添加 GitHub remote。

不要执行：

```bash
git push
```

GitHub Remote 将由用户后续手动配置。

---

# 7. .gitignore

重新生成或整理根目录：

```text
.gitignore
```

至少覆盖以下内容。

## Java / Spring Boot

```gitignore
target/
*.class
*.jar
*.war
```

注意：如果项目存在需要版本控制的特殊 JAR，请根据实际情况判断，不要机械删除。

## Node / Vue / Vite

```gitignore
node_modules/
dist/
.vite/
```

## IDE

```gitignore
.idea/
.vscode/
*.iml
```

如果 `.vscode` 中存在值得共享的项目级配置，可以保留必要文件，而不是简单忽略整个目录。

## Environment

```gitignore
.env
.env.local
.env.*.local
```

必须避免提交：

* API Key
* Password
* Token
* Database Password
* JWT Secret
* 私有服务器信息
* 其他 Credentials

如果项目依赖环境变量，应创建：

```text
.env.example
```

其中只能保留变量名称和安全的示例值。

---

# 8. Sensitive Information Audit

在初始化新仓库后，对准备进入 Git 的文件执行一次敏感信息检查。

重点搜索：

```text
password
secret
token
api_key
apikey
access_key
private_key
Authorization
Bearer
```

以及常见数据库连接配置。

如果发现疑似真实凭据：

1. 不要将其提交到 Git；
2. 优先改为环境变量；
3. 在 `.env.example` 中提供安全示例；
4. 在最终报告中明确指出。

不要输出真实 Secret 的完整值。

---

# 9. README

如果已有 README：

保留有价值的信息，并根据新的 Monorepo 结构修正明显失效的路径说明。

如果 README 缺失，可以创建一个简洁的基础 README，但本阶段不需要进行大规模文档重写。

README 至少应能够说明：

```text
SmartSE
├── frontend    Vue/Vite frontend
└── backend     Spring Boot backend
```

并简单说明前后端如何启动。

---

# 10. Validation

目录整理完成后执行基本验证。

## Backend

根据项目实际构建工具执行，例如：

```bash
cd backend
mvn test
```

或者：

```bash
./mvnw test
```

至少确认：

* Maven/Gradle 能正确识别项目；
* Java 源代码路径正常；
* 编译配置没有因为目录移动而失效。

## Frontend

执行：

```bash
cd frontend
npm install
npm run build
```

如果依赖已经存在，也可以根据实际情况选择更合适的安装方式，例如：

```bash
npm ci
```

至少确认：

* package 配置正常；
* Vite 可以找到项目入口；
* 前端能够完成构建。

如果测试或构建本身存在旧项目遗留错误，不要为了让测试强行通过而大规模修改源代码。

应记录错误并报告。

---

# 11. Git Verification

完成后执行：

```bash
git status
```

检查仓库。

确保不存在：

```text
frontend/.git
backend/.git
```

等嵌套 Git Repository。

同时确认：

```bash
git remote -v
```

不存在旧 remote。

最终仓库应满足：

```text
SmartSE/
└── .git/
```

仅根目录存在一个 Git Repository。

---

# 12. Initial Commit

确认：

* 目录整理完成；
* 构建产物已排除；
* 敏感信息已处理；
* 前后端目录正确；
* 不存在旧 Git 元数据；

之后可以创建新的初始提交：

```bash
git add .
git commit -m "chore: initialize SmartSE monorepo"
```

如果当前 Git 环境缺少 `user.name` 或 `user.email`，导致 commit 无法执行：

**不要擅自修改用户全局 Git 配置。**

保留 staged changes，并在最终报告中说明即可。

---

# 13. Important Constraints

本次任务属于：

> Repository Restructuring / Monorepo Migration

而不是：

> Application Architecture Refactoring

因此除非为了修复目录迁移导致的问题，否则：

**不要：**

* 重写 Spring Boot Service；
* 修改 Controller 业务逻辑；
* 修改数据库模型；
* 重构 Vue 页面；
* 改写 API；
* 更换框架；
* 升级大量依赖；
* 修改数据库结构；
* 删除无法确认用途的源代码；
* 为“代码更漂亮”而进行无关重构。

对于用途无法确认的文件：

> 保留优先于删除。

---

# 14. Final Report

任务完成后输出简洁的重构报告，包括：

## Repository Structure

给出整理后的主要目录树。

## Changes

说明：

* 哪些目录被移动；
* 哪些生成文件被清理；
* `.gitignore` 做了哪些调整；
* 是否修改了路径相关配置；
* 是否发现敏感配置。

## Git

说明：

* 旧 Git 信息是否已完全清除；
* 新 Git Repository 是否初始化；
* 当前 branch；
* 是否创建 initial commit；
* 是否存在 remote。

## Validation

分别给出：

```text
Frontend: PASS / FAIL
Backend:  PASS / FAIL
Git:      PASS / FAIL
```

如果失败，给出具体错误和建议处理方式。

---

# 15. Final Goal

最终项目应该形成一个干净、可维护的 Monorepo：

```text
SmartSE
│
├── frontend     # Vue / Vite
├── backend      # Spring Boot
├── docs
│
├── .gitignore
├── README.md
└── CLAUDE.md
```

整个 SmartSE 由 **一个 Git Repository** 管理。

完成本次仓库整理后停止。

不要继续进行业务功能重构。

后续 Java/Spring Boot 架构重构、功能增强以及前端改造将在下一阶段单独进行。
