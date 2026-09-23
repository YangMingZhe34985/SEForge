# SEForge 项目重构升级说明书

> 原项目：SmartSE —— 基于大语言模型的课程智能助手
>  升级项目：**SEForge —— AI 软件工程学习与实践平台**
>  核心技术方向：Spring Boot + Vue 3 + LangChain4j + RAG + Agent + Software Engineering Education

------

## 1. 项目背景

SmartSE 原项目面向《软件工程》课程教学场景，实现了基于大语言模型的多轮问答、知识库检索、多智能体协同以及代码质量分析等能力。

原项目已经能够验证 LLM 在教学场景中的基本可行性，但整体仍偏向于课程设计性质，主要存在以下不足：

- AI 功能偏 Demo 化，多 Agent 缺少稳定、明确的业务边界；
- 系统核心仍以“聊天问答”为中心，缺乏完整教学业务流程；
- 学生、教师、课程、作业等领域模型较弱；
- AI 能力之间缺乏统一编排机制；
- 缺乏完整的作业辅导、自动评审和学习反馈闭环；
- 工程稳定性、异常恢复、日志、测试等基础设施不足；
- AI 能力与传统业务代码耦合较高；
- 缺乏面向约 200 人规模课程实际使用的设计。

因此，本轮升级将 SmartSE 从：

> **“基于 LLM 的课程问答系统”**

升级为：

> **“面向高校《软件工程》课程的 AI-native 学习与实践平台”。**

系统目标是能够支撑一个专业约 200 名学生完成：

```
课程学习
   ↓
知识问答
   ↓
作业辅导
   ↓
作业提交
   ↓
自动评审
   ↓
教师批改
   ↓
学习分析
   ↓
个性化反馈
```

形成完整教学闭环。

------

# 2. 当前项目状态

本轮升级开始前，已经完成以下基础重构：

- 项目前后端统一纳入 **Monorepo** 管理；
- 清理旧项目中无用目录与历史开发文件；
- 重构项目目录结构；
- 保留原有核心业务能力；
- 暂未进行大规模业务逻辑修改；
- 为后续模块化开发预留目录与扩展空间；
- 原有 Spring Boot + Vue 体系继续保留。

因此，后续开发重点不再是项目结构调整，而是：

> **业务领域重构 + LangChain4j AI 架构重构 + 新功能开发 + 工程稳定性建设。**

------

# 3. 总体技术架构

升级后的 SEForge 建议采用以下整体架构：

```
┌────────────────────────────────────────────┐
│                 Vue 3 Web                  │
│                                            │
│ 学习端 / 教师端 / 作业系统 / AI助手 / Dashboard │
└────────────────────┬───────────────────────┘
                     │ REST / SSE
                     ↓
┌────────────────────────────────────────────┐
│              Spring Boot Backend           │
│                                            │
│ Course │ Assignment │ User │ Review │ Stats│
└────────────────────┬───────────────────────┘
                     │
             Application Service
                     │
        ┌────────────┴────────────┐
        ↓                         ↓
 Traditional Services        AI Application
                                  │
                           LangChain4j
                                  │
              ┌───────────────────┼───────────────────┐
              ↓                   ↓                   ↓
          AI Services            RAG               Tools
              │                   │                   │
         AI Agents          Vector Store       Business API
              │
       ┌──────┼──────┬──────┬───────┐
       ↓      ↓      ↓      ↓       ↓
     Tutor    QA   Review  Code   Teacher
     Agent   Agent  Agent  Agent  Agent

                     ↓
              LLM Gateway Layer
                     ↓
     OpenAI / Qwen / DeepSeek / Other Models
```

其中：

> **所有核心 LLM 业务能力原则上统一通过 LangChain4j 实现。**

禁止在不同业务模块中各自直接调用不同模型 HTTP API，避免 AI 层再次失控。

------

# 4. LangChain4j 在项目中的定位

本次升级必须将 **LangChain4j 提升为整个 AI 子系统的核心框架**。

LangChain4j 主要承担以下职责。

