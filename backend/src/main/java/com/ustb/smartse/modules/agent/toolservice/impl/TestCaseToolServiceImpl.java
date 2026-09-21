package com.ustb.smartse.modules.agent.toolservice.impl;

import com.ustb.smartse.modules.agent.toolservice.TestCaseToolService;
import org.springframework.stereotype.Service;

@Service
public class TestCaseToolServiceImpl implements TestCaseToolService {
    @Override
    public String getWeather(String city) {
        // 实现天气查询逻辑
        return "城市 " + city + " 的天气：晴天，温度25℃";
    }
}
