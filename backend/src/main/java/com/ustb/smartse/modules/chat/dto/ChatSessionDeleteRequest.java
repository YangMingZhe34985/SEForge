package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionDeleteRequest {
    private Long sessionId; // 要删除的会话ID
}
