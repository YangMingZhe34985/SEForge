# SEForge 单机发布与恢复手册

本手册面向 `backend/docker/docker-compose.yml` 的单机生产拓扑。命令默认从仓库根目录执行，并使用未纳入版本控制的 `.env`。

## 发布前检查

Phase 6 正式 TLS 覆盖：准备 CA 签发的 `cert.pem`/`key.pem`，通过
`node docs/release/render-tls.mjs <受限目录>/nginx-tls.conf` 从当前前端配置生成 TLS 配置，
设置 `SEFORGE_TLS_DIR`、`SEFORGE_TLS_CONFIG` 的绝对路径及 `SEFORGE_HTTPS_PORT=443`。
生产命令在基础文件后追加 `-f backend/docker/compose.production.yml`；该覆盖移除 MySQL/Redis/Sonar 宿主端口。
不要在生产使用 `compose.release-test.yml`（包含 Stub Provider）或演练自签名证书。
`SESSION_COOKIE_SECURE=true` 必须保留。`/actuator/prometheus` 要求管理员会话，不对学生或匿名开放。

V8/V9 为 Phase 6 新前向迁移，不能修改旧 checksum。MySQL 需要允许 schema 迁移账号创建事务触发器；
Compose 显式设置 `log_bin_trust_function_creators=1`，不向应用授予 SUPER。
禁止不受信任用户持有 schema DDL 权限。升级前备份，先更新数据库启动参数，再启动迁移应用。
生产 JSON 日志不保存自由文本、参数/异常正文或任意 MDC，使用事件码、异常类型、traceId/requestId 与授权审计关联。

Analytics 普通活动按贡献差值更新；结构变化重查依赖数据族。`analytics_change.applied_revision`
关联快照的 `delta-<revision>` cursor；不要删未应用事件或手动改累计值。
归档已应用日志前保存快照/贡献/累计值一致备份。恢复无需供应商回答的语义确定性，但索引重建必须使用兼容 embeddingVersion。

1. 将 `.env.example` 复制为 `.env`，替换所有 `change-me` 值，并将 `SEFORGE_IMAGE_TAG` 设为不可变的 Git SHA 或发布号。
2. 验证配置：

   ```bash
   docker compose --env-file .env -f backend/docker/docker-compose.yml -f backend/docker/compose.production.yml --profile review config --quiet
   ```

3. 依次执行 `backend/mvnw clean verify`、前端 lint/typecheck/test/build 和 Playwright。
4. 确认 TLS 反向代理已就绪，`SESSION_COOKIE_SECURE=true`，对外只放行 443/所需 SSH 管理端口；
   `SEFORGE_ALLOWED_ORIGINS` 必须设置为实际公共 Origin（协议 + 主机 + 端口，逗号分隔可多个），
   否则跨域浏览器调用会被 CORS 拒绝（同源流量经自带 nginx 自动豁免）。
5. 启用 SonarQube 时先配置宿主机 `vm.max_map_count`，并创建仅允许分析的 Token。
6. 核对 `SEFORGE_VECTOR_ACTIVE_VERSION`（查询蓝索引）和 `SEFORGE_VECTOR_WRITE_VERSION`
   （摄取绿索引）；切换前先完成全量重索引及向量对账，再原子更新 active 版本并重启 API/worker。
7. 对每门发布样例课程执行 `node docs/rag-evaluation/run.mjs ...`，确认题量不少于 50，
   Recall@5 ≥ 0.80、引用命中率 ≥ 0.90、无依据问题拒答率 ≥ 0.90。

## 健康、指标与日志

- 存活/就绪：`GET /actuator/health/liveness` 和 `GET /actuator/health/readiness`。
- Prometheus：`GET /actuator/prometheus`，只应暴露给受信网络。
- OpenAPI：`/api-docs`；交互文档：`/docs`。
- 日志应使用 `traceId`/`requestId` 联系 API、SSE、AI Trace 和异步任务，不记录 Cookie、API Key、完整 Prompt 或学生源码。

## 一致性备份

建议先停止新写入，保持数据服务运行：

```bash
docker compose --env-file .env -f backend/docker/docker-compose.yml -f backend/docker/compose.production.yml stop frontend api worker
```

在受限权限的备份主机上保存带时间戳的以下内容：

- MySQL 逻辑备份：使用 `mysqldump --single-transaction --routines --events --hex-blob seforge`。
  Phase 6 含变更触发器：推荐分别导出 schema（`--no-data --skip-triggers`）、data（`--no-create-info --skip-triggers`）和 triggers（`--no-data --no-create-info --triggers`），
  按此顺序恢复，避免恢复数据本身产生额外 Analytics 事件。按权限加 `--no-tablespaces --set-gtid-purged=OFF`。
- MinIO：使用 `mc mirror --overwrite` 镜像整个 SEForge bucket，并保存 bucket policy。
- SonarQube（启用时）：备份其 PostgreSQL 数据库与自定义扩展。
- `.env`/密钥由独立的秘密管理系统备份，不应与数据归档明文存放。

Milvus 向量可由 MySQL 中的文档/切片元数据和 MinIO 原文档重建。单机版默认以这两者为权威数据源；如需缩短 RTO，再按 Milvus Backup 的相同版本流程保存向量快照。Redis 丢失只会使会话失效；MySQL Outbox 与 worker 恢复会重发未完成任务。

备份完成后恢复服务：

```bash
docker compose --env-file .env -f backend/docker/docker-compose.yml -f backend/docker/compose.production.yml start api worker frontend
```

## 灾难恢复演练

可复现工程演练见 `docs/release/verify.mjs backup`，只用于其隔离项目；记录 MySQL 全表数据摘要、MinIO 对象 SHA-256、配置解密、重新索引与真实 Course QA Citation。
备份会暂停隔离栈写入；RPO=0 只适用于这个停写窗口，不代表在线业务的持续 RPO。
生产配置备份密钥必须放独立 vault，不能照搬演练目录里的测试恢复密钥布局。

1. 在隔离主机上用相同镜像版本创建空卷。
2. 先恢复 MySQL 和 MinIO，然后启动 Redis、etcd/Milvus，最后启动 API/worker。Flyway 只允许向前迁移，不得在恢复环境使用 `flyway clean`。
3. 通过管理员 API 对所有有效课程文档发起重建索引，等待摄取任务全部进入 `COMPLETED`。
4. 执行向量/数据库对账，再用一门样例课程的 RAG Golden Set 验证引用命中率。
5. 验证账号登录、跨课程 403/404、文档下载、作业提交、成绩可见性与唯一终态 SSE。
6. 记录 RPO/RTO、数据差异和手工步骤；未通过恢复演练不得发布。

## 故障与安全响应

- 模型、Milvus、MinIO 或 SonarQube 故障时，对应 AI/任务保持可重试失败态；不得手工改为 `COMPLETED`。
- Redis 故障后先确认 MySQL `async_job`/`outbox_event` 状态，再恢复 Redis 与 worker；不要直接重复提交业务请求。
- Worker 会按租约周期续租；恢复任务只接管过期租约，旧 Worker 的完成/失败写入会因所有权不匹配被丢弃。
- 凭据泄露时立即撤销供应商/MinIO/Sonar Token、轮换 `.env` 并强制会话失效，随后用 Audit Log 和 AI Trace 界定影响范围。
- 可疑 ZIP/文档只能作为数据解析；不得在 API/worker 容器内执行学生代码。
- worker 必须保持仅连接 internal data 网络；`ai-egress` 只允许固定的 DeepSeek/DashScope
  上游路径。不得把 worker 重新接入 edge 网络，否则 SonarScanner 会重新获得直接公网出口。
