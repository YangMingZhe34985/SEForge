package com.ustb.smartse.modules.agent.toolservice;

import dev.langchain4j.agent.tool.Tool;

public interface GeneralToolService {

    @Tool("查询天气信息，需要提供城市名称")
    String getWeather(String city);

    @Tool("搜索相关文档，需要提供关键词")
    String searchDocuments(String keywords);

    @Tool("根据ID获取用户信息")
    String getUserInfo(String userId);
}