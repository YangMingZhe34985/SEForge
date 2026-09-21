package com.ustb.smartse.modules.test.controller;

import com.ustb.smartse.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Result<String> home() {
        return Result.success("Hello, SmartSE系统已启动！");
    }
}