## 4.1 模型统一接入

所有模型统一抽象为：

```
SEForge
    ↓
LangChain4j
    ↓
Model Provider
    ├── OpenAI-compatible
    ├── Qwen
    ├── DeepSeek
    └── 其他模型
```

业务代码不得直接依赖具体模型厂商。

需要设计统一：

```
ModelConfig
ModelRegistry
ModelRouter
LLMService
```

支持不同业务场景使用不同模型。

例如：

```
普通课程问答
→ Fast Model

复杂作业辅导
→ Reasoning Model

代码 Review
→ Coding Model
```

------

## 4.2 LangChain4j AI Services

优先采用 LangChain4j 的 **AI Services** 抽象业务 Agent。

例如：

```
public interface CourseTutor {

    @SystemMessage("""
        你是软件工程课程教学助手。
        你的目标是引导学生理解知识，而不是简单提供答案。
        """)
    TutorResponse tutor(String question);
}
```

业务代码不直接拼接 Prompt。

Prompt、AI Service 和领域逻辑应进行清晰分离。

------

## 4.3 Chat Memory

课程问答、作业辅导等场景需要支持：

```
Student
   ↓
Conversation
   ↓
ChatMemory
```

建议按照：

```
studentId
courseId
conversationId
```

隔离上下文。

同时避免无限保存全部对话，需要设置：

- 上下文窗口；
- 历史摘要；
- 长对话压缩；
- 数据库存储。

------

## 4.4 LangChain4j RAG

课程知识库统一使用 LangChain4j RAG Pipeline。

基本流程：

```
课程资料
   ↓
Document Parser
   ↓
Document Splitter
   ↓
Embedding Model
   ↓
Embedding Store
   ↓
Retriever
   ↓
Content Retrieval
   ↓
LLM
```

支持：

- PDF；
- PPT/PPTX；
- Word；
- Markdown；
- TXT。

需要保存 Chunk Metadata：

```
courseId
documentId
chapter
page
section
source
```

最终回答必须能够返回来源，例如：

> 根据《软件工程》第 5 章第 18 页……

而不是仅生成文本答案。

------

## 4.5 Tool Calling

Tool 是新版多 Agent 系统的关键。

Agent 不应该只能“聊天”，而应该能够访问真实业务数据。

例如：

```
getCourseMaterial()
getAssignment()
getStudentSubmission()
getKnowledgePoint()
getStudentLearningProfile()
runStaticAnalysis()
getCodeReviewResult()
getAssignmentRubric()
```

实现：

```
LLM
 ↓
LangChain4j Agent
 ↓
Tool
 ↓
Spring Service
 ↓
Database / External Service
```

这样 AI 才真正进入系统业务。

------

# 5. 新增模块与功能

------

# 5.1 用户、课程与班级系统

**优先级：P0**

建立完整教学业务模型。

主要实体：

```
User
Student
Teacher

Course
Class
Semester

CourseMember
CourseResource
KnowledgePoint
```

支持：

- 学生注册 / 登录；
- 教师账号；
- 管理员账号；
- RBAC 权限控制；
- 创建课程；
- 加入课程；
- 邀请码；
- 学期管理；
- 课程成员管理；
- 教学资源管理；
- 公告；
- 课程章节；
- 知识点。

这是后续所有 AI 功能的数据基础。

------

# 5.2 课程知识库

**优先级：P0**

将原 SmartSE 知识库能力重新设计。

支持教师上传：

```
PPT
PDF
DOCX
Markdown
TXT
```

系统自动完成：

```
上传
 ↓
解析
 ↓
切片
 ↓
Embedding
 ↓
向量存储
 ↓
课程知识库
```

使用：

> **LangChain4j RAG**

实现。

增加：

- 文档状态；
- Chunk 管理；
- 文档删除；
- 重新索引；
- Embedding Version；
- 数据隔离；
- Metadata Filter。

例如查询：

```
courseId = 10001
chapter = 需求工程
```

避免不同课程之间发生知识污染。

------

# 5.3 AI 课程问答助手

**优先级：P0**

重构原有聊天功能。

新增：

- 多轮对话；
- RAG；
- 引用来源；
- 会话历史；
- 推荐问题；
- 知识点关联；
- 答案反馈；
- 模型切换；
- Streaming 输出。

使用：

```
LangChain4j AI Service
+
ChatMemory
+
RAG
```

实现。

流程：

```
Question
   ↓
CourseQAService
   ↓
LangChain4j
   ↓
RAG Retriever
   ↓
Course Knowledge
   ↓
LLM
   ↓
Answer + Citation
```

------

# 5.4 作业管理系统

**优先级：P0**

新增：

```
Assignment
Question
Submission
Answer
Grade
Rubric
Feedback
```

支持题型：

- 单选题；
- 多选题；
- 判断题；
- 简答题；
- 分析题；
- 软件设计题；
- 代码题。

教师可以：

- 创建作业；
- 设置截止时间；
- 设置题目；
- 设置评分标准；
- 查看提交；
- 批改作业。

学生可以：

- 查看作业；
- 保存草稿；
- 提交；
- 重交；
- 查看反馈。

------

# 5.5 AI 作业辅导系统

**优先级：P0**

这是新版 SEForge 的核心 AI 功能之一。

学生做题过程中可以调用：

```
Hint
解释知识点
检查思路
分析错误
评价答案
完整解析
```

与传统 ChatGPT 式问答不同，Tutor Agent 应知道：

```
当前课程
当前作业
当前题目
学生答案
课程知识库
评分标准
历史辅导记录
```

架构：

```
Student
   ↓
Assignment Tutor
   ↓
LangChain4j Agent
   ├── CourseKnowledgeTool
   ├── AssignmentTool
   ├── KnowledgePointTool
   └── SubmissionTool
```

------

# 5.6 多 Agent 系统重构

**优先级：P1**

删除原来为了展示 Multi-Agent 而存在的松散协作设计。

重新按照**业务职责**划分 Agent。

建议：

```
                  AI Orchestrator
                        │
        ┌───────────────┼───────────────┐
        ↓               ↓               ↓

 Course QA Agent    Tutor Agent    Review Agent

        ↓               ↓               ↓

Code Review Agent               Teacher Agent
```

Agent 职责：

### Course QA Agent

负责：

- 课程知识问答；
- RAG；
- 知识解释；
- 来源引用。

### Tutor Agent

负责：

- 作业辅导；
- 逐步提示；
- 学生错误分析；
- 学习引导。

### Review Agent

负责：

- 作业评价；
- 软件工程文档 Review；
- Rubric 分析。

### Code Review Agent

负责：

- 代码解释；
- Bug 分析；
- 静态分析结果解释；
- 代码质量 Review。

### Teacher Agent

负责：

- 题目生成；
- 学情分析；
- 教学建议；
- 作业统计总结。

Agent 统一采用：

> **LangChain4j AI Services + Tool Calling**

实现。

------

# 5.7 软件工程文档 AI Review

**优先级：P1**

这是项目与普通教学平台产生明显差异的功能。

支持上传：

```
SRS
需求分析报告
软件设计说明书
测试报告
项目总结
README
API 文档
```

AI 自动检查：

### 完整性

是否缺失必要部分。

### 一致性

不同章节之间是否存在冲突。

### 可验证性

需求是否可测试。

例如：

> 系统应拥有良好的响应速度。

AI 应指出：

> “良好”缺少可量化指标。

### 规范性

判断：

- 文档格式；
- 描述方式；
- UML 关系；
- 接口设计；
- 需求编号。

最终生成：

```
Document Review Report

Completeness
Consistency
Testability
Clarity
Suggestions
```

实现：

```
Document
 ↓
Parser
 ↓
LangChain4j
 ↓
Review Agent
 ↓
Structured Output
 ↓
ReviewReport
```

------

# 5.8 AI 辅助批改系统

**优先级：P1**

采用：

> AI 初评 + 教师确认

而不是：

