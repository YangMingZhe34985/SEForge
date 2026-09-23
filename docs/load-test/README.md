# SEForge 混合负载门禁

`mixed-load.js` 使用标准 k6 HTTP 能力执行一个有状态的发布门禁。默认并发由 125 个空闲在线会话、50 个活跃 API 用户、20 路课程问答 SSE 和 5 个异步任务组成，峰值合计 200 个已认证会话。

脚本会校验真实 HTTP 状态、`ApiEnvelope.code`、SSE 类型/增量/引用/唯一终态以及后台任务最终状态。账号、课程或 worker 配置错误会使测试失败，不会把 `401`、`403`、错误终态或超时记作成功。

## 前置数据

请只在可丢弃的测试环境运行；脚本会创建会话、消息和统计快照。

- 准备一个课程及可检索的知识文档，使测试问题至少命中一个 Citation。
- 准备学生测试账号并加入该课程。单账号可用于小规模冒烟；正式门禁建议创建 `loadtest-1` 至 `loadtest-200` 的同密码账号池。
- 准备一个属于该课程的教师/助教账号，用于提交 5 个统计快照任务。
- 启动 API 与 worker，并将 DeepSeek/DashScope 兼容地址指向可访问的聊天与 Embedding Stub Provider；Stub 必须返回正常的流式答案和查询向量。负载测试不应调用计费的生产模型。
- 若通过容器内的明文 HTTP 运行，测试环境需设置 `SESSION_COOKIE_SECURE=false`；正式 HTTPS 环境保持 `true`。

必须提供以下环境变量：

| 变量 | 说明 |
| --- | --- |
| `SEFORGE_LOAD_TEST_COURSE_ID` | 已准备课程的数字 ID |
| `SEFORGE_LOAD_TEST_PASSWORD` | 学生测试账号或账号池的密码 |
| `SEFORGE_LOAD_TEST_IDENTIFIER` | 单一测试账号；与账号池前缀二选一 |
| `SEFORGE_LOAD_TEST_USER_PREFIX` | 账号池前缀，例如 `loadtest-` |
| `SEFORGE_LOAD_TEST_USER_COUNT` | 账号池数量，默认 `200` |
| `SEFORGE_LOAD_TEST_TEACHER_IDENTIFIER` | 教师/助教测试账号 |
| `SEFORGE_LOAD_TEST_TEACHER_PASSWORD` | 教师/助教测试密码 |

常用可调参数：

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `SEFORGE_LOAD_TEST_BASE_URL` | `http://localhost:8080` | SEForge 同源入口；Compose profile 会改为 `http://frontend:8080` |
| `SEFORGE_LOAD_TEST_IDLE_VUS` | `125` | 空闲在线会话 |
| `SEFORGE_LOAD_TEST_API_VUS` | `50` | 活跃传统 API 用户 |
| `SEFORGE_LOAD_TEST_SSE_VUS` | `20` | 并发课程问答流 |
| `SEFORGE_LOAD_TEST_JOB_VUS` | `5` | 并发后台任务 |
| `SEFORGE_LOAD_TEST_DURATION` | `2m` | 主负载保持时间 |
| `SEFORGE_LOAD_TEST_SSE_TIMEOUT` | `2m` | 单路 SSE 最长时间 |
| `SEFORGE_LOAD_TEST_SSE_MAX_DURATION` | `3m` | 包含登录、建会话和 SSE 的场景上限 |
| `SEFORGE_LOAD_TEST_JOB_TIMEOUT_SECONDS` | `120` | 后台任务完成期限 |
| `SEFORGE_LOAD_TEST_SSE_QUESTION` | 内置课程问题 | SSE 提问内容 |
| `SEFORGE_LOAD_TEST_REQUIRE_SSE_CITATION` | `true` | 强制每路问答至少包含一个 Citation |
| `SEFORGE_LOAD_TEST_JOB_PATH` | 统计快照接口 | 可替换为返回 `id`/`jobId`/`asyncJobId` 的异步任务接口；支持 `{courseId}`、`{idempotencyKey}` |
| `SEFORGE_LOAD_TEST_JOB_BODY` | `{}` | 自定义任务 JSON；支持相同占位符 |

## 运行

仓库 Compose 已提供固定版本的 `load-test` profile。先在根目录 `.env` 中填写上述测试变量，然后运行：

```bash
docker compose --env-file .env -f backend/docker/docker-compose.yml up -d
docker compose --env-file .env -f backend/docker/docker-compose.yml --profile load-test run --rm k6
```

也可使用本机 k6：

```bash
k6 run --summary-export=load-test-summary.json docs/load-test/mixed-load.js
```

脚本默认门禁为：传统 API 错误率低于 1%、P95 小于 500ms；认证、SSE 终态和后台任务失败率均低于 1%；所有显式检查通过率高于 99%。5 个后台任务都必须到达 `COMPLETED`。

标准 k6 HTTP 客户端会在有限 SSE 响应结束后返回完整响应，因此该脚本能验证 20 路并发、事件结构和唯一终态，但不会伪造 TTFT 指标。真实 AI TTFT P95 应从服务端 Micrometer 指标或专用流式采集器计算。
