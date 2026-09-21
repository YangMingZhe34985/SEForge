package com.ustb.smartse.modules.agent.config;

import com.ustb.smartse.modules.agent.toolservice.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    public interface GeneralAssistant {
        // 非流式
        String chat(@MemoryId Object memoryId, @UserMessage String userMessage);
        // 流式响应
        @SystemMessage("""
                您是由SmartSE团队研发的一款致力于为学生提供专业的软件工程课程问题解答的小助手。
                您致力于为学生提供专业的软件工程领域的概念解释、需求分析、软件设计、测试用例、代码评审、习题解答等服务。
                请以友好、乐于助人且愉快的方式来回复，同时善于渐进式引导学生。
                您正在通过在线聊天系统与学生互动。
                请讲中文。
                """)
        TokenStream stream(@MemoryId Object memoryId, @UserMessage String userMessage);

        // 新增方法：不记录到对话历史的分析方法
        String analyzeWithoutMemory(String message);
    }

    public interface ConceptExplanationAgent {
        @SystemMessage("""
        您是专业的软件工程概念解释助手，擅长：
        1. 结合术语定义和示例对比解释复杂概念
        2. 通过知识图谱查询提供权威定义
        3. 使用对比生成技术展示相似/相异概念
        
        请遵循以下原则：
        - 先给出标准定义，再提供生活化示例
        - 对相似概念进行对比分析（如：解释'聚合'时对比'组合'）
        - 使用表格或分点形式呈现关键特征
        - 最后通过提问引导学生思考应用场景
        """)
        TokenStream stream(@MemoryId Object memoryId, @UserMessage String userMessage);
    }

    public interface RequirementAnalysisAgent {
        @SystemMessage("""
        您是专业的需求分析助手，具备：
        1. 将用户故事转换为规范用例图的能力
        2. 自动生成PlantUML代码并校验规则
        3. 识别不完整/矛盾的需求
        
        交互要求：
        - 先确认需求边界和业务目标
        - 展示转换前后的对比（用户故事→用例元素）
        - 对生成的PlantUML提供解释说明
        - 标注可能存在的需求风险点
        """)
        TokenStream stream(@MemoryId Object memoryId, @UserMessage String userMessage);
    }

    public interface SoftwareDesignAgent {
        @SystemMessage("""
        您是智能软件设计顾问，专长：
        1. 根据问题特征推荐设计模式
        2. 自动生成类图并支持迭代优化
        3. 通过Graphviz实现可视化表达
        
        工作流程：
        1. 分析用户描述的问题场景
        2. 推荐2-3种适用模式并对比优劣
        3. 生成初始类图后询问修改需求
        4. 最终提供可导出的设计文档
        """)
        TokenStream stream(@MemoryId Object memoryId, @UserMessage String userMessage);
    }

    public interface TestCaseAgent {
        @SystemMessage("""
        您是自动化测试专家，能够：
        1. 进行边界值/等价类分析
        2. 生成符合JUnit规范的测试模板
        3. 通过因果推理覆盖异常路径
        
        输出要求：
        - 测试用例应包含：前置条件/输入数据/预期结果
        - 对关键边界值进行标注说明
        - 提供测试覆盖率优化建议
        - 支持多种断言风格选择
        """)
        TokenStream stream(@MemoryId Object memoryId, @UserMessage String userMessage);
    }

    public interface CodeReviewAgent {
        @SystemMessage("""
        您是高级代码评审助手，集成：
        1. SonarQube静态分析能力
        2. 圈复杂度/重复代码检测
        3. 重构建议生成系统
        
        评审标准：
        - 优先检查安全漏洞和性能瓶颈
        - 对不良味道代码标注严重等级
        - 提供重构前后的代码对比
        - 给出符合SOLID原则的改进方案
        """)
        TokenStream stream(@MemoryId Object memoryId, @UserMessage String userMessage);
    }


    // 内容检索器
//        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
//                .embeddingStore(embeddingStore)
//                .embeddingModel(qwenEmbeddingModel)
//                .maxResults(5) // 最相似的5个结果
//                .minScore(0.6) // 只找相似度在0.6以上的内容
//                .build();

    @Bean
    public ConceptExplanationAgent conceptExplanationAgent(ChatLanguageModel deepseekChatModel,
                                                            StreamingChatLanguageModel deepseekStreamingChatModel,
                                                           ConceptExplanationToolService conceptExplanationToolService) {
        return AiServices.builder(ConceptExplanationAgent.class)
                .chatLanguageModel(deepseekChatModel)
                .streamingChatLanguageModel(deepseekStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(30)
                                .build()
                )
                .tools(conceptExplanationToolService)
                .build();
    }

    @Bean
    public RequirementAnalysisAgent requirementAnalysisAgent(ChatLanguageModel deepseekChatModel,
                                                              StreamingChatLanguageModel deepseekStreamingChatModel,
                                                             RequirementAnalysisToolService requirementAnalysisToolService) {
        return AiServices.builder(RequirementAnalysisAgent.class)
                .chatLanguageModel(deepseekChatModel)
                .streamingChatLanguageModel(deepseekStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(30)
                                .build()
                )
                .tools(requirementAnalysisToolService)
                .build();
    }

    @Bean
    public SoftwareDesignAgent softwareDesignAgent(ChatLanguageModel deepseekChatModel,
                                                    StreamingChatLanguageModel deepseekStreamingChatModel,
                                                   SoftwareDesignToolService softwareDesignToolService) {
        return AiServices.builder(SoftwareDesignAgent.class)
                .chatLanguageModel(deepseekChatModel)
                .streamingChatLanguageModel(deepseekStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(30)
                                .build()
                )
                .tools(softwareDesignToolService)
                .build();
    }

    @Bean
    public TestCaseAgent testCaseAgent(ChatLanguageModel deepseekChatModel,
                                        StreamingChatLanguageModel deepseekStreamingChatModel,
                                       TestCaseToolService testCaseToolService) {
        return AiServices.builder(TestCaseAgent.class)
                .chatLanguageModel(deepseekChatModel)
                .streamingChatLanguageModel(deepseekStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(30)
                                .build()
                )
                .tools(testCaseToolService)
                .build();
    }

    @Bean
    public CodeReviewAgent codeReviewAgent(ChatLanguageModel deepseekChatModel,
                                            StreamingChatLanguageModel deepseekStreamingChatModel,
                                           CodeReviewToolService codeReviewToolService) {
        return AiServices.builder(CodeReviewAgent.class)
                .chatLanguageModel(deepseekChatModel)
                .streamingChatLanguageModel(deepseekStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(30)
                                .build()
                )
                .tools(codeReviewToolService)
                .build();
    }
}