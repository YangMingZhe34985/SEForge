package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionRenameResponse {
    private Long sessionId;
    private String newTitle;
    private String message;

    public ChatSessionRenameResponse(Long sessionId, String newTitle) {
        this.sessionId = sessionId;
        this.newTitle = newTitle;
        this.message = "会话标题已更新";
    }
}
