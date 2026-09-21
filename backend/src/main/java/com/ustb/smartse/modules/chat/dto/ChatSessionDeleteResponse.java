package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionDeleteResponse {
    private Long sessionId;
    private String message;

    public ChatSessionDeleteResponse(Long sessionId) {
        this.sessionId = sessionId;
        this.message = "会话及消息已成功删除";
    }
}
