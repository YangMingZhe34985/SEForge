package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import com.ustb.smartse.modules.knowledgebase.service.RAGService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RAGServiceImpl implements RAGService {

    @Autowired
    private KnowledgeService knowledgeService;
    
    @Autowired
    private ChatLanguageModel chatModel;

    /**
     * 检索相关文档的数量
     */
    private static final int DEFAULT_RETRIEVAL_COUNT = 5;
    
    /**
     * 系统提示模板
     */
    private static final String SYSTEM_PROMPT = "你是一个软件工程课程助手，专注于回答软件工程相关问题。" +
            "你需要基于检索到的相关知识来生成答案。如果知识库中没有相关信息，你应该诚实地回答不知道。" +
            "请确保回答简洁、准确、专业。";
    
    /**
     * RAG提示模板
     */
    private static final String RAG_TEMPLATE = """
            根据以下检索到的知识，回答用户问题。
            如果检索到的知识无法回答用户问题，请诚实地说明你不知道答案。
            
            用户问题: {{question}}
            
            检索到的知识:
            {{context}}
            
            请用中文回答。
            """;

    @Override
    public String generateAnswer(String question) {
        try {
            // 从知识库检索相关内容
            List<TextSegment> relevantSegments = knowledgeService.semanticSearch(question, DEFAULT_RETRIEVAL_COUNT);
            
            if (relevantSegments.isEmpty()) {
                return "对不起，我无法找到与您问题相关的信息。";
            }
            
            // 构建上下文
            String context = buildContext(relevantSegments);
            
            // 构建提示
            PromptTemplate promptTemplate = PromptTemplate.from(RAG_TEMPLATE);
            Prompt prompt = promptTemplate.apply(Map.of(
                    "question", question,
                    "context", context
            ));
            
            // 发送到大模型生成回答
            MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
            memory.add(new SystemMessage(SYSTEM_PROMPT));
            memory.add(new UserMessage(prompt.text()));
            
            // 使用新版API方法生成响应
            ChatResponse chatResponse = chatModel.chat(memory.messages());
            AiMessage aiMessage = chatResponse.aiMessage();
            return aiMessage.text();
            
        } catch (Exception e) {
            log.error("生成回答失败", e);
            return "抱歉，处理您的问题时出现错误，请稍后再试。";
        }
    }

    @Override
    public Map<String, Object> generateAnswerWithMetadata(String question) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 使用混合检索获取相关内容
            List<TextSegment> relevantSegments = knowledgeService.hybridSearch(question, DEFAULT_RETRIEVAL_COUNT);
            
            if (relevantSegments.isEmpty()) {
                result.put("answer", "对不起，我无法找到与您问题相关的信息。");
                result.put("sourceDocs", List.of());
                return result;
            }
            
            // 构建上下文
            String context = buildContext(relevantSegments);
            
            // 构建提示
            PromptTemplate promptTemplate = PromptTemplate.from(RAG_TEMPLATE);
            Prompt prompt = promptTemplate.apply(Map.of(
                    "question", question,
                    "context", context
            ));
            
            // 发送到大模型生成回答
            MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
            memory.add(new SystemMessage(SYSTEM_PROMPT));
            memory.add(new UserMessage(prompt.text()));
            
            // 使用新版API方法生成响应
            ChatResponse chatResponse = chatModel.chat(memory.messages());
            AiMessage aiMessage = chatResponse.aiMessage();
            
            // 收集检索到的文档元数据
            List<Map<String, Object>> sourceDocs = relevantSegments.stream()
                    .map(segment -> {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("content", segment.text());
                        Metadata metadata = segment.metadata();
                        Map<String, Object> metadataMap = metadata.toMap();
                        for (Map.Entry<String, Object> entry : metadataMap.entrySet()) {
                            doc.put(entry.getKey(), entry.getValue());
                        }
                        return doc;
                    })
                    .collect(Collectors.toList());
            
            result.put("answer", aiMessage.text());
            result.put("sourceDocs", sourceDocs);
            
            return result;
            
        } catch (Exception e) {
            log.error("生成带元数据的回答失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("answer", "抱歉，处理您的问题时出现错误，请稍后再试。");
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    @Override
    public Map<String, Object> retrieveRelevantCodeSnippets(String question, int maxResults) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 查找相关代码片段（假设来源类型3是UML案例库，其中包含代码示例）
            List<KnowledgeEntry> codeEntries = knowledgeService.keywordSearch(question, maxResults);
            
            List<Map<String, Object>> snippets = codeEntries.stream()
                    .filter(entry -> entry.getSourceType() == 3)
                    .map(entry -> {
                        Map<String, Object> snippet = new HashMap<>();
                        snippet.put("title", entry.getTitle());
                        snippet.put("content", entry.getContent());
                        snippet.put("category", entry.getCategory());
                        return snippet;
                    })
                    .collect(Collectors.toList());
            
            result.put("snippets", snippets);
            result.put("count", snippets.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("检索代码片段失败", e);
            return Map.of(
                    "error", "检索相关代码片段时出现错误",
                    "message", e.getMessage()
            );
        }
    }
    
    /**
     * 构建上下文字符串
     */
    private String buildContext(List<TextSegment> segments) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            context.append(i + 1).append(". ").append(segments.get(i).text()).append("\n\n");
        }
        return context.toString();
    }
} 