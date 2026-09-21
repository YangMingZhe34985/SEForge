package com.ustb.smartse.modules.knowledgebase.controller;

import com.ustb.smartse.common.utils.R;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RAGController {

    @Value("${langchain4j.openai.chat-model.api-key}")
    private String openaiApiKey;

    @Value("${langchain4j.openai.chat-model.base-url}")
    private String openaiBaseUrl;

    @Value("${langchain4j.openai.chat-model.model-name}")
    private String openaiModelName;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public RAGController(ChatLanguageModel chatModel, 
                         EmbeddingStore<TextSegment> embeddingStore,
                         EmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        log.info("RAGController初始化完成，使用模型: {}, 向量存储类型: {}", 
                this.chatModel.getClass().getSimpleName(), embeddingStore.getClass().getSimpleName());
    }

    @Data
    public static class QueryRequest {
        private String question;
        private int topK = 5;
        private double minScore = 0.0;
    }

    @Data
    public static class QueryResponse {
        private String answer;
        private List<MatchResult> references;

        public QueryResponse(String answer, List<MatchResult> references) {
            this.answer = answer;
            this.references = references;
        }
    }

    @Data
    public static class MatchResult {
        private String content;
        private double score;
        private Map<String, String> metadata;

        public MatchResult(String content, double score, Map<String, String> metadata) {
            this.content = content;
            this.score = score;
            this.metadata = metadata;
        }
    }

    private List<ChatMessage> buildMessages(String question, List<EmbeddingMatch<TextSegment>> relevantMatches) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("以下是一些相关的参考资料：\n\n");
        
        for (int i = 0; i < relevantMatches.size(); i++) {
            EmbeddingMatch<TextSegment> match = relevantMatches.get(i);
            TextSegment segment = match.embedded();
            double score = match.score();
            
            contextBuilder.append("参考资料").append(i + 1);
            contextBuilder.append("（相关度：")
                    .append(String.format("%.2f", score))
                    .append("）");
            contextBuilder.append("：\n");
            contextBuilder.append(segment.text()).append("\n\n");
        }

        String systemPrompt = """
                你是一个专业的软件工程助手。请根据提供的参考资料，回答用户的问题。
                在回答时，请：
                1. 综合参考资料中的信息
                2. 使用专业且准确的术语
                3. 保持答案的连贯性和完整性
                4. 如果参考资料不足以完整回答问题，请说明这一点
                5. 引用具体的参考资料编号来支持你的论述
                """;

        String userPrompt = String.format("""
                参考资料：
                %s
                
                问题：%s
                """, contextBuilder.toString(), question);

        return Arrays.asList(
            new SystemMessage(systemPrompt),
            new UserMessage(userPrompt)
        );
    }

    @PostMapping("/query")
    public R query(@RequestBody QueryRequest request) {
        log.info("收到RAG查询请求: {}, topK={}, minScore={}", 
                request.getQuestion(), request.getTopK(), request.getMinScore());
        
        try {
            // 使用注入的EmbeddingModel生成查询的向量嵌入
            log.info("开始生成查询向量嵌入...");
            Embedding queryEmbedding = embeddingModel.embed(request.getQuestion()).content();
            log.info("查询向量嵌入生成成功，维度: {}", queryEmbedding.vector().length);

            // 在向量数据库中搜索相似内容
            log.info("开始在向量数据库中搜索相似内容...");
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(request.getTopK())
                    .minScore(request.getMinScore())
                    .build();

            List<EmbeddingMatch<TextSegment>> relevantMatches = embeddingStore.search(searchRequest).matches();
            log.info("向量搜索完成，找到 {} 个相关匹配", relevantMatches.size());

            if (relevantMatches.isEmpty()) {
                log.warn("未找到相关内容，可能是向量数据库中没有数据或相似度低于阈值");
                return R.error(404, "未找到相关内容，请检查知识库是否已导入数据");
            }

            // 转换检索结果
            List<MatchResult> matches = new ArrayList<>();
            for (int i = 0; i < relevantMatches.size(); i++) {
                EmbeddingMatch<TextSegment> match = relevantMatches.get(i);
                TextSegment segment = match.embedded();
                Map<String, String> metadataMap = new HashMap<>();
                Metadata metadata = segment.metadata();
                if (metadata != null) {
                    Map<String, Object> metadataValues = metadata.toMap();
                    for (Map.Entry<String, Object> entry : metadataValues.entrySet()) {
                        metadataMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                    }
                }
                
                matches.add(new MatchResult(
                        segment.text(),
                        match.score(),
                        metadataMap
                ));
                
                log.debug("匹配 #{}: 相关度={}, 内容长度={}, 元数据={}", 
                        i+1, match.score(), segment.text().length(), metadataMap);
            }

            // 构建消息列表
            log.info("构建LLM提示...");
            List<ChatMessage> messages = buildMessages(request.getQuestion(), relevantMatches);

            // 使用语言模型生成回答
            log.info("调用语言模型生成回答...");
            AiMessage aiMessage = chatModel.chat(messages).aiMessage();
            String answer = aiMessage.text();
            log.info("语言模型回答生成成功，长度: {}", answer.length());

            // 返回结果
            QueryResponse response = new QueryResponse(answer, matches);
            log.info("RAG查询成功完成");
            return R.ok().put("data", response);

        } catch (Exception e) {
            log.error("RAG查询失败: {}", e.getMessage(), e);
            
            // 记录更详细的错误信息
            if (e.getCause() != null) {
                log.error("根本原因: {}", e.getCause().getMessage());
            }
            
            // 检查是否是向量存储相关的错误
            String errorMsg = e.getMessage();
            if (errorMsg.contains("Milvus") || errorMsg.contains("vector") || 
                errorMsg.contains("embedding") || errorMsg.contains("collection")) {
                log.error("可能是向量数据库连接或查询问题");
                return R.error(500, "向量数据库查询失败: " + e.getMessage());
            }
            
            // 检查是否是语言模型相关的错误
            if (errorMsg.contains("model") || errorMsg.contains("API") || 
                errorMsg.contains("token") || errorMsg.contains("OpenAI")) {
                log.error("可能是语言模型API调用问题");
                return R.error(500, "语言模型调用失败: " + e.getMessage());
            }
            
            return R.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/test")
    public R test() {
        log.info("执行RAG测试查询...");
        try {
            String testQuestion = "什么是软件工程？请详细解释其基本概念和重要性。";
            QueryRequest request = new QueryRequest();
            request.setQuestion(testQuestion);
            log.info("使用测试问题: {}", testQuestion);
            return query(request);
        } catch (Exception e) {
            log.error("RAG测试查询失败: {}", e.getMessage(), e);
            return R.error(500, "测试失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/status")
    public R checkStatus() {
        log.info("检查RAG系统状态...");
        Map<String, Object> status = new HashMap<>();
        
        try {
            // 检查向量存储状态
            status.put("embeddingStoreType", embeddingStore.getClass().getSimpleName());
            
            // 检查向量存储中是否有数据
            // 使用一个简单的测试向量进行查询
            float[] testVector = new float[1536]; // 假设向量维度为1536
            Embedding testEmbedding = Embedding.from(testVector);
            
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(testEmbedding)
                    .maxResults(1)
                    .build();
            
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
            status.put("hasData", !matches.isEmpty());
            status.put("embeddingModelType", embeddingModel.getClass().getSimpleName());
            status.put("chatModelType", chatModel.getClass().getSimpleName());
            
            log.info("RAG系统状态检查完成: {}", status);
            return R.ok().put("data", status);
        } catch (Exception e) {
            log.error("RAG系统状态检查失败: {}", e.getMessage(), e);
            status.put("error", e.getMessage());
            status.put("status", "error");
            return R.error(500, "状态检查失败: " + e.getMessage()).put("data", status);
        }
    }
} 