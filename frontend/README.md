# SEForge Web

Vue 3 + TypeScript + Pinia 前端。应用通过同源 `/api/v1` 访问后端，认证使用 HttpOnly Session Cookie 和 CSRF Token，不在浏览器存储用户令牌。

## 本地开发

```bash
npm ci
npm run dev
```

需要开发代理时，在本机环境文件中配置：

```dotenv
SEFORGE_API_PROXY=http://127.0.0.1:8080
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
