package com.ustb.smartse.modules.agent.toolservice.impl;

import com.ustb.smartse.modules.agent.toolservice.RequirementAnalysisToolService;
import net.sourceforge.plantuml.SourceStringReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RequirementAnalysisToolServiceImpl implements RequirementAnalysisToolService {

    @Override
    public String generateUseCaseDiagram(String userStory) {

        log.debug("生成PlantUML用例图，输入的用户故事是：{}", userStory);

        // 提取用户角色、操作和业务目标
        List<String> actors = extractActors(userStory);
        List<String> actions = extractActions(userStory);

        // 构建PlantUML代码
        StringBuilder plantUmlBuilder = new StringBuilder();
        plantUmlBuilder.append("@startuml\n");
        plantUmlBuilder.append("left to right direction\n");
        plantUmlBuilder.append("skinparam packageStyle rectangle\n\n");

        // 添加系统边界
        plantUmlBuilder.append("rectangle \"系统边界\" {\n");

        // 添加用例
        for (String action : actions) {
            plantUmlBuilder.append("  usecase \"").append(action).append("\" as ").append(convertToId(action)).append("\n");
        }
        plantUmlBuilder.append("}\n\n");

        // 添加角色
        for (String actor : actors) {
            plantUmlBuilder.append("actor \"").append(actor).append("\" as ").append(convertToId(actor)).append("\n");
        }

        // 添加关系
        for (String actor : actors) {
            for (String action : actions) {
                if (userStory.contains(actor) && userStory.contains(action)) {
                    plantUmlBuilder.append(convertToId(actor)).append(" -- ").append(convertToId(action)).append("\n");
                }
            }
        }

        plantUmlBuilder.append("@enduml");

        // 返回生成的PlantUML代码和转换说明
        return "根据用户故事生成的PlantUML用例图：\n\n```\n" + plantUmlBuilder.toString() +
                "\n```\n\n转换说明：\n- 提取用户角色: " + String.join(", ", actors) +
                "\n- 提取操作/目标: " + String.join(", ", actions);
    }

    @Override
    public String validateUseCaseDiagram(String plantUmlCode) {

        log.debug("验证PlantUML用例图，输入的PlantUML代码是：{}", plantUmlCode);

        List<String> validationErrors = new ArrayList<>();

        // 基本语法验证
        if (!plantUmlCode.contains("@startuml") || !plantUmlCode.contains("@enduml")) {
            validationErrors.add("缺少 @startuml 或 @enduml 标记");
        }

        // 检查是否有角色定义
        if (!plantUmlCode.contains("actor")) {
            validationErrors.add("缺少角色(actor)定义");
        }

        // 检查是否有用例定义
        if (!plantUmlCode.contains("usecase")) {
            validationErrors.add("缺少用例(usecase)定义");
        }

        // 检查角色与用例的关联
        boolean hasAssociations = plantUmlCode.matches(".*actor[^\\n]*[\\s\\S]*usecase[^\\n]*[\\s\\S]*--.*") ||
                plantUmlCode.matches(".*usecase[^\\n]*[\\s\\S]*actor[^\\n]*[\\s\\S]*--.*");
        if (!hasAssociations) {
            validationErrors.add("角色与用例之间缺少关联");
        }

        // 尝试使用PlantUML库验证图表
        try {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            String desc = reader.generateImage(os);
            if (desc == null) {
                validationErrors.add("PlantUML图表生成失败，可能存在语法错误");
            }
        } catch (Exception e) {
            validationErrors.add("PlantUML验证异常: " + e.getMessage());
        }

        if (validationErrors.isEmpty()) {
            return "PlantUML用例图验证通过，符合规范要求。";
        } else {
            return "PlantUML用例图验证未通过，存在以下问题：\n- " + String.join("\n- ", validationErrors);
        }
    }

    @Override
    public String checkRequirementCompleteness(String requirements) {

        log.debug("检查需求完整性，输入的需求描述是：{}", requirements);

        List<String> incompletenessIssues = new ArrayList<>();

        // 检查是否缺少角色定义
        if (!requirements.matches("(?i).*作为.*")) {
            incompletenessIssues.add("缺少明确的用户角色定义");
        }

        // 检查是否缺少操作目标
        if (!requirements.matches("(?i).*能够.*|.*可以.*")) {
            incompletenessIssues.add("缺少明确的用户操作或目标");
        }

        // 检查是否缺少业务价值
        if (!requirements.matches("(?i).*以便.*|.*从而.*|.*目的是.*")) {
            incompletenessIssues.add("缺少明确的业务价值或目的说明");
        }

        // 检查是否缺少验收标准
        if (!requirements.matches("(?i).*成功条件.*|.*验收标准.*|.*完成条件.*")) {
            incompletenessIssues.add("缺少明确的验收标准或成功条件");
        }

        // 检查是否有模糊词汇
        List<String> ambiguousTerms = findAmbiguousTerms(requirements);
        if (!ambiguousTerms.isEmpty()) {
            incompletenessIssues.add("存在模糊词汇: " + String.join(", ", ambiguousTerms));
        }

        if (incompletenessIssues.isEmpty()) {
            return "需求完整性检查通过，未发现明显的缺失点。";
        } else {
            return "需求完整性检查发现以下问题：\n- " + String.join("\n- ", incompletenessIssues);
        }
    }

    @Override
    public String detectRequirementConflicts(String requirements) {

        log.debug("检测需求冲突，输入的需求描述是：{}", requirements);

        List<String> conflicts = new ArrayList<>();

        // 检查逻辑矛盾
        if (requirements.matches("(?i).*总是.*") && requirements.matches("(?i).*永不.*")) {
            conflicts.add("存在逻辑矛盾：同时使用了'总是'和'永不'");
        }

        if (requirements.matches("(?i).*必须.*") && requirements.matches("(?i).*可选.*")) {
            conflicts.add("存在逻辑矛盾：同时将功能描述为'必须'和'可选'");
        }

        // 检查数值范围冲突
        Pattern numPattern = Pattern.compile("(最小|至少|最低)([0-9]+).*(最大|至多|最高)([0-9]+)");
        Matcher matcher = numPattern.matcher(requirements);
        while (matcher.find()) {
            int min = Integer.parseInt(matcher.group(2));
            int max = Integer.parseInt(matcher.group(4));
            if (min > max) {
                conflicts.add("存在数值范围冲突：最小值" + min + "大于最大值" + max);
            }
        }

        // 检查角色职责冲突
        List<String> roles = extractActors(requirements);
        for (String role : roles) {
            Pattern rolePattern = Pattern.compile(role + ".*?(负责|可以|能够|应该)([^。，；,;.]+)");
            Matcher roleMatcher = rolePattern.matcher(requirements);
            List<String> responsibilities = new ArrayList<>();
            while (roleMatcher.find()) {
                responsibilities.add(roleMatcher.group(2).trim());
            }

            // 检查同一角色的不同职责是否冲突
            for (int i = 0; i < responsibilities.size(); i++) {
                for (int j = i + 1; j < responsibilities.size(); j++) {
                    if (areConflicting(responsibilities.get(i), responsibilities.get(j))) {
                        conflicts.add("角色'" + role + "'的职责存在冲突：'" +
                                responsibilities.get(i) + "' 与 '" +
                                responsibilities.get(j) + "'");
                    }
                }
            }
        }

        if (conflicts.isEmpty()) {
            return "需求冲突检测完成，未发现明显的矛盾点。";
        } else {
            return "需求冲突检测发现以下问题：\n- " + String.join("\n- ", conflicts);
        }
    }

    @Override
    public String convertToFormalUseCase(String userStory) {

        log.debug("将用户故事转换为正式用例，输入的用户故事是：{}", userStory);

        List<String> actors = extractActors(userStory);
        List<String> actions = extractActions(userStory);

        String mainActor = actors.isEmpty() ? "未指定角色" : actors.get(0);
        String mainAction = actions.isEmpty() ? "未指定操作" : actions.get(0);

        StringBuilder useCaseBuilder = new StringBuilder();
        useCaseBuilder.append("# 用例：").append(mainAction).append("\n\n");
        useCaseBuilder.append("## 简述\n");
        useCaseBuilder.append(userStory).append("\n\n");

        useCaseBuilder.append("## 参与者\n");
        useCaseBuilder.append("- 主要参与者：").append(mainActor).append("\n");
        if (actors.size() > 1) {
            useCaseBuilder.append("- 次要参与者：").append(String.join(", ", actors.subList(1, actors.size()))).append("\n");
        }
        useCaseBuilder.append("\n");

        useCaseBuilder.append("## 前置条件\n");
        useCaseBuilder.append("- 用户已登录系统\n");
        useCaseBuilder.append("- 用户具有执行此操作的权限\n\n");

        useCaseBuilder.append("## 主场景流程\n");
        useCaseBuilder.append("1. 用户请求执行").append(mainAction).append("\n");
        useCaseBuilder.append("2. 系统显示相关信息和操作选项\n");
        useCaseBuilder.append("3. 用户提供必要的信息\n");
        useCaseBuilder.append("4. 系统验证信息\n");
        useCaseBuilder.append("5. 系统执行操作并保存结果\n");
        useCaseBuilder.append("6. 系统确认操作成功完成\n\n");

        useCaseBuilder.append("## 扩展场景\n");
        useCaseBuilder.append("4a. 用户提供的信息无效：\n");
        useCaseBuilder.append("   1. 系统提示错误信息\n");
        useCaseBuilder.append("   2. 用户修正信息并重新提交\n\n");

        useCaseBuilder.append("## 后置条件\n");
        useCaseBuilder.append("- 系统状态已更新\n");
        useCaseBuilder.append("- 操作结果已保存\n\n");

        useCaseBuilder.append("## 验收标准\n");
        useCaseBuilder.append("- 用户能够成功完成").append(mainAction).append("\n");
        useCaseBuilder.append("- 系统能正确处理异常情况\n");
        useCaseBuilder.append("- 操作结果符合业务规则要求");

        return useCaseBuilder.toString();
    }

    // 辅助方法：提取用户角色
    private List<String> extractActors(String text) {

        List<String> actors = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?:作为|身为|用户|角色|参与者)[：:\\s]+([^，。,;\n]+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            actors.add(matcher.group(1).trim());
        }

        // 如果没有找到明确的角色定义，尝试识别常见角色名词
        if (actors.isEmpty()) {
            Pattern rolePattern = Pattern.compile("(管理员|用户|客户|访客|系统管理员|操作员|学生|教师|医生|病人)");
            Matcher roleMatcher = rolePattern.matcher(text);
            while (roleMatcher.find()) {
                actors.add(roleMatcher.group(1));
            }
        }

        return actors;
    }

    // 辅助方法：提取用户操作/目标
    private List<String> extractActions(String text) {
        List<String> actions = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?:能够|可以|希望|想要|应该)[：:\\s]+([^，。,;\n]+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            actions.add(matcher.group(1).trim());
        }

        // 如果没有找到明确的操作定义，尝试提取动词短语
        if (actions.isEmpty()) {
            Pattern actionPattern = Pattern.compile("(查询|添加|删除|修改|创建|管理|浏览|导出|导入|登录|注册|审核|发布|配置|监控|统计|分析)[^，。,;\n]{2,20}");
            Matcher actionMatcher = actionPattern.matcher(text);
            while (actionMatcher.find()) {
                actions.add(actionMatcher.group(0));
            }
        }

        return actions;
    }

    // 辅助方法：将文本转换为标识符
    private String convertToId(String text) {
        return text.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }

    // 辅助方法：查找模糊词汇
    private List<String> findAmbiguousTerms(String text) {
        List<String> ambiguousTerms = new ArrayList<>();
        String[] terms = {"可能", "也许", "大概", "一些", "很多", "经常", "偶尔", "通常", "相对", "适当", "合理", "友好", "方便"};

        for (String term : terms) {
            if (text.contains(term)) {
                ambiguousTerms.add(term);
            }
        }

        return ambiguousTerms;
    }

    // 辅助方法：判断两个职责是否冲突
    private boolean areConflicting(String resp1, String resp2) {
        // 简单实现：检查否定词
        boolean resp1Negative = resp1.contains("不") || resp1.contains("禁止") || resp1.contains("拒绝");
        boolean resp2Negative = resp2.contains("不") || resp2.contains("禁止") || resp2.contains("拒绝");

        // 如果一个是肯定一个是否定，且核心动词相似，则可能冲突
        if (resp1Negative != resp2Negative) {
            String core1 = resp1.replaceAll("不|禁止|拒绝", "").trim();
            String core2 = resp2.replaceAll("不|禁止|拒绝", "").trim();
            return core1.contains(core2) || core2.contains(core1);
        }

        return false;
    }
}
