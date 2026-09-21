package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class ChatSessionListResponse {
    private Long sessionId;
    private String sessionTitle;
    private Integer status;
    private Timestamp createdAt;
}
