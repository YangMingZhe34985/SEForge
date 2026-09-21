package com.ustb.smartse.modules.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ustb.smartse.modules.agent.dto.SerializableChatMessage;
import com.ustb.smartse.modules.chat.entity.ChatMessage;
import com.ustb.smartse.modules.chat.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(PersistentChatMemoryStore.class);
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatMessageMapper chatMessageMapper;

    private static final String REDIS_PREFIX = "chat:memory:";
    private static final long EXPIRE_MINUTES = 10;

    // 更新用于识别原始问题的模式
    private static final Pattern ORIGINAL_QUESTION_PATTERN = Pattern.compile(
            "<原始问题>\\s*\\n?(.*?)\\n?</原始问题>|" +    // 匹配新的 <原始问题> 标签
                    "(?:原始问题|Original Question)[:：]\\s*\"?([^\"]+?)\"?\\s*(?=\\n|$)",  // 保留原有模式作为备选
            Pattern.DOTALL);  // 添加DOTALL标志以匹配跨行内容

    // 更新用于识别推理过程的模式
    private static final Pattern REASONING_PROCESS_PATTERN = Pattern.compile(
            "<附加推理过程>\\s*\\n?(.*?)\\n?</附加推理过程>|" +  // 匹配新的 <附加推理过程> 标签
                    "\\[REASONING_START\\](.*?)\\[REASONING_END\\]",  // 保留原有模式
            Pattern.DOTALL);
    /**
     * 1）先读 Redis 缓存
     * 2）未命中时，从 MySQL 按 created_at 升序查询
     * 3）将实体转换为 Langchain4j 的 ChatMessage 并写回 Redis
     */
    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        System.out.println("📝 getMessages 调用 memoryId = " + memoryId);
        String key = REDIS_PREFIX + memoryId;

        // 检查是否为临时会话
        boolean isTemporarySession = memoryId.toString().contains("temp");
        if (isTemporarySession) {
            // 对于临时会话，直接返回空列表或从Redis中读取临时缓存
            @SuppressWarnings("unchecked")
            List<SerializableChatMessage> cached =
                    (List<SerializableChatMessage>) redisTemplate.opsForValue().get(key);

            if (cached != null && !cached.isEmpty()) {
                return cached.stream()
                        .map(msg -> {
                            if ("user".equalsIgnoreCase(msg.getRole())) {
                                return UserMessage.from(msg.getContent());
                            } else if ("system".equalsIgnoreCase(msg.getRole())) {
                                return SystemMessage.from(msg.getContent());
                            } else {
                                return new AiMessage(msg.getContent());
                            }
                        }).collect(Collectors.toList());
            }

            // 如果没有缓存，返回空列表
            return new ArrayList<>();
        }

        // 解析 sessionId
        String[] parts = memoryId.toString().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid memoryId format: expected 'userId:sessionId'");
        }
        Long sessionId = Long.parseLong(parts[1]);

        //noinspection unchecked
        List<SerializableChatMessage> cached =
                (List<SerializableChatMessage>) redisTemplate.opsForValue().get(key);

        if (cached != null && !cached.isEmpty()) {
            List<dev.langchain4j.data.message.ChatMessage> restored = cached.stream()
                    .filter(msg -> msg.getContent() != null && !msg.getContent().trim().isEmpty())
                    .map(msg -> {
                        if ("user".equalsIgnoreCase(msg.getRole())) {
                            return UserMessage.from(msg.getContent());
                        } else if ("system".equalsIgnoreCase(msg.getRole())) {
                            return SystemMessage.from(msg.getContent());
                        } else {
                            log.info("AI Message content: {}", msg.getContent());
                            // 检查是否有推理过程，如果有则添加到内容中
                            String content = msg.getContent();
                            if (msg.getReasoningProcess() != null && !msg.getReasoningProcess().isEmpty()) {
                                content = "思考过程:\n" + msg.getReasoningProcess() + "\n\n最终回答:\n" + content;
                            }
                            return new AiMessage(content);
                        }
                    }).collect(Collectors.toList());

            return restored;
        }

        // 使用正确的 sessionId 查询数据库
        List<ChatMessage> dbList = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("created_at")
        );

        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>(dbList.size());
        for (ChatMessage entity : dbList) {
            String role = entity.getSenderType();
            String content = entity.getContent();

            // 只处理非空消息
            if (content != null && !content.trim().isEmpty()) {
                if ("user".equalsIgnoreCase(role)) {
                    result.add(UserMessage.from(content));
                } else if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
                    // 为AI回复添加推理过程
                    if (entity.getReasoningProcess() != null && !entity.getReasoningProcess().isEmpty()) {
                        content = "思考过程:\n" + entity.getReasoningProcess() + "\n\n最终回答:\n" + content;
                    }
                    result.add(new AiMessage(content));
                } else if ("system".equalsIgnoreCase(role)) {
                    result.add(SystemMessage.from(content));
                }
            }
        }

        if (!result.isEmpty()) {
            // 将 langchain4j 的 ChatMessage 转为可序列化的 SerializableChatMessage
            List<SerializableChatMessage> toCache = result.stream().map(msg -> {
                        SerializableChatMessage simple = new SerializableChatMessage();

                        String content;
                        // 区分不同类型，提取文本
                        if (msg instanceof UserMessage userMsg) {
                            simple.setRole("user");
                            if (userMsg.hasSingleText()) {
                                content = userMsg.singleText();
                            } else {
                                content = userMsg.contents().stream()
                                        .filter(c -> c instanceof TextContent)
                                        .map(c -> ((TextContent) c).text())
                                        .collect(Collectors.joining("\n"));
                            }

                        } else if (msg instanceof SystemMessage sysMsg) {
                            simple.setRole("system");
                            content = sysMsg.text();

                        } else if (msg instanceof AiMessage aiMsg) {
                            simple.setRole("ai");
                            content = aiMsg.text();

                            // 从AI消息中提取推理过程
                            String reasoningContent = content;
                            String finalContent = content;

                            // 如果内容包含"思考过程"和"最终回答"的分隔符
                            if (content.contains("思考过程:") && content.contains("最终回答:")) {
                                String[] parts2 = content.split("最终回答:");
                                if (parts2.length > 1) {
                                    finalContent = parts2[1].trim();

                                    String[] reasoningParts = parts2[0].split("思考过程:");
                                    if (reasoningParts.length > 1) {
                                        reasoningContent = reasoningParts[1].trim();
                                        simple.setReasoningProcess(reasoningContent);
                                    }
                                }
                                content = finalContent;
                            }
                        } else {
                            // 如果遇到未知类型，跳过
                            return null;
                        }

                        // 只缓存非空文本
                        if (content != null) {
                            simple.setContent(content.trim());
                            return simple;
                        } else {
                            return null;
                        }

                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 需要保证从数据库读取的推理过程也被添加到缓存中
            for (int i = 0; i < toCache.size() && i < dbList.size(); i++) {
                if ("ai".equalsIgnoreCase(toCache.get(i).getRole()) &&
                        dbList.get(i).getReasoningProcess() != null) {
                    toCache.get(i).setReasoningProcess(dbList.get(i).getReasoningProcess());
                }
            }

            // 写回 Redis，只保存 SerializableChatMessage 列表
            redisTemplate.opsForValue()
                    .set(REDIS_PREFIX + memoryId, toCache, EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
        return result;
    }

    /**
     * 1）刷新 Redis 缓存
     * 2）逐条解析 ChatMessage、映射到实体并插入 MySQL
     *
     * 关键修改：在存储用户消息时，自动提取原始问题
     */
    @Override
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        System.out.println("📝 updateMessages 调用 memoryId = " + memoryId);
        String key = REDIS_PREFIX + memoryId;


        // 检查是否为临时会话（专家意见查询）
        boolean isTemporarySession = memoryId.toString().contains("temp");

        // 如果是临时会话，则只更新Redis缓存，不写入数据库
        if (isTemporarySession) {
            List<SerializableChatMessage> cacheList = new ArrayList<>();

            for (dev.langchain4j.data.message.ChatMessage message : messages) {
                SerializableChatMessage simple = new SerializableChatMessage();
                String content;

                if (message instanceof UserMessage userMessage) {
                    simple.setRole("user");
                    if (userMessage.hasSingleText()) {
                        content = userMessage.singleText();
                    } else {
                        content = userMessage.contents().stream()
                                .filter(c -> c instanceof TextContent)
                                .map(c -> ((TextContent) c).text())
                                .collect(Collectors.joining("\n"));
                    }
                } else if (message instanceof AiMessage aiMessage) {
                    simple.setRole("ai");
                    content = aiMessage.text();
                } else if (message instanceof SystemMessage systemMessage) {
                    simple.setRole("system");
                    content = systemMessage.text();
                } else {
                    continue;
                }

                if (content != null && !content.trim().isEmpty()) {
                    simple.setContent(content.trim());
                    cacheList.add(simple);
                }
            }

            // 设置较短的过期时间，例如1分钟
            if (!cacheList.isEmpty()) {
                redisTemplate.opsForValue().set(key, cacheList, 1, TimeUnit.MINUTES);
            }

            return; // 直接返回，不进行后续数据库操作
        }

        // ✅ 在循环外处理 memoryId，获取 sessionId
        String[] parts = memoryId.toString().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid memoryId format: expected 'userId:sessionId'");
        }
        Long sessionId = Long.parseLong(parts[1]);

        // 获取现有消息用于去重
        final List<ChatMessage> existingMessages = new ArrayList<>(chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("created_at")
        ));

        // 从 Redis 获取现有缓存
        @SuppressWarnings("unchecked")
        List<SerializableChatMessage> existingCache = (List<SerializableChatMessage>) redisTemplate.opsForValue().get(key);
        List<SerializableChatMessage> cacheList = new ArrayList<>();

        // 如果缓存存在，加入到新列表中
        if (existingCache != null) {
            cacheList.addAll(existingCache);
        }

        // 处理新消息
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            SerializableChatMessage simple = new SerializableChatMessage();
            String content;
            String senderType;
            String reasoningProcess = null; // 存储推理过程

            if (message instanceof UserMessage userMessage) {
                simple.setRole("user");
                senderType = "user";
                if (userMessage.hasSingleText()) {
                    content = userMessage.singleText();
                } else {
                    content = userMessage.contents().stream()
                            .filter(c -> c instanceof TextContent)
                            .map(c -> ((TextContent) c).text())
                            .collect(Collectors.joining("\n"));
                }

                // 关键修改：提取原始问题
                String originalQuestion = extractOriginalQuestion(content);
                reasoningProcess = extractReasoningProcess(content);

                if (originalQuestion != null) {
                    log.info("✅ 从构造prompt中提取原始问题: {}", originalQuestion);
                    content = originalQuestion; // 替换为原始问题
                }


                if (reasoningProcess != null) {
                    log.info("✅ 从用户消息中提取推理过程: {}", reasoningProcess);
                    simple.setReasoningProcess(reasoningProcess);
                }
            } else if (message instanceof AiMessage aiMessage) {
                simple.setRole("ai");
                senderType = "assistant";
                content = aiMessage.text();
            } else if (message instanceof SystemMessage systemMessage) {
                simple.setRole("system");
                senderType = "system";
                content = systemMessage.text();
            } else {
                // Skip unknown message types
                continue;
            }

            // 只添加非空且不重复的消息
            if (content != null && !content.trim().isEmpty()) {
                final String finalContent = content.trim();

                // 检查是否在数据库中已存在
                boolean isDuplicateInDb = existingMessages.stream()
                        .anyMatch(msg ->
                                msg.getSenderType().equals(senderType) &&
                                        msg.getContent().trim().equals(finalContent)
                        );

                // 检查是否在 Redis 缓存中已存在
                boolean isDuplicateInCache = cacheList.stream()
                        .anyMatch(msg ->
                                msg.getRole().equals(simple.getRole()) &&
                                        msg.getContent().trim().equals(finalContent)
                        );

                if (!isDuplicateInDb && !isDuplicateInCache) {
                    simple.setContent(finalContent);
                    cacheList.add(simple); // 追加到缓存列表

                    // 同时保存到数据库
                    ChatMessage dbMsg = new ChatMessage();
                    dbMsg.setSessionId(sessionId);
                    dbMsg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                    dbMsg.setContentType("text");
                    dbMsg.setSenderType(senderType);
                    dbMsg.setSenderId(0L);
                    dbMsg.setContent(finalContent);

                    // 如果是用户消息，保存原始问题
                    if (senderType.equals("user")) {
                        dbMsg.setOriginalQuestion(finalContent);
                        dbMsg.setReasoningProcess(reasoningProcess);
                    }

                    chatMessageMapper.insert(dbMsg);

                    // 添加到现有消息列表中，防止在同一批次中重复
                    existingMessages.add(dbMsg);
                } else {
                    log.debug("Skipping duplicate message: {} - {}", senderType, finalContent);
                }
            }
        }

        // 只有在有消息时才更新 Redis
        if (!cacheList.isEmpty()) {
            redisTemplate.opsForValue().set(key, cacheList, EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * 从构造的prompt中提取原始问题
     */
    private String extractOriginalQuestion(String content) {
        Matcher matcher = ORIGINAL_QUESTION_PATTERN.matcher(content);
        if (matcher.find()) {
            // 尝试获取第一个捕获组，如果为空则尝试第二个捕获组
            String result = matcher.group(1);
            if (result == null || result.trim().isEmpty()) {
                result = matcher.group(2);
            }
            return result != null ? result.trim() : null;
        }

        // 如果所有模式都不匹配，返回null
        return null;
    }

    /**
     * 提取推理过程
     */
    private String extractReasoningProcess(String content) {
        Matcher matcher = REASONING_PROCESS_PATTERN.matcher(content);
        if (matcher.find()) {
            // 尝试获取第一个捕获组，如果为空则尝试第二个捕获组
            String result = matcher.group(1);
            if (result == null || result.trim().isEmpty()) {
                result = matcher.group(2);
            }
            return result != null ? result.trim() : null;
        }

        return null;
    }
    /**
     * 同步清理：Redis 缓存 + MySQL 记录
     */
    @Override
    public void deleteMessages(Object memoryId) {
        System.out.println("📝 deleteMessages 调用 memoryId = " + memoryId);
        String key = REDIS_PREFIX + memoryId;
        redisTemplate.delete(key);

        // 临时会话只需删除缓存，不涉及数据库操作
        if (memoryId.toString().contains("temp")) {
            return;
        }

        // 解析 memoryId 获取 sessionId（格式：userId:sessionId）
        String[] parts = memoryId.toString().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid memoryId format, expected 'userId:sessionId'");
        }

        Long sessionId;
        try {
            sessionId = Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid sessionId in memoryId: " + memoryId);
        }

        chatMessageMapper.delete(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
        );
    }
}