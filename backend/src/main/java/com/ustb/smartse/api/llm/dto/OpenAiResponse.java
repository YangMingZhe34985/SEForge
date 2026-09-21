package com.ustb.smartse.api.llm.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Getter
@Setter
@Accessors(chain = true)
public class OpenAiResponse {
    private List<Choice> choices;

    // 手动添加getter方法以确保可访问性
    public List<Choice> getChoices() {
        return choices;
    }

    @Data
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Choice {
        private Message message;

        // 手动添加getter方法以确保可访问性
        public Message getMessage() {
            return message;
        }
    }

    @Data
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Message {
        private String role;
        private String content;

        // 手动添加getter方法以确保可访问性
        public String getContent() {
            return content;
        }
    }
}
