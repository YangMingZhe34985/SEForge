package com.ustb.smartse.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TitleGenerationResult {
    private boolean wasFirstMessage;   // 是否为首次提问
    private boolean success;           // 是否成功生成并保存标题
    private String title;              // 若生成成功，返回标题，否则为 null
    private String errorMessage;       // 若失败，返回错误信息，否则为 null

    public static TitleGenerationResult notFirst() {
        return new TitleGenerationResult(false, false, null, null);
    }

    public static TitleGenerationResult success(String title) {
        return new TitleGenerationResult(true, true, title, null);
    }

    public static TitleGenerationResult failure(String errorMessage) {
        return new TitleGenerationResult(true, false, null, errorMessage);
    }
}
