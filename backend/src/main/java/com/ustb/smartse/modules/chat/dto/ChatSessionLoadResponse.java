package com.ustb.smartse.modules.chat.dto;

import com.ustb.smartse.modules.chat.entity.ChatMessage;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class ChatSessionLoadResponse {
    /**
     * "sessionId": <long>,             // 会话 ID
     *               "sessionTitle": <string>,        // 会话标题
     *               "status": <int>,                 // 状态（1进行中，0已结束）
     *               "createdAt": <datetime>,         // 创建时间
     *               "chatMessages": [                // 会话中的消息列表
     *      *             {
     *      *                 "role": <string>         //发送者类型，如 ai，user
     *      *                 "content": <string>,     // 消息内容
     *      *
     *      *             },
     *      *             ...
     *      *         ]
     */
    private Long sessionId;
    private String sessionTitle;
    private Integer status;
    private Timestamp createdAt;

    private List<ChatMessage> chatMessages;
    
}
