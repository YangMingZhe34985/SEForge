# Phase 6 机器可读工程证据

2026-09-25，隔离 Docker Desktop / Linux 容器，真实 MySQL、Redis、MinIO、Milvus、API、worker、TLS nginx，受控 HTTP AI Provider。

- `mixed-load-results.json`：200 认证会话、50 活跃 API 用户、20 流、5 重摄取任务；逐流/任务结果和服务端 Stub 实测并发。
- `fault-recovery-results.json`：worker 强杀、Redis 重启/transport 丢失、AI 503 与重试。
- `backup-restore-results.json`：SQL 摘要、MinIO 对象摘要、配置解密、新 Milvus 重建、RPO/RTO。RPO=0 仅限停写备份窗口。
- `stack-health.json` / `network-resources.json`：完整栈健康、资源限制及 worker internal 网络。
- `security-smoke.json` / `json-log-verification.json`：HTTPS/Secure Cookie/权限/安全头与日志检查。
- `health.json` / `metrics.json` / `openapi.json`：已认证 HTTP 200 冒烟结果，仅保留少量非敏感响应片段。

这些是已检查的机器输出副本，不包含真实凭据或完整备份。原始测试凭据、证书私钥、SQL/对象备份位于 Git 忽略的 `backend/target/phase6-release/`，不得整体提交或分享。真实供应商 TTFT 未验证。

最终测试数量及未验证边界见[开发过程记录书 Phase 6](../../development-history.md#phase-6-teacher-dashboard-与生产发布)。工程验证不替代用户验收，不表示已部署至实际生产域名。
