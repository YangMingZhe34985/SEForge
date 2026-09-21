package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionRenameRequest {
    private Long sessionId;       // 要重命名的会话ID
    private String sessionTitle;  // 新标题
}