> AI 直接决定最终成绩。

流程：

```
Student Submission
       ↓
Rubric
       ↓
Review Agent
       ↓
AI Evaluation
       ↓
Teacher Review
       ↓
Final Grade
```

AI 输出结构化：

```
{
  "scoreSuggestion": 85,
  "rubricItems": [],
  "strengths": [],
  "problems": [],
  "feedback": ""
}
```

LangChain4j 负责：

- Prompt；
- Structured Output；
- Agent 调用；
- Tool 调用。

------

# 5.9 代码 Review 系统

**优先级：P1**

继承原有 SonarQube 能力并重新设计。

流程：

```
Source Code
   ↓
Static Analysis
   ↓
SonarQube
   ↓
Analysis Result
   ↓
Code Review Agent
   ↓
Review Report
```

分析：

- Bug；
- Code Smell；
- Complexity；
- Security；
- Duplication；
- Naming；
- Exception Handling。

AI 不替代 SonarQube。

职责应该是：

```
SonarQube
→ 检测

LangChain4j Agent
→ 理解 + 解释 + 建议
```

------

# 5.10 自动代码运行 / Judge 系统

**优先级：P2**

属于高级功能。

支持：

```
Java
C++
Python
```

执行：

```
Code Submission
       ↓
Task Queue
       ↓
Sandbox Worker
       ↓
Docker Container
       ↓
Compile
       ↓
Execute
       ↓
Test Cases
       ↓
Judge Result
```

需要：

- Timeout；
- CPU Limit；
- Memory Limit；
- 禁止网络；
- 文件限制；
- 进程限制；
- 容器自动销毁。

输出：

```
Accepted
Wrong Answer
Compile Error
Runtime Error
Time Limit Exceeded
```

然后：

```
JudgeResult
     ↓
LangChain4j Code Tutor Agent
     ↓
AI Explanation
```

让 AI 分析：

> 为什么 Test Case 3 没通过？

------

# 5.11 学习画像系统

**优先级：P2**

通过：

```
作业
AI问答
知识点
成绩
错误类型
```

构建：

```
StudentLearningProfile
```

例如：

```
需求工程          82%
UML              73%
设计模式          58%
软件测试          91%
项目管理          76%
```

记录：

- 掌握知识点；
- 薄弱知识点；
- 高频错误；
- 学习活跃度；
- AI Tutor 使用情况；
- 作业完成情况。

------

# 5.12 个性化 AI 学习建议

**优先级：P2**

Teacher/Tutor Agent 可以读取：

```
LearningProfile
AssignmentHistory
QuestionHistory
KnowledgePoints
```

然后生成：

```
目前你的主要薄弱内容为：

1. Observer Pattern
2. Factory Pattern
3. UML Sequence Diagram

建议首先完成……
```

这里继续通过 LangChain4j Tool 获取真实数据，而不是把全部信息硬编码进 Prompt。

------

# 5.13 教师 Dashboard

**优先级：P1**

面向约 200 人规模教学，提供：

- 作业完成率；
- 平均分；
- 成绩分布；
- 知识点正确率；
- 高频问题；
- AI Tutor 调用量；
- 学生薄弱知识点；
- 高频错误；
- 活跃度趋势。

例如：

```
软件测试
├── Statement Coverage      92%
├── Branch Coverage         81%
└── Path Coverage           53%
```

教师可以快速发现：

> Path Coverage 是当前班级普遍薄弱内容。

------

# 5.14 AI 教师助手

**优先级：P2**

Teacher Agent 支持：

```
根据第五章生成10道题
分析这次作业最常见的5个错误
找出本周学生问得最多的问题
根据课程PPT生成复习提纲
生成一道UML设计题及Rubric
```

需要 LangChain4j Tools：

```
CourseTool
AssignmentTool
StatisticsTool
KnowledgeBaseTool
StudentProfileTool
```

------

# 6. AI 基础设施新增模块

除了业务 Agent，本次必须增加统一 AI Infrastructure。

建议目录：

