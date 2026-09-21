package com.ustb.smartse.modules.agent.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SerializableChatMessage {

    private String role;
    private String content;
    private String originalQuestion; // 存储原始问题
    private String reasoningProcess; // 存储推理过程

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public void setOriginalQuestion(String originalQuestion) {
        this.originalQuestion = originalQuestion;
    }

    public String getReasoningProcess() {
        return reasoningProcess;
    }

    public void setReasoningProcess(String reasoningProcess) {
        this.reasoningProcess = reasoningProcess;
    }
}
