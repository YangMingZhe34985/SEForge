# 通义千问向量模型配置改进

本目录包含对通义千问向量模型配置的改进，解决了在RAG功能中使用时出现的URL格式错误问题。

## 主要改进

1. 创建了`QwenEmbeddingModelFactory`工厂类，用于统一和标准化通义千问向量模型的创建过程
2. 修改了`MilvusConfig`类，使用新的工厂类创建嵌入模型
3. 添加了详细的错误处理和日志输出，便于问题诊断
4. 添加了独立的测试类，方便验证模型配置是否正确

## 错误原因分析

原来的代码在创建`QwenEmbeddingModel`实例时存在URL处理问题，具体体现在：

```
IllegalArgumentException: Expected URL scheme 'http' or 'https' but no scheme was found for sk-dc2...
```

这个错误表明API密钥被错误地用作URL，而不是作为独立的参数传递。

## 使用方法

### 配置方式

在`application.yml`或`application.properties`中添加如下配置：

```yaml
langchain4j:
  dashscope:
    api-key: "您的通义千问API密钥"
    base-url: "https://dashscope.aliyuncs.com/api/v1"
```

### 测试方法

1. 可以运行`TestQwenEmbedding`类进行独立测试：

```
java -cp your-app.jar com.ustb.smartse.modules.knowledgebase.test.TestQwenEmbedding [可选的API密钥]
```

2. 或者使用Spring Boot profile运行测试：

```
java -jar your-app.jar --spring.profiles.active=qwen-test
```

## 工厂类核心功能

- 统一的创建方法，确保配置一致性
- 预先测试DashScope SDK连接
- 正确设置API密钥和baseUrl
- 详细的错误处理和诊断信息

## 注意事项

1. 确保API密钥有效且有足够的配额
2. 确保网络能够正常访问通义千问API
3. 在实际使用中监控日志，以便及时发现问题 