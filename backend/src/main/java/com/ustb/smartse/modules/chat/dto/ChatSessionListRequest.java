package com.ustb.smartse.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionListRequest {
    private Long userId;     // 用户ID
    private Integer page;    // 当前页码
    private Integer size;    // 每页条数
}
