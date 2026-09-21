package com.ustb.smartse.modules.chat.controller;

import com.ustb.smartse.modules.agent.config.AgentConfig;
import com.ustb.smartse.modules.chat.dto.TitleGenerationResult;
import com.ustb.smartse.modules.chat.service.ChatSessionService;
import com.ustb.smartse.modules.agent.service.AgentRouterService;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // 注入多个智能体配置
    @Autowired
    AgentConfig.GeneralAssistant generalAssistant;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private AgentRouterService agentRouterService;

    /**
     * 有记忆的流式聊天接口（流式响应）
     * 用于逐词或逐句输出模型响应，提高用户体验
     *
     * @param message 用户输入内容
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return Flux<String> 模型返回的流式响应
     */
    @RequestMapping(value = "/stream_chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> memoryStreamChat(@RequestParam(defaultValue = "你是谁") String message,
                                         @RequestParam Long userId,
                                         @RequestParam Long sessionId) {
        // 参数验证
        if (userId == null || sessionId == null) {
            return Flux.error(new IllegalArgumentException("userId 和 sessionId 不能为空"));
        }

        String memoryId = userId + ":" + sessionId;

        // 调用智能体路由服务
        TokenStream stream = agentRouterService.routeToAgent(memoryId, message);;

        // 判断是否首次提问（延迟执行，避免阻塞响应）
        boolean isFirstMessage = chatSessionService.isFirstMessage(sessionId);
        System.out.println("isFirstMessage:" + isFirstMessage);

        return Flux.create(sink -> {
            stream
                    .onPartialResponse(chunk -> {
                        sink.next(chunk);
                    })
                    .onCompleteResponse(c -> {
                        sink.next("[DONE]"); // 先发送结束标志

                        // 响应完成后再进行首次标题生成尝试
                        if (isFirstMessage) {
                            TitleGenerationResult titleResult = chatSessionService.tryGenerateTitleIfFirstMessage(sessionId, message);
                            if (titleResult.isSuccess()) {
                                log.info("✅ [流式] 标题生成成功：{}", titleResult.getTitle());
                                // 发送标题更新事件
                                sink.next("[TITLE]"+titleResult.getTitle());
                            } else {
                                log.warn("⚠️ [流式] 标题生成失败：{}", titleResult.getErrorMessage());
                            }
                        }

                        sink.complete();
                    })
                    .onError(sink::error)
                    .start();
        });
    }

    /**
     * 预留接口：检索增强（RAG）
     */
    private String enrichPromptViaRAG(String question) {
        // TODO: 未来接入RAG系统
        return "";
    }
}
