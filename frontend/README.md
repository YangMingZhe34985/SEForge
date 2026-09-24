# SEForge Web

Vue 3 + TypeScript + Pinia 前端。应用通过同源 `/api/v1` 访问后端，认证使用 HttpOnly Session Cookie 和 CSRF Token，不在浏览器存储用户令牌。

## 本地开发

```bash
npm ci
npm run dev
```

开发服务器默认启用 API 代理（见已提交的 `.env.development`，目标 `http://127.0.0.1:8080`）。
先在 `backend/` 目录执行 `./mvnw spring-boot:run` 启动本地后端，再 `npm run dev`。

如需修改代理目标（例如指向 Docker 栈 `http://127.0.0.1:8081`）或禁用代理，请新建
`.env.development.local`（已被 gitignore，优先级高于 `.env.development`；注意 `.env.local`
不会覆盖 `.env.development`）：

```dotenv
SEFORGE_API_PROXY=http://127.0.0.1:8081
# 置空即禁用代理：SEFORGE_API_PROXY=
```

生产环境默认由反向代理提供同源 `/api/v1`，也可通过 `VITE_API_BASE_URL` 在构建时覆盖。

## 质量门禁

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

路由页面使用动态导入。前端按角色划分为三个工作台：`/admin`（管理控制台：概览、用户与学期、审计日志）、`/teacher`（教学工作台：课程创建、课程内容、教学班/邀请码/成员、评审与 Dashboard）、`/student`（学习工作台：加入课程、课程内容、助手、作业、成绩）。三个工作台共享同一个 Shell 组件、API Client 与认证体系，登录后按 `roles / accountType` 自动进入对应工作台；旧版扁平 URL（`/courses` 等）自动重定向。导航显示和路由守卫按服务端返回的账号能力控制，服务端仍是最终授权边界。
