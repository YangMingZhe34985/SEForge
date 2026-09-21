package com.ustb.smartse.modules.agent.toolservice.impl;

import com.ustb.smartse.modules.agent.toolservice.ConceptExplanationToolService;
import org.springframework.stereotype.Service;

@Service
public class ConceptExplanationToolServiceImpl implements ConceptExplanationToolService {
    @Override
    public String getWeather(String city) {
        // 实现天气查询逻辑
        return "城市 " + city + " 的天气：晴天，温度25℃";
    }
}
