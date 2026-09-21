# 通义千问向量模型配置修改记录

## 问题描述

在知识库的RAG功能中，使用通义千问向量模型生成嵌入向量时出现以下错误：

```
com.alibaba.dashscope.exception.ApiException: {"statusCode":-1,"message":"IllegalArgumentException: Expected URL scheme 'http' or 'https' but no scheme was found for sk-dc2...","code":"network error","isJson":false}
```

这个错误表明在创建API请求时，将API密钥错误地作为URL的一部分使用，导致URL解析失败。

## 解决方案

1. 创建专门的工厂类来标准化通义千问向量模型的创建过程
2. 分离API密钥和基础URL的配置
3. 增加详细的错误处理和诊断信息
4. 添加测试工具验证配置正确性

## 文件修改列表

1. **新增文件**:
   - `src/main/java/com/ustb/smartse/modules/knowledgebase/factory/QwenEmbeddingModelFactory.java`
   - `src/main/java/com/ustb/smartse/modules/knowledgebase/test/QwenEmbeddingModelTest.java`
   - `src/main/java/com/ustb/smartse/modules/knowledgebase/test/TestQwenEmbedding.java`
   - `src/main/java/com/ustb/smartse/modules/knowledgebase/factory/README.md`
   - `src/main/java/com/ustb/smartse/modules/knowledgebase/CHANGES.md`

2. **修改文件**:
   - `src/main/java/com/ustb/smartse/modules/knowledgebase/config/MilvusConfig.java`
     - 更新了`embeddingModel()`方法，使用新创建的工厂类

## 主要改进点

### 1. API正确性
- 确保API密钥不会被误用作URL
- 正确设置baseUrl，使其指向完整的API端点

### 2. 错误处理
- 添加详细的日志输出
- 对特定类型的错误提供更具体的诊断信息
- 在测试失败时提供排查建议

### 3. 可测试性
- 添加独立的测试工具
- 支持通过命令行参数或配置文件测试

### 4. 文档
- 添加详细的README说明工厂类的使用方法
- 记录问题原因和解决方案
- 提供配置和测试指南

## 使用说明

1. 在`application.yml`中确保正确配置:
   ```yaml
   langchain4j:
     dashscope:
       api-key: "您的API密钥"
       base-url: "https://dashscope.aliyuncs.com/api/v1"
   ```

2. 测试配置:
   ```
   java -cp your-app.jar com.ustb.smartse.modules.knowledgebase.test.TestQwenEmbedding
   ```

3. 如需更新API密钥，只需更新配置文件，无需修改代码 