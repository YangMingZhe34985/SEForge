# SEForge Backend

Spring Boot API 与异步 worker 共用本模块和容器镜像，通过 Spring Profile 区分进程职责。

## 构建与测试

需要 JDK 21。仓库自带 Maven Wrapper：

```bash
./mvnw clean verify                 # Windows: mvnw.cmd clean verify
./mvnw spring-boot:run
```

运行时约定：

- `dev`：本地开发，允许 AI/外部基础设施不可用。
- `test`：自动化测试，使用测试替身或 Testcontainers，不访问云模型。
- `prod`：API 的容器配置，Flyway 是唯一 Schema 管理入口。
- `worker`：容器使用 `prod,worker`；宿主机使用 `dev,worker`，默认启用任务发布/消费并监听 8082。

API 监听 8080，健康检查为 `GET /actuator/health`。容器入口不探测云模型；Provider 未配置时应报告降级状态，而不是阻止核心服务启动。

宿主机先按根目录 README 启动 MySQL/Redis/MinIO/etcd/Milvus，再分别运行 API 和 worker：

```powershell
.\mvnw.cmd spring-boot:run
# 在另一个 backend 终端中：
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,worker'
```

两者从 `../.env` 读取同一组 `MYSQL_APP_*`、`REDIS_PASSWORD`、`MINIO_APP_*`。
默认宿主机端口依次为 MySQL 3307、Redis 6380、MinIO 9000、Milvus 19530；Compose 通过服务名访问内部端口。
文档/RAG 还需要 `DASHSCOPE_API_KEY` 与向量开关；只启动 API 会让摄取任务等待 worker。
Code Review 应使用 Compose 的网络隔离 worker，并配置真实 SonarQube 与 Token。

## 容器运行

发布 Compose 位于 `docker/docker-compose.yml`。从仓库根目录运行：

```bash
cp .env.example .env
docker compose --env-file .env -f backend/docker/docker-compose.yml config --quiet
docker compose --env-file .env -f backend/docker/docker-compose.yml up -d --build
```

默认启动 frontend、api、worker、MySQL、Redis、MinIO、etcd 和 Milvus。SonarQube 与专用 PostgreSQL 仅在 `--profile review` 下启动。

关键配置全部由环境变量注入：

| 范围 | 变量 |
| --- | --- |
| MySQL | `MYSQL_DATABASE`、`MYSQL_APP_USERNAME`、`MYSQL_APP_PASSWORD`、`MYSQL_ROOT_PASSWORD` |
| Redis | `REDIS_PASSWORD` |
| MinIO | `MINIO_ROOT_*`、`MINIO_APP_*` |
| Milvus | `SEFORGE_VECTOR_STORE_ENABLED`（优先）、`MILVUS_ENABLED`（兼容）、`MILVUS_HOST_PORT` |
| AI | `SEFORGE_AI_ENABLED`、`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY` |
| SonarQube 服务 | `SONAR_POSTGRES_*`、`SONAR_HTTP_PORT` |
| 代码扫描 Worker | `SEFORGE_SONAR_ENABLED`、`SEFORGE_SONAR_SERVER_URL`、`SEFORGE_SONAR_TOKEN`、`SEFORGE_SONAR_SCANNER_EXECUTABLE` |

Compose 将这些值映射为标准 `SPRING_*` 和 `SEFORGE_*` 配置。数据库、中间件和模型凭据不写入镜像或 Compose 文件。

API 侧 DeepSeek/DashScope 的 Base URL 与模型名可通过 `.env.example` 中的变量覆盖。worker
默认经固定 allow-list 代理出站；发布门禁若使用 Stub Provider，可用
`SEFORGE_WORKER_DEEPSEEK_BASE_URL` / `SEFORGE_WORKER_DASHSCOPE_BASE_URL` 指向已加入 internal
data 网络的 Stub 服务。不要让负载测试调用计费的生产模型。

全新数据库可通过 `SEFORGE_BOOTSTRAP_ADMIN_EMAIL` 与 `SEFORGE_BOOTSTRAP_ADMIN_PASSWORD` 一次性创建管理员；密码留空即禁用。创建成功后清空这两个值并重建 API 容器，后续账号由管理员界面维护。

