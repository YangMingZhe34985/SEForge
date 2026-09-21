package com.ustb.smartse.modules.agent.service;

import com.ustb.smartse.modules.agent.config.AgentConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentRouterService {

    private static final Logger log = LoggerFactory.getLogger(AgentRouterService.class);

    @Autowired
    private final AgentConfig.GeneralAssistant generalAssistant;

    @Autowired
    private final AgentConfig.ConceptExplanationAgent conceptExplanationAgent;

    @Autowired
    private final AgentConfig.RequirementAnalysisAgent requirementAnalysisAgent;

    @Autowired
    private final AgentConfig.SoftwareDesignAgent softwareDesignAgent;

    @Autowired
    private final AgentConfig.TestCaseAgent testCaseAgent;

    @Autowired
    private final AgentConfig.CodeReviewAgent codeReviewAgent;

    /**
     * 主要路由方法：智能判断问题类型并选择合适的处理模式
     */
    public TokenStream routeToAgent(String memoryId, String message) {
        StringBuilder reasoningProcess = new StringBuilder();
        reasoningProcess.append("[REASONING_START]\n");

        // 第一步：先判断是否是简单问题（可直接由generalAssistant回答）
        reasoningProcess.append("嗯，我需要先分析用户的问题类型...\n");
        if (isSimpleQuestion(memoryId, message)) {
            reasoningProcess.append(" 这是一个简单问题，那么将由通用助手直接回答\n");
            reasoningProcess.append("[REASONING_END]\n\n");

            log.info("简单问题由generalAssistant直接回答: {}", shortenMessage(message));
            String formattedMessage = "<原始问题>\n" + message + "\n</原始问题>\n\n" +
                    "<附加推理过程>\n" + reasoningProcess.toString() + "</附加推理过程>";
            TokenStream originalStream = generalAssistant.stream(memoryId, formattedMessage);

            return new ReasoningTokenStream(reasoningProcess.toString(), originalStream);
        }

        // 第二步：分析是否需要多智能体协作
        reasoningProcess.append("这不是一个简单问题呢，我需要请求专业智能体来处理，分析调用哪些专业智能体...\n");
        String[] relevantAgents = determineRelevantAgents(memoryId, message);

        // 多智能体协作处理
        if (relevantAgents.length >0) {
            reasoningProcess.append("我将选择以下智能体：\n");
            for (String agent : relevantAgents) {
                if (!agent.trim().equals("通用问题")) {
                    reasoningProcess.append("     - ").append(agent.trim()).append("\n");
                }
            }
            reasoningProcess.append("开始收集各专家智能体的意见，并由通用助手进行整合...\n");
            reasoningProcess.append("[REASONING_END]\n\n");

            log.info("多智能体协作处理问题: {}, 使用智能体: {}", shortenMessage(message), Arrays.toString(relevantAgents));
            TokenStream originalStream = handleMultiAgentCollaboration(memoryId, message, relevantAgents, reasoningProcess);
            return new ReasoningTokenStream(reasoningProcess.toString(), originalStream);
        }
        // 默认回退到通用助手
        else {
            reasoningProcess.append("   结果：无法确定需要哪种专业智能体，将由通用助手处理\n");
            reasoningProcess.append("[REASONING_END]\n\n");

            log.info("未能确定合适智能体，由generalAssistant处理: {}", shortenMessage(message));
            String formattedMessage = "<原始问题>\n" + message + "\n</原始问题>\n\n" +
                    "<附加推理过程>\n" + reasoningProcess.toString() + "</附加推理过程>";
            TokenStream originalStream = generalAssistant.stream(memoryId, formattedMessage);
            return new ReasoningTokenStream(reasoningProcess.toString(), originalStream);
        }
    }

    /**
     * 判断是否为简单问题（如"你是谁"等闲聊问题）
     */
    private boolean isSimpleQuestion(String memoryId, String message) {
        String analysisPrompt = "请分析下面的问题是否是简单的闲聊或基础问题(学习方法或用户心情之类)，无需专业知识即可回答。" +
                "例如'你是谁'、'你能做什么'等。只需回复'是'或'否'：\n\n" + message;
        String response = generalAssistant.analyzeWithoutMemory(analysisPrompt).trim().toLowerCase();
        return response.contains("是");
    }

    /**
     * 确定可能相关的智能体类型（可能返回多个）
     */
    private String[] determineRelevantAgents(String memoryId, String message) {
        String analysisPrompt = "请分析用户问题可能涉及哪些专业领域，需要哪类专家回答。从以下选项中选择(可多选，用逗号分隔)：" +
                "概念解释、需求分析、软件设计、测试用例、代码审查、通用问题。\n\n如果完全无法判断，请直接回复'通用问题'。\n\n问题：" + message;

        String response = generalAssistant.analyzeWithoutMemory(analysisPrompt).trim();
        log.debug("智能体选择分析结果: {}", response);

        return response.split("，|,|、");
    }

    /**
     * 处理多智能体协作
     */
    private TokenStream handleMultiAgentCollaboration(String memoryId, String message, String[] agentTypes, StringBuilder reasoningProcess) {

        // 构建协作提示
        StringBuilder expertInputs = new StringBuilder();
        Set<String> processedAgentTypes = new HashSet<>();

        // 去重并收集各专家意见
        for (String agentType : agentTypes) {
            String trimmedType = agentType.trim();
            if (processedAgentTypes.contains(trimmedType) || trimmedType.equals("通用问题")) {
                continue;
            }

            processedAgentTypes.add(trimmedType);
            String expertResponse = getExpertResponse(memoryId, message, trimmedType);
            expertInputs.append("【").append(trimmedType).append("专家意见】:\n")
                    .append(expertResponse).append("\n\n");
        }

        // 如果没有收集到有效专家意见，直接使用generalAssistant
        if (expertInputs.length() == 0) {
            String formattedMessage = "<原始问题>\n" + message + "\n</原始问题>\n\n" +
                    "<附加推理过程>\n" + reasoningProcess.toString() + "</附加推理过程>";
            return generalAssistant.stream(memoryId, formattedMessage);
        }

        // 构建整合提示
        String coordinationPrompt = "作为智能协调者，我需要你基于以下专家意见，为用户提供全面、综合的回答，请忽略附加推理过程：\n\n" +
                expertInputs.toString() +
                "\n<原始问题> \n" + message + "\n</原始问题>" +
                "\n\n请整合各专家观点，给出连贯、完整的回答，避免简单拼接。确保回答直接针对用户问题，风格统一自然。"+
                "\n\n<附加推理过程>\n" + reasoningProcess.toString() + "</附加推理过程>";

        return generalAssistant.stream(memoryId, coordinationPrompt);
    }

    /**
     * 获取特定专家的非流式回答
     */
    private String getExpertResponse(String memoryId, String message, String agentType) {
        try {
            StringBuilder responseBuilder = new StringBuilder();
            TokenStream tokenStream = switch (agentType) {
                case "概念解释" -> conceptExplanationAgent.stream(memoryId+"temp", message);
                case "需求分析" -> requirementAnalysisAgent.stream(memoryId+"temp", message);
                case "软件设计" -> softwareDesignAgent.stream(memoryId+"temp", message);
                case "测试用例" -> testCaseAgent.stream(memoryId+"temp", message);
                case "代码审查" -> codeReviewAgent.stream(memoryId+"temp", message);
                default -> generalAssistant.stream(memoryId+"temp", message);
            };

            // 手动收集完整响应
            tokenStream
                    .onPartialResponse(responseBuilder::append)
                    .onError(e -> log.error("收集{}专家回答时出错: {}", agentType, e.getMessage()))
                    .start();

            return responseBuilder.toString();
        } catch (Exception e) {
            log.error("获取{}专家回答时出错: {}", agentType, e.getMessage());
            return "（该专家意见获取失败）";
        }
    }

    /**
     * 缩短消息用于日志显示
     */
    private String shortenMessage(String message) {
        if (message.length() <= 50) {
            return message;
        }
        return message.substring(0, 47) + "...";
    }

}


