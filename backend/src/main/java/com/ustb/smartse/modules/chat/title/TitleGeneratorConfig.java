package com.ustb.smartse.modules.chat.title;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于向 Spring 容器注册 TitleGenerator Bean。
 */
@Configuration
public class TitleGeneratorConfig {

    @Bean
    public TitleGenerator titleGenerator(ChatLanguageModel deepseekChatModel) {
        return TitleGenerator.create(deepseekChatModel);
    }
}
