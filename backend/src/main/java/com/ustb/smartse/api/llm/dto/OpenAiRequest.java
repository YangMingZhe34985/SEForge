package com.ustb.smartse.api.llm.dto;

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
    private Map<String, String> response_format;
    private int max_tokens;

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

    public void setResponse_format(Map<String, String> response_format) {
        this.response_format = response_format;
    }

    public Map<String, String> getResponse_format() {
        return response_format;
    }

    public int getMax_tokens() {
        return max_tokens;
    }

    public void setMax_tokens(int max_tokens) {
        this.max_tokens = max_tokens;
    }
}
