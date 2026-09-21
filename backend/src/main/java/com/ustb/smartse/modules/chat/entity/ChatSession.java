package com.ustb.smartse.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.sql.Timestamp;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId
    private Long id;

    private Long userId;

    private Long agentId;

    private String sessionTitle;

    private Integer status;

    private Timestamp createdAt;

    private Timestamp updatedAt;
}
