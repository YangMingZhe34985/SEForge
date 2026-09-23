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

路由页面使用动态导入。学生、教师与管理员共享一个 App Shell，导航显示和路由守卫都按服务端返回的账号能力控制；服务端仍是最终授权边界。
