package com.ustb.smartse.modules.agent.toolservice.impl;

import com.ustb.smartse.modules.agent.toolservice.GeneralToolService;
import org.springframework.stereotype.Service;

@Service
public class GeneralToolServiceImpl implements GeneralToolService {

    @Override
    public String getWeather(String city) {
        // 实现天气查询逻辑
        return "城市 " + city + " 的天气：晴天，温度25℃";
    }

    @Override
    public String searchDocuments(String keywords) {
        // 实现文档搜索逻辑
        return "关于 '" + keywords + "' 的搜索结果：找到5条相关文档";
    }

    @Override
    public String getUserInfo(String userId) {
        // 实现用户信息获取逻辑
        return "用户ID " + userId + " 的信息：姓名: 张三, 年龄: 28";
    }
}
