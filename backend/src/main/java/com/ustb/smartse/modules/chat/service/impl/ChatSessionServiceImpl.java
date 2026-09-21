package com.ustb.smartse.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ustb.smartse.modules.chat.dto.ChatSessionLoadResponse;
import com.ustb.smartse.modules.chat.dto.TitleGenerationResult;
import com.ustb.smartse.modules.chat.entity.ChatMessage;
import com.ustb.smartse.modules.chat.entity.ChatSession;
import com.ustb.smartse.modules.chat.mapper.ChatMessageMapper;
import com.ustb.smartse.modules.chat.mapper.ChatSessionMapper;
import com.ustb.smartse.modules.chat.service.ChatSessionService;
import com.ustb.smartse.modules.chat.title.TitleGenerator;
import com.ustb.smartse.modules.user.entity.User;
import com.ustb.smartse.modules.user.mapper.UserMapper;
// import org.springframework.data.redis.core.RedisTemplate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final TitleGenerator titleGenerator;
    private final ChatSessionMapper chatSessionMapper;
    private static final Logger log = LoggerFactory.getLogger(ChatSessionServiceImpl.class);


    @Override
    public Long createSession(Long userId, String title) {
        // 检查用户是否存在
        if (!userExists(userId)) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionTitle(title != null ? title : "新会话");
        session.setStatus(1); // 可定义 1 为进行中
        session.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        session.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        this.save(session);
        return session.getId();
    }

    private boolean userExists(Long userId) {
        QueryWrapper<User> chatSessionQueryWrapper = new QueryWrapper<>();
        chatSessionQueryWrapper.eq("id", userId);
        return userMapper.exists(chatSessionQueryWrapper);
    }

    @Override
    public boolean renameSession(Long sessionId, String newTitle) {
        ChatSession session = this.getById(sessionId);
        if (session == null) return false;
        session.setSessionTitle(newTitle);
        session.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return this.updateById(session);
    }

    @Override
    public boolean deleteSessionCascade(Long sessionId) {
        // 先删消息，再删会话
        chatMessageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", sessionId));
        return this.removeById(sessionId);
    }

    @Override
    public ChatSessionLoadResponse loadSessionById(Long sessionId) {
        ChatSession session = this.getById(sessionId);
        if (session == null) return null;

        ChatSessionLoadResponse response = new ChatSessionLoadResponse();
        response.setSessionId(session.getId());
        response.setSessionTitle(session.getSessionTitle());
        response.setStatus(session.getStatus());
        response.setCreatedAt(session.getCreatedAt());

        Long userId = session.getUserId();
        // 获取里面的数据
        List<ChatMessage> chatMessages = chatMessageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", sessionId));
        response.setChatMessages(chatMessages);

        return response;
    }

    @Override
    public boolean isFirstMessage(Long sessionId) {
        return chatMessageMapper.selectCount(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .eq("sender_type", "user")  // 只统计用户的发言
        ) == 1;
    }



    @Override
    public TitleGenerationResult tryGenerateTitleIfFirstMessage(Long sessionId, String message) {
        boolean isFirst = isFirstMessage(sessionId);

        if (!isFirst) return TitleGenerationResult.notFirst();

        try {
            String title = titleGenerator.generateTitle(message);
            ChatSession session = chatSessionMapper.selectById(sessionId);
            if (session != null) {
                session.setSessionTitle(title);
                chatSessionMapper.updateById(session);
                log.info("🎯 会话 {} 设置标题成功：{}", sessionId, title);
                return TitleGenerationResult.success(title);
            } else {
                return TitleGenerationResult.failure("未找到对应会话记录");
            }
        } catch (Exception e) {
            log.error("❌ 生成会话标题失败: {}", e.getMessage(), e);
            return TitleGenerationResult.failure(e.getMessage());
        }
    }



}