```
backend
└── src
    └── main
        └── java
            └── ...
                └── ai
                    ├── agent
                    ├── service
                    ├── rag
                    ├── memory
                    ├── tool
                    ├── prompt
                    ├── model
                    ├── embedding
                    ├── parser
                    └── config
```

------

## 6.1 Model Registry

负责：

```
model name
provider
base url
api key
temperature
timeout
max token
```

统一模型配置。

------

## 6.2 Model Router

业务可以声明：

```
FAST
REASONING
CODING
EMBEDDING
```

而不是硬编码：

```
qwen-xxx
deepseek-xxx
```

方便后续换模型。

------

## 6.3 Prompt Registry

Prompt 从 Java 业务代码中解耦。

例如：

```
prompts/
├── course-qa/
├── tutor/
├── review/
├── code-review/
└── teacher/
```

支持：

```
Prompt Version
```

例如：

```
Tutor-v1
Tutor-v2
Tutor-v3
```

------

## 6.4 AI Trace

记录：

```
requestId
userId
agent
model
promptVersion
latency
tokenUsage
toolCalls
status
```

方便排查：

> 为什么这次 Agent 回答异常？

------

## 6.5 AI Failure Handling

所有 LangChain4j 调用必须考虑：

```
Timeout
Retry
Rate Limit
Provider Failure
Invalid Output
Tool Failure
```

基本策略：

```
Primary Model
    ↓ failure
Retry
    ↓ failure
Fallback Model
    ↓
Graceful Degradation
```

避免大模型接口异常导致整个教学平台不可用。

------

# 7. 工程基础设施升级

这一部分与 AI 功能同样重要。

------

## 7.1 Redis

用于：

- Cache；
- Session；
- Rate Limit；
- Task State；
- AI 请求状态。

------

## 7.2 异步任务系统

以下任务不应该阻塞 HTTP：

```
文档解析
Embedding
AI Review
代码分析
代码执行
报告生成
```

建议：

```
API
 ↓
Task Queue
 ↓
Worker
 ↓
Result
```

------

## 7.3 SSE

用于：

- AI Streaming；
- 作业 Review 状态；
- 文档解析状态；
- Judge 状态。

例如：

```
QUEUED
 ↓
PARSING
 ↓
EMBEDDING
 ↓
ANALYZING
 ↓
COMPLETED
```

------

## 7.4 日志

统一结构化日志：

```
traceId
userId
requestId
module
operation
duration
status
```

------

## 7.5 全局异常处理

统一：

```
{
  "code": "AI_MODEL_TIMEOUT",
  "message": "AI服务暂时不可用",
  "traceId": "xxx"
}
```

禁止不同 Controller 自己定义异常返回格式。

------

## 7.6 数据库 Migration

数据库 Schema 变化必须使用 Migration 管理。

禁止：

```
开发者自己修改数据库
↓
提交代码
↓
其他环境不知道修改了什么
```

------

# 8. 功能优先级

整体优先级划分如下：

| 模块                          | 优先级 | 是否必须     |
| ----------------------------- | ------ | ------------ |
| 用户 / RBAC                   | P0     | 必须         |
| 课程 / 班级                   | P0     | 必须         |
| 课程资源                      | P0     | 必须         |
| LangChain4j AI Infrastructure | P0     | **必须**     |
| LangChain4j RAG               | P0     | **必须**     |
| AI 课程问答                   | P0     | 必须         |
| 作业系统                      | P0     | 必须         |
| AI Tutor                      | P0     | **必须**     |
| Multi-Agent 重构              | P1     | 必须         |
| 软件工程文档 Review           | P1     | 推荐作为特色 |
| AI 辅助批改                   | P1     | 推荐         |
| Code Review                   | P1     | 推荐         |
| SonarQube                     | P1     | 推荐         |
| Teacher Dashboard             | P1     | 推荐         |
| Learning Profile              | P2     | 增强         |
| AI Teacher Agent              | P2     | 增强         |
| Docker Judge                  | P2     | 高级功能     |
| 自动代码运行                  | P2     | 高级功能     |
| 知识图谱                      | P3     | 可选         |
| GitHub 项目管理               | P3     | 可选         |
| Sprint / Milestone            | P3     | 可选         |

重点原则是：

> **宁可把 P0/P1 做完整，也不要为了功能数量把 P2/P3 全部做成半成品。**

------

# 9. 分阶段开发计划

不按照具体天数规划，而按照**功能可验收阶段**推进。

------

# Phase 1 —— 核心领域模型重构

### 目标

从原来的“AI 问答项目”建立真正的教学平台业务基础。

实现：

```
User
Role
Course
Class
Semester
CourseMember
CourseResource
KnowledgePoint
```

完成：

- 登录；
- 权限；
- 学生 / 教师角色；
- 课程创建；
- 课程加入；
- 课程资料管理。

### 验收标准

教师能够：

```
创建课程
→ 添加学生
→ 上传课程资料
```

学生能够：

```
加入课程
→ 查看课程
→ 查看资料
```

------

# Phase 2 —— LangChain4j AI Core

### 目标

彻底重构 AI 基础设施。

建立：

```
ai/
├── model
├── prompt
├── memory
├── rag
├── agent
├── tool
└── trace
```

实现：

- LangChain4j 模型统一接入；
- AI Services；
- Chat Memory；
- Prompt Registry；
- Model Router；
- Structured Output；
- AI Trace；
- Timeout / Retry；
- Fallback。

### 验收标准

所有新的 AI 功能必须：

> **通过 LangChain4j 调用。**

业务代码禁止直接请求具体 LLM HTTP API。

------

# Phase 3 —— Course RAG + AI Assistant

### 目标

完成新版课程智能问答系统。

实现：

```
Document
 ↓
Parse
 ↓
Split
 ↓
Embedding
 ↓
Vector Store
 ↓
Retrieve
 ↓
LangChain4j
```

支持：

- PDF / PPT / DOCX；
- 文档解析；
- 向量化；
- Course Scoped RAG；
- 引用来源；
- 多轮对话；
- Streaming。

### 验收标准

教师上传课程资料后：

```
Student Question
      ↓
系统能够基于课程资料回答
      ↓
返回来源
```

------

# Phase 4 —— Assignment + AI Tutor

### 目标

完成整个系统最重要的教学闭环。

实现：

```
Assignment
Question
Submission
Rubric
Feedback
```

以及：

> LangChain4j Tutor Agent

支持：

```
Hint
Explain
Check
Review
Guide
```

流程：

```
学生做题
 ↓
遇到问题
 ↓
AI Tutor
 ↓
继续完成
 ↓
提交作业
```

### 验收标准

一个学生可以完整完成：

```
查看作业
→ AI辅导
→ 保存答案
→ 提交
```

------

# Phase 5 —— Review Agent + AI 批改

### 目标

扩展 AI 在软件工程课程中的专业应用。

完成：

### Document Review

```
SRS
Design Document
Test Report
```

### Assignment Review

```
Rubric
Submission
AI Evaluation
```

### Code Review

```
Static Analysis
+
LLM Review
```

LangChain4j Agent 能够调用：

```
AssignmentTool
RubricTool
DocumentTool
CodeAnalysisTool
```

### 验收标准

能够自动生成结构化：

```
Assignment Review Report
Document Review Report
Code Review Report
```

------

# Phase 6 —— Teacher Dashboard + Learning Profile

### 目标

真正支持约 200 人教学场景。

建立：

```
StudentLearningProfile
ClassStatistics
KnowledgePointStatistics
```

教师能够查看：

```
知识点正确率
作业完成率
高频问题
薄弱知识点
成绩分布
AI Tutor 使用情况
```

学生能够查看：

```
掌握情况
薄弱知识
学习建议
```

------

# Phase 7 —— AI Teacher Agent

### 目标

形成完整 AI 教学助手。

Teacher Agent 可以执行：

```
生成题目
生成Rubric
分析作业
分析班级
生成复习资料
识别薄弱知识点
```

数据通过 LangChain4j Tools 获取。

形成：

```
Teacher
   ↓
Teacher Agent
   ↓
Tool Calling
   ↓
Course / Assignment / Statistics
```

------

# Phase 8 —— Sandbox / Online Judge

### 目标

增加项目工程难度与技术亮点。

建立：

```
Judge API
Task Queue
Judge Worker
Docker Sandbox
```

支持：

```
Java
Python
C++
```

完成：

```
Compile
Execute
Test
Judge
AI Explain
```

AI 部分继续使用：

> LangChain4j Code Tutor Agent

------

# Phase 9 —— 工程稳定性与发布

最终进行系统级工程强化。

完成：

- Docker Compose；
- Production Config；
- Redis；
- Migration；
- Cache；
- Rate Limit；
- Health Check；
- Structured Logging；
- Trace ID；
- Metrics；
- Unit Test；
- Integration Test；
- E2E；
- CI；
- API Documentation。

重点验证约：

> **200 名课程用户规模**

下系统是否能够稳定运行。

------

# 10. 最终核心功能范围

完成 Phase 1～7 后，SEForge 应至少形成以下七大业务模块：

```
SEForge
│
├── Course System
│
├── Knowledge Base
│
├── AI Course Assistant
│
├── Assignment System
│
├── AI Tutor
│
├── AI Review
│
└── Learning Analytics
```

AI 子系统则形成：

```
LangChain4j AI Platform
│
├── Model Router
├── AI Services
├── RAG
├── Chat Memory
├── Tool Calling
├── Structured Output
├── Prompt Registry
└── Agent Orchestrator
```

最终实现：

```
                SEForge

             Course Content
                   │
                   ↓
              LangChain4j
                   │
        ┌──────────┼──────────┐
        ↓          ↓          ↓
       RAG       Agents      Tools
        │          │          │
        └──────────┼──────────┘
                   ↓
              AI Services
                   │
     ┌─────────────┼─────────────┐
     ↓             ↓             ↓
Course Assistant Tutor      Review Agent
                                 │
               ┌─────────────────┼───────────┐
               ↓                 ↓           ↓
          Assignment        Document       Code
            Review           Review        Review
                   │
                   ↓
            Learning Profile
                   │
                   ↓
           Teacher Dashboard
```

------

# 11. 本轮升级的开发原则

整个升级过程中遵守以下原则：

1. **LangChain4j 是 AI 核心框架，而非附属功能。**
2. Agent 必须对应真实业务职责，不再为了展示 Multi-Agent 而设计 Agent。
3. AI 不直接访问数据库，统一通过受控的 Tool / Service 获取业务数据。
4. Prompt 与业务代码分离，并支持版本管理。
5. 所有 AI 输出尽可能采用结构化对象，而不是依赖字符串解析。
6. RAG 必须支持课程级数据隔离和来源引用。
7. 耗时操作全部异步化。
8. AI 服务异常不能导致整个教学系统不可用。
9. 自动评分采用“AI 辅助 + 教师确认”，保留教师最终控制权。
10. 优先完成真实业务闭环，再增加炫技功能。
11. P0 / P1 模块必须达到可实际使用水平，P2 / P3 功能可以根据开发进度取舍。
12. 每一个 Phase 均应具有独立可运行、可测试、可验收状态。

------

## 12. 项目升级后的定位

最终 SEForge 不再定义为：

> 一个基于 LangChain4j 的聊天机器人。

而应定义为：

> **SEForge 是一个基于 Spring Boot、Vue 3 与 LangChain4j 构建的 AI-native 软件工程教学平台。系统围绕课程知识、作业、软件工程实践和学习过程构建统一教学数据模型，并通过 RAG、AI Services、Tool Calling 与业务型 Multi-Agent，将大语言模型能力应用于课程问答、个性化作业辅导、软件工程文档评审、代码质量分析、AI 辅助批改及教学学情分析，形成从“学习—辅导—实践—评审—反馈”的完整智能教学闭环。**