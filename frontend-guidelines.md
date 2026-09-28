# SEForge Frontend Engineering Guidelines

> 适用范围：SEForge 前端重构、功能开发与 UI 优化。  
> 技术基线：Vue 3 + TypeScript + Composition API + Pinia + Element Plus。

## 1. 总体原则

1. **业务正确性优先于视觉还原**：不得为了匹配设计稿破坏现有路由、权限、API 契约或业务流程。
2. **共享优先于复制**：相同视觉或交互优先抽取共享组件、Composable 或样式规范，不复制三套 Admin / Teacher / Student 实现。
3. **前端只做体验隔离**：权限最终以服务端为准，禁止依赖前端角色状态完成真正授权。
4. **明确状态**：所有异步页面至少考虑 `loading / empty / success / error / permission / retry`。

## 2. 代码规范

- 新增代码统一使用 **TypeScript + Composition API**。
- Vue 组件：`PascalCase`；变量/函数：`camelCase`；常量：`UPPER_SNAKE_CASE`。
- 禁止滥用 `any`、`data1`、`temp`、`foo` 等无语义命名。
- 页面组件负责布局与业务编排；领域组件负责单一业务块；基础组件负责通用视觉与交互。
- 避免超大 `.vue` 文件；复杂页面应拆分为可测试的子组件和 composable。
- 页面禁止直接 `fetch/axios`，统一通过 `src/api` 中的 API Client。
- API DTO、Props、Emits、Store 状态必须有明确类型。

## 3. 目录与职责

```text
src/
├── views/          页面级组件
├── components/     共享组件 / 领域组件
├── api/            API Client 与 DTO
├── stores/         跨页面状态
├── composables/    可复用逻辑
├── router/         路由与前端守卫
├── types/          公共类型
└── styles/         Design Tokens / 全局样式
```

- Pinia 仅保存跨页面共享状态；局部状态保留在页面或组件内。
- 路由参数（如 `courseId`）是页面上下文事实来源，Store 仅作缓存。

## 4. Design System

统一维护 Design Tokens，禁止页面随意硬编码：

- Color：品牌色、中性色、成功/警告/错误/信息色
- Typography：标题、正文、辅助文本层级
- Spacing：建议以 8px 节奏扩展
- Radius / Border / Shadow / Z-index
- Focus / Disabled / Hover 状态

优先复用或建设：

`WorkspaceShell`、`PageHeader`、`Toolbar`、`StatCard`、`DataTable`、`FormDialog`、`EmptyState`、`ErrorState`、`LoadingState`、`StatusBadge`、`MarkdownRenderer`。

## 5. 表单与交互

- 所有字段必须有明确 Label；必填项、说明、校验错误要可见。
- 前端校验规则与后端保持一致，但不能替代后端校验。
- 提交操作必须处理 `loading / disabled / duplicate submit`。
- 破坏性操作必须二次确认。
- 错误优先展示字段级信息；服务端错误尽量保留 `code / message / traceId`。
- 不使用颜色作为唯一状态表达。

## 6. 响应式与可访问性

- 至少保证常见桌面宽度与较窄窗口下可用。
- 禁止固定宽度导致文字截断、表单挤压或弹窗溢出。
- 关键操作支持键盘导航，Focus 状态清晰。
- 图标按钮必须具备可理解文本或 `aria-label`。
- 图片提供必要替代文本。

## 7. 样式规范

- 优先 CSS Variables / Design Tokens / scoped class。
- 禁止大面积 inline style。
- 谨慎使用 `!important` 和深层选择器。
- 不允许通过全局 CSS 强行覆盖局部组件行为。
- Element Plus 主题修改集中管理，不在页面零散覆盖。

## 8. 富文本与安全

- Markdown 统一使用共享安全渲染组件。
- 禁止直接渲染未经处理的 `v-html`。
- 必须过滤 XSS、`javascript:` URL、事件属性和不可信 HTML。
- 不在 LocalStorage / SessionStorage 保存密码、Session、API Key 等敏感凭据。
- 上传文件类型、大小和权限必须由后端再次验证。

## 9. 性能

- 页面路由按需加载。
- 大型表格必须分页或虚拟化，不一次加载全部数据。
- 避免无意义重复请求和深度响应式大对象。
- 图片合理压缩、懒加载。
- 不因 UI 重构明显增加 bundle 体积。

## 10. 测试与质量门禁

重构完成至少通过：

```bash
npm run lint
npm run typecheck
npm test
npm run build
npm run test:e2e
```

必须维护：
- API / Store / Composable 单元测试
- 关键共享组件测试
- Admin / Teacher / Student 核心 Playwright 流程
- 权限、错误状态、表单校验、响应式关键场景

## 11. 禁止项

- 禁止复制三套相似工作台组件。
- 禁止页面硬编码 API URL。
- 禁止通过 `any`、关闭类型检查或跳过测试快速过关。
- 禁止用前端隐藏按钮代替服务端权限。
- 禁止为了视觉效果删除现有业务能力。
- 禁止未验证就重写共享 API Client、Router、Auth Store。
- 禁止只“看起来正常”却不跑现有门禁。

## 12. 前端重构执行顺序

```text
Design Tokens
→ Shared Components
→ WorkspaceShell / Layout
→ 页面级重构
→ 响应式修正
→ 交互状态补齐
→ 测试与视觉回归
```

管理员端完成并稳定后，教师端与学生端必须继续复用同一套 Design System。
