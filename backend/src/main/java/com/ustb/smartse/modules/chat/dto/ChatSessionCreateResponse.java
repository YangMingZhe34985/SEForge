package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionCreateResponse {
    private Long sessionId;
    private String message;

    public ChatSessionCreateResponse(Long sessionId) {
        this.sessionId = sessionId;
        this.message = "新会话创建成功";
    }
}
