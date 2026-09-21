package com.ustb.smartse.modules.test.controller;

import com.ustb.smartse.api.llm.DeepSeekApi;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final DeepSeekApi deepSeekApi;

    @GetMapping("/deepseek")
    public String testDeepSeek(@RequestParam String question) {
        return deepSeekApi.chat(question);
    }
}
