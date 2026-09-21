package com.ustb.smartse.modules.chat.title;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;

/**
 * TitleGenerator 是一个 LLM 服务接口，
 * 用于根据用户的首次提问自动生成会话标题。
 */
public interface TitleGenerator {

    /**
     * 调用大语言模型，根据用户提问内容生成一个不超过10字的简洁中文标题。
     *
     * @param question 用户的首次提问
     * @return 自动生成的会话标题
     */
    @UserMessage("""
        请根据以下用户的提问生成一个简洁、准确的中文会话标题，最多10个字：
        "{{question}}"
        只输出标题本身，不需要任何说明或前缀。
        """)
    String generateTitle(String question);

    /**
     * 快捷构建 TitleGenerator 实例（用于手动注入时）。
     */
    static TitleGenerator create(dev.langchain4j.model.chat.ChatLanguageModel model) {
        return AiServices.builder(TitleGenerator.class)
                .chatLanguageModel(model)
                .build();
    }
}