## 运维

```bash
# 状态和资源
docker compose --env-file .env -f backend/docker/docker-compose.yml ps
docker stats

# 聚合日志
docker compose --env-file .env -f backend/docker/docker-compose.yml logs -f --tail=200 api worker

# 重启单个无状态进程
docker compose --env-file .env -f backend/docker/docker-compose.yml restart api

# 在容器网络内检查 API
docker compose --env-file .env -f backend/docker/docker-compose.yml exec api \
  wget -qO- http://127.0.0.1:8080/actuator/health
```

持久数据位于 Compose named volumes。升级前应分别备份 MySQL、MinIO 和 Milvus/etcd；仅备份某一个 volume 不能构成一致的向量数据快照。`docker compose down` 保留数据，`down --volumes` 会不可恢复地删除本栈数据。

生产建议由宿主机 TLS 反向代理转发到 `SEFORGE_HTTP_PORT`，将 `SEFORGE_BIND_ADDRESS` 限制为代理可访问的地址，并把 `SESSION_COOKIE_SECURE` 设为 `true`。轮换已持久化的 MySQL、MinIO 或 SonarQube 凭据时，应先按对应服务流程更新账号，再同步 `.env`；仅修改文件不会重置已有账号。SonarQube 启动前还需按其官方要求配置宿主机 `vm.max_map_count`。

## SonarQube 代码评审

`POST /api/v1/courses/{courseId}/reviews/code` 只接受服务端已经绑定到该 submission 的
ZIP `attachmentObjectKey`，不接受客户端提供的 Sonar findings。异步 worker 会重新读取并校验
对象、解压到一次性目录、调用 SonarScanner、等待 Compute Engine 终态，然后从 SonarQube API
读取 issues、度量和 Quality Gate。Scanner 非零退出、超时、CE `FAILED/CANCELED`、API 异常或
畸形响应都会使任务失败并进入统一重试/死信流程，不会生成成功报告。

Compose 的 worker target 基于固定版本的官方 SonarScanner CLI 镜像构建；API target 保持精简。
应用不会在启动或任务执行时下载 Scanner。`SEFORGE_SONAR_SCANNER_EXECUTABLE` 可覆盖默认
`sonar-scanner`，Token 仅通过子进程环境和 Bearer API 认证传递，不写入命令行。若启用扫描但
Scanner 或 Token 不可用，Review 会明确失败；缺失配置分别返回 `SONAR_SCANNER_MISSING`、
`SONAR_TOKEN_MISSING`，其他扫描失败返回 `SONAR_FAILED`，不会生成假成功报告。

```text
SEFORGE_SONAR_ENABLED=true
SEFORGE_SONAR_SERVER_URL=http://sonarqube:9000
SEFORGE_SONAR_TOKEN=<project-analysis-token>
SEFORGE_SONAR_SCANNER_EXECUTABLE=sonar-scanner
```

扫描进程不会调用 shell、Maven、Gradle、npm 或学生代码，并忽略学生提供的
`sonar-project.properties`。ZIP 限制为 50 MB 压缩体积、200 MB 展开体积、2,000 个条目，
同时检查 MIME、签名、单文件大小、路径穿越、保留路径、嵌套压缩包和压缩比。Compose 中
worker 只连接 `internal` data 网络，不能直接访问公网；模型调用只能经过 `ai-egress` 的固定
DeepSeek/DashScope 反向代理路径。Scanner 子进程的环境会移除模型 URL 与凭据，因此扫描阶段
只能访问 data 网络内的 SonarQube，不能借用模型出口。若修改网络拓扑，必须保留这条隔离边界。

`load-test` profile 使用固定版本的 k6 镜像执行混合负载门禁，运行前必须准备独立测试账号、课程、知识文档和 Stub AI。参数及运行方法见 [混合负载说明](../docs/load-test/README.md)。

完整的发布前检查、备份、恢复演练和故障响应流程见 [运维手册](../docs/operations.md)。
