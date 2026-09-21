# SmartSE

智能软件工程辅导系统 —— 前后端一体化 Monorepo。

```text
SmartSE
├── frontend/        # Vue 3 / Vite 前端
├── backend/         # Spring Boot 3 后端（Java 17）
├── docs/            # 项目文档
├── .env.example     # 环境变量示例（复制为 .env 后填写真实值）
├── .gitignore
├── README.md
└── CLAUDE.md
```

## 环境要求

| 组件 | 要求 |
| ---- | ---- |
| 前端 | Node.js 18+、npm |
| 后端 | JDK 17+（构建可直接使用自带的 `mvnw` Wrapper，无需单独安装 Maven） |
| 依赖服务 | MySQL 8、Redis、Elasticsearch、Neo4j、Milvus（可用 `backend/docker/docker-compose.yml` 一键启动） |

## 配置环境变量

```bash
cp .env.example .env   # 然后填入真实值
```

后端通过操作系统环境变量读取 `DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`、`MYSQL_PASSWORD` 等配置（见 `backend/src/main/resources/application.yml` 中的 `${VAR:default}` 占位符），可在 IDE 运行配置或 shell 中注入。`.env` 不会被提交。

## 启动后端

```bash
# 1. 首次运行：启动依赖的中间件服务
docker compose -f backend/docker/docker-compose.yml up -d

# 2. 启动 Spring Boot（默认端口 8080）
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

## 启动前端

```bash
cd frontend
npm install
npm run dev                   # 默认 http://localhost:5173
```

开发服务器已将 `/api` 请求代理到 `http://localhost:8080`（见 `frontend/vite.config.js`）。

## 构建

```bash
# 前端产物 → frontend/dist
cd frontend && npm run build

# 后端产物 → backend/target/*.jar
cd backend && ./mvnw package
```

## 大文件 / 运行时数据说明

以下目录为运行时数据或大体积模型文件，**已被 `.gitignore` 忽略**，新环境部署时需自行准备（部分可由程序运行时自动下载）：

- `backend/nlp-models/` —— Stanford NLP 中文模型（约 2 GB）
- `backend/models/` —— Whisper / HuggingFace 模型缓存
- `backend/temp/` —— 视频、PDF、下载等临时文件
- `backend/docker/volumes/` —— docker-compose 服务数据卷

## 子项目文档

- 后端详细说明：[backend/README.md](backend/README.md)
- 检索增强与知识库功能说明：[docs/智能软件工程辅导系统-检索增强与知识库功能说明.md](docs/智能软件工程辅导系统-检索增强与知识库功能说明.md)
