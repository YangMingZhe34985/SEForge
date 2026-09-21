package com.ustb.smartse.modules.agent.toolservice;

import dev.langchain4j.agent.tool.Tool;

public interface RequirementAnalysisToolService {

    @Tool("生成用户故事对应的用例图，输入是用户故事描述")
    String generateUseCaseDiagram(String userStory);

    @Tool("验证用例图是否符合规范，输入是PlantUML代码")
    String validateUseCaseDiagram(String plantUmlCode);

    @Tool("分析需求的完整性，检测是否存在不完整的需求点，输入是需求描述")
    String checkRequirementCompleteness(String requirements);

    @Tool("检测需求中的冲突和矛盾，输入是需求描述")
    String detectRequirementConflicts(String requirements);

    @Tool("将用户故事转换为正式的用例描述，包含前置条件、主场景、扩展场景等，输入是用户故事描述")
    String convertToFormalUseCase(String userStory);
}
