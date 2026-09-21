package com.ustb.smartse.modules.agent.service;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.List;
import java.util.function.Consumer;

public class ReasoningTokenStream implements TokenStream {
    private final String reasoningProcess;
    private final TokenStream originalStream;
    private boolean reasoningSent = false;

    public ReasoningTokenStream(String reasoningProcess, TokenStream originalStream) {
        this.reasoningProcess = reasoningProcess;
        this.originalStream = originalStream;
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        return originalStream.onPartialResponse(token -> {
            // 仅在第一个令牌到达且尚未发送推理过程时，先发送推理过程
            if (!reasoningSent) {
                // 将推理过程分成多个小块发送，避免一次性发送大量文本
                String[] reasoningChunks = reasoningProcess.split("\n");
                for (String chunk : reasoningChunks) {
                    if (!chunk.trim().isEmpty()) {
                        partialResponseHandler.accept(chunk + "\n");
                    }
                }
                // 添加一个空行作为推理过程和正式回答的分隔
                partialResponseHandler.accept("\n");
                reasoningSent = true;
            }
            // 然后发送原始令牌
            partialResponseHandler.accept(token);
        });
    }

    // 其他方法保持委托给originalStream
    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> retrievedHandler) {
        return originalStream.onRetrieved(retrievedHandler);
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecutionHandler) {
        return originalStream.onToolExecuted(toolExecutionHandler);
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
        return originalStream.onCompleteResponse(completeResponseHandler);
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        return originalStream.onError(errorHandler);
    }

    @Override
    public TokenStream ignoreErrors() {
        return originalStream.ignoreErrors();
    }

    @Override
    public void start() {
        originalStream.start();
    }
}