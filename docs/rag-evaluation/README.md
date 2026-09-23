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
  --password "$SEFORGE_RAG_EVAL_PASSWORD" \
  --golden ./private/course-42-golden.jsonl \
  --output ./rag-course-42-results.jsonl
```

也可使用同名环境变量 `SEFORGE_RAG_EVAL_BASE_URL`、`SEFORGE_RAG_EVAL_COURSE_ID`、`SEFORGE_RAG_EVAL_IDENTIFIER`、`SEFORGE_RAG_EVAL_PASSWORD`、`SEFORGE_RAG_EVAL_GOLDEN` 和 `SEFORGE_RAG_EVAL_OUTPUT`。阈值可通过 `SEFORGE_RAG_EVAL_RECALL_AT_5`、`SEFORGE_RAG_EVAL_CITATION_HIT_RATE` 与 `SEFORGE_RAG_EVAL_REFUSAL_RATE` 上调；发布环境不应下调默认值。

结果文件保留每题的 Top-5 来源、命中来源、答案、拒答判定与 SSE 终态，应作为发布产物留存，但不得提交真实账号密码或课程私有内容。
