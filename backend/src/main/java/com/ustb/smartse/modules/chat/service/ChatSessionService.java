package com.ustb.smartse.modules.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ustb.smartse.modules.chat.dto.ChatSessionLoadResponse;
import com.ustb.smartse.modules.chat.dto.TitleGenerationResult;
import com.ustb.smartse.modules.chat.entity.ChatSession;

public interface ChatSessionService extends IService<ChatSession> {
    ChatSessionLoadResponse loadSessionById(Long sessionId);
    Long createSession(Long userId, String title);
    boolean renameSession(Long sessionId, String newTitle);
    boolean deleteSessionCascade(Long sessionId);
    boolean isFirstMessage(Long sessionId);
    TitleGenerationResult tryGenerateTitleIfFirstMessage(Long sessionId, String message);
}
