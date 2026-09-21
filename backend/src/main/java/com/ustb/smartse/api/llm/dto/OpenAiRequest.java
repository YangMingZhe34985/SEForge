package com.ustb.smartse.api.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@Accessors(chain = true)
public class OpenAiRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private Double temperature;

    // 线上协议字段为 snake_case，通过 @JsonProperty 保持 JSON 契约不变
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;

    @JsonProperty("max_tokens")
    private int maxTokens;

    // 手动添加setter方法，防止Lombok注解处理器问题
    public void setModel(String model) {
        this.model = model;
    }

    public void setMessages(List<OpenAiMessage> messages) {
        this.messages = messages;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public void setResponseFormat(Map<String, String> responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Map<String, String> getResponseFormat() {
        return responseFormat;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
