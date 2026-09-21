package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionCreateRequest {
    private Long userId;          // 用户ID
    private String sessionTitle;  // 新会话标题（可选）
}
