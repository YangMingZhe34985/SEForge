package com.ustb.smartse.api.llm.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private String question;          // 用户文字问题，可为空（如果只有图片）
    private Long agentId;              // 用户选择的智能体ID
    private List<String> imageUrls;    // 图片地址列表，可为空（如果没有上传图片）
}
