package com.ustb.smartse.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.sql.Timestamp;

@Data
@Getter
@Setter
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage {

    @TableId
    private Long id;

    private Long sessionId;

    private String originalQuestion;

    private String reasoningProcess;

    private String senderType; // user / system

    private Long senderId;

    private String content;

    private String contentType; // text / image / code

    private Timestamp createdAt;
    
    // 手动添加getter和setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getSenderType() {
        return senderType;
    }
    
    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }
    
    public Long getSenderId() {
        return senderId;
    }
    
    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
