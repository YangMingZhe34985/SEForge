package com.ustb.smartse.modules.agent.toolservice;

import dev.langchain4j.agent.tool.Tool;

public interface TestCaseToolService {

    @Tool("查询天气信息，需要提供城市名称")
    String getWeather(String city);

}
