# SEForge RAG Golden Set 门禁

`run.mjs` 使用真实 Session/CSRF 登录，为每道题创建独立会话，通过课程问答 POST-SSE 收集引用和答案，并以非零退出码执行以下默认门禁：

- 每个 Golden Set 不少于 50 题，且同时含有依据题和无依据题；
- Recall@5 ≥ 0.80；
- 引用命中率 ≥ 0.90；
- 无依据问题拒答率 ≥ 0.90；
- 每路 SSE 必须以唯一 `done` 结束，任何 `error` 或缺失终态都使门禁失败。

Golden Set 采用 JSON Lines，每行字段如下：

```json
{"id":"q-001","question":"...","answerable":true,"expectedSources":["SRS.md"]}
```

`expectedSources` 使用课程中已上传文档的文件名（不含路径、不区分大小写）。复制 `golden-set.example.jsonl` 为每门样例课程编写至少 50 条真实题目；示例文件故意少于 50 条，不能被误当作发布数据。

```bash
node docs/rag-evaluation/run.mjs \
  --base-url https://seforge.example.edu \
  --course-id 42 \
  --identifier rag-evaluator \
  --portal TEACHER \
  --golden ./private/course-42-golden.jsonl \
  --output ./rag-course-42-results.jsonl
```

也可使用同名环境变量 `SEFORGE_RAG_EVAL_BASE_URL`、`SEFORGE_RAG_EVAL_COURSE_ID`、`SEFORGE_RAG_EVAL_IDENTIFIER`、`SEFORGE_RAG_EVAL_PASSWORD`、`SEFORGE_RAG_EVAL_GOLDEN` 和 `SEFORGE_RAG_EVAL_OUTPUT`。阈值可通过 `SEFORGE_RAG_EVAL_RECALL_AT_5`、`SEFORGE_RAG_EVAL_CITATION_HIT_RATE` 与 `SEFORGE_RAG_EVAL_REFUSAL_RATE` 上调；发布环境不应下调默认值。

密码使用环境变量 `SEFORGE_RAG_EVAL_PASSWORD`，避免暴露在命令行进程列表。`--portal` / `SEFORGE_RAG_EVAL_PORTAL` 默认为 `TEACHER`；学生用 `STUDENT` 和学号。登录后脚本会重新获取 CSRF Token。

结果文件保留每题的 Top-5 来源、命中来源、答案、拒答判定与 SSE 终态，应作为发布产物留存，但不得提交真实账号密码或课程私有内容。

## Phase 3 可控工程评测

`backend/src/test/resources/phase3/` 中提供 software-engineering、database-foundations 两门样例课程，各 10 份资料、50 题（40 有依据、10 无依据）。`PhaseThreeDocuments` 在运行时生成真实 PDF / PPT / PPTX / DOCX / Markdown / TXT 字节。

Java 21 + Docker 下，在 backend 执行：

```sh
mvn -Dapi.version=1.44 -Dtest=PhaseThreeRuntimeIntegrationTest test
```

测试使用独立 MySQL、MinIO、Milvus，经真实上传 Service、Job Handler、解析、AI Runtime HTTP Embedding、Milvus 检索、Course QA Streaming 和持久化链路评测，逐题产物为 `backend/target/phase3-golden-results.json`。同时通过真实 HTTP Session/CSRF 检查 owner/course 隔离、取消、连接断开、供应商中断及重试。

Embedding Stub 按公开的 20 个课程术语生成固定 32 维向量；聊天 Stub 给出带 `[C1]` 的固定答复。测试不从题目答案标签生成向量，但资料与题目是受控小样本，**只能证明工程链路、过滤、引用和拒答分支正确，不能证明自然语言语义质量或真实供应商 SLA**。专业课程的真实语义评测仍需教师独立编写、审阅 Golden Set 后运行上述 HTTP 脚本。

门禁机器不可用 Docker 时，Testcontainers 可能跳过；发布验收必须检查该测试实际执行，不能将跳过报告为真实集成通过。
