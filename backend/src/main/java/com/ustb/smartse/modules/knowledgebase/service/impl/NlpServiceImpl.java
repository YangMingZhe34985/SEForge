package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.dependency.IDependencyParser;
import com.hankcs.hanlp.model.perceptron.PerceptronLexicalAnalyzer;
import com.hankcs.hanlp.corpus.dependency.CoNll.CoNLLSentence;
import com.hankcs.hanlp.corpus.dependency.CoNll.CoNLLWord;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.seg.Segment;
import com.ustb.smartse.modules.knowledgebase.service.NlpService;
import com.ustb.smartse.api.llm.DeepSeekApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * NLP服务实现类
 */
@Slf4j
@Service
@Primary
public class NlpServiceImpl implements NlpService {
    
    @Autowired
    private DeepSeekApi deepSeekApi;
    
    // 软件工程领域分类
    private static final Map<String, String> DOMAIN_CATEGORIES = new HashMap<>();
    
    static {
        // 初始化领域分类
        DOMAIN_CATEGORIES.put("模式", "DESIGN_PATTERN");
        DOMAIN_CATEGORIES.put("设计模式", "DESIGN_PATTERN");
        DOMAIN_CATEGORIES.put("原则", "DESIGN_PRINCIPLE");
        DOMAIN_CATEGORIES.put("设计原则", "DESIGN_PRINCIPLE");
        DOMAIN_CATEGORIES.put("架构", "ARCHITECTURE");
        DOMAIN_CATEGORIES.put("过程", "PROCESS");
        DOMAIN_CATEGORIES.put("方法", "METHOD");
        DOMAIN_CATEGORIES.put("编程", "PROGRAMMING");
        DOMAIN_CATEGORIES.put("测试", "TESTING");
        DOMAIN_CATEGORIES.put("需求", "REQUIREMENT");
        DOMAIN_CATEGORIES.put("UML", "MODELING");
        DOMAIN_CATEGORIES.put("图", "DIAGRAM");
        DOMAIN_CATEGORIES.put("算法", "ALGORITHM");
        DOMAIN_CATEGORIES.put("数据结构", "DATA_STRUCTURE");
    }

    // 软件工程领域常见关系类型映射
    private static final Map<String, String> SE_RELATION_TYPES = new HashMap<>();
    
    static {
        // 初始化软件工程领域关系映射
        // 类与接口关系
        SE_RELATION_TYPES.put("继承", "INHERITS_FROM");
        SE_RELATION_TYPES.put("实现", "IMPLEMENTS");
        SE_RELATION_TYPES.put("扩展", "EXTENDS");
        
        // 包含和组成关系
        SE_RELATION_TYPES.put("包含", "CONTAINS");
        SE_RELATION_TYPES.put("组成", "COMPOSED_OF");
        SE_RELATION_TYPES.put("由", "COMPOSED_OF");
        
        // 使用和依赖关系
        SE_RELATION_TYPES.put("使用", "USES");
        SE_RELATION_TYPES.put("依赖", "DEPENDS_ON");
        SE_RELATION_TYPES.put("需要", "DEPENDS_ON");
        
        // 创建和定义关系
        SE_RELATION_TYPES.put("创建", "CREATES");
        SE_RELATION_TYPES.put("定义", "DEFINES");
        
        // 遵循和解决关系
        SE_RELATION_TYPES.put("遵循", "FOLLOWS");
        SE_RELATION_TYPES.put("解决", "SOLVES");
        
        // 其他常见关系
        SE_RELATION_TYPES.put("适用于", "APPLIES_TO");
        SE_RELATION_TYPES.put("验证", "VALIDATES");
        SE_RELATION_TYPES.put("测试", "VERIFIES");
        SE_RELATION_TYPES.put("描述", "DESCRIBES");
        SE_RELATION_TYPES.put("规范", "SPECIFIES");
        SE_RELATION_TYPES.put("应用", "APPLIES_TO");
        SE_RELATION_TYPES.put("相关", "RELATED_TO");
        SE_RELATION_TYPES.put("依赖", "DEPENDS_ON");
        SE_RELATION_TYPES.put("关联", "ASSOCIATES_WITH");
        SE_RELATION_TYPES.put("聚合", "AGGREGATES");
        SE_RELATION_TYPES.put("扩展", "EXTENDS");
        SE_RELATION_TYPES.put("属于", "BELONGS_TO");
        SE_RELATION_TYPES.put("是", "IS_A");
        SE_RELATION_TYPES.put("遵循", "FOLLOWS");
        
        // 新增软件工程领域关系映射
        SE_RELATION_TYPES.put("定义", "DEFINES");
        SE_RELATION_TYPES.put("描述", "DESCRIBES");
        SE_RELATION_TYPES.put("创建", "CREATES");
        SE_RELATION_TYPES.put("生成", "GENERATES");
        SE_RELATION_TYPES.put("调用", "CALLS");
        SE_RELATION_TYPES.put("引用", "REFERENCES");
        SE_RELATION_TYPES.put("导入", "IMPORTS");
        SE_RELATION_TYPES.put("导出", "EXPORTS");
        SE_RELATION_TYPES.put("测试", "TESTS");
        SE_RELATION_TYPES.put("验证", "VERIFIES");
        SE_RELATION_TYPES.put("配置", "CONFIGURES");
        SE_RELATION_TYPES.put("部署", "DEPLOYS");
        SE_RELATION_TYPES.put("监控", "MONITORS");
        SE_RELATION_TYPES.put("优化", "OPTIMIZES");
        SE_RELATION_TYPES.put("重构", "REFACTORS");
        SE_RELATION_TYPES.put("替代", "ALTERNATIVE_TO");
        SE_RELATION_TYPES.put("协作", "COLLABORATES_WITH");
        SE_RELATION_TYPES.put("冲突", "CONFLICTS_WITH");
        SE_RELATION_TYPES.put("通信", "COMMUNICATES_WITH");
        SE_RELATION_TYPES.put("先于", "PRECEDES");
        SE_RELATION_TYPES.put("迭代", "ITERATES");
        SE_RELATION_TYPES.put("细化", "REFINES");
        SE_RELATION_TYPES.put("满足", "SATISFIES");
        // 增加更多软件工程领域关系词汇
        SE_RELATION_TYPES.put("派生", "DERIVES_FROM");
        SE_RELATION_TYPES.put("演化", "EVOLVES_TO");
        SE_RELATION_TYPES.put("组织", "ORGANIZES");
        SE_RELATION_TYPES.put("分解", "DECOMPOSES");
        SE_RELATION_TYPES.put("封装", "ENCAPSULATES");
        SE_RELATION_TYPES.put("抽象", "ABSTRACTS");
        SE_RELATION_TYPES.put("表示", "REPRESENTS");
        SE_RELATION_TYPES.put("支持", "SUPPORTS");
        SE_RELATION_TYPES.put("限制", "CONSTRAINS");
        SE_RELATION_TYPES.put("连接", "CONNECTS_TO");
        SE_RELATION_TYPES.put("触发", "TRIGGERS");
        SE_RELATION_TYPES.put("产生", "PRODUCES");
        SE_RELATION_TYPES.put("消费", "CONSUMES");
        SE_RELATION_TYPES.put("分析", "ANALYZES");
        SE_RELATION_TYPES.put("设计", "DESIGNS");
        SE_RELATION_TYPES.put("管理", "MANAGES");
        SE_RELATION_TYPES.put("维护", "MAINTAINS");
    }
    
    // 自定义分词器
    private Segment customSegment;
    
    // 依存句法分析器
    private PerceptronLexicalAnalyzer lexicalAnalyzer;

    @PostConstruct
    public void init() {
        try {
            log.info("初始化DeepSeek+HanLP的NLP管道，启用为主要NLP实现...");
            
            // 设置HanLP配置
            HanLP.Config.ShowTermNature = false; // 关闭词性显示，减少内存占用
            
            // 加载软件工程领域词典
            loadDomainDictionary();
            
            // 初始化自定义分词器
            customSegment = HanLP.newSegment()
                    .enableCustomDictionary(true)  // 启用自定义词典
                    .enableNameRecognize(true)     // 启用人名识别
                    .enableOrganizationRecognize(true)  // 启用机构名识别
                    .enablePartOfSpeechTagging(true);   // 启用词性标注
            
            log.info("自定义分词器初始化完成");
            
            // 不初始化依存句法分析器，避免加载本地模型
            lexicalAnalyzer = null;
            
            log.info("NLP管道初始化完成，使用HanLP portable版本，不使用依存句法分析");
        } catch (Exception e) {
            log.error("初始化NLP管道失败", e);
        }
    }
    
    /**
     * 加载软件工程领域词典
     */
    private void loadDomainDictionary() {
        try {
            log.info("开始加载软件工程领域词典...");
            ClassPathResource resource = new ClassPathResource("dict/se_domain_dict.txt");
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    // 跳过注释行和空行
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // 词条格式：词语 词性
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        String word = parts[0];
                        String nature = parts[1];
                        
                        // 添加到自定义词典
                        CustomDictionary.add(word, nature);
                        count++;
                    }
                }
                
                log.info("软件工程领域词典加载完成，共加载 {} 个词条", count);
            }
        } catch (Exception e) {
            log.error("加载软件工程领域词典失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> extractConcepts(String text, int limit) {
        try {
            // 参数验证
            if (text == null || text.trim().isEmpty()) {
                log.debug("输入文本为空，无法提取概念");
                return Collections.emptyList();
            }
            
            // 限制处理文本大小
            String processText = text;
            if (processText.length() > 10000) {
                log.info("文本过长 ({} 字符)，截取前10000字符进行处理", processText.length());
                processText = processText.substring(0, 10000);
            }
            
            // 1. 使用HanLP提取关键词
            List<String> keywords = HanLP.extractKeyword(processText, 20);
            log.debug("HanLP关键词提取完成，找到 {} 个关键词", keywords.size());
            
            // 2. 使用自定义分词器提取领域术语
            List<Term> terms = customSegment.seg(processText);
            Set<String> domainTerms = new HashSet<>();
            Map<String, Integer> phraseFrequency = new HashMap<>();
            
            for (int i = 0; i < terms.size(); i++) {
                Term term = terms.get(i);
                String word = term.word;
                String nature = term.nature.toString();
                
                // 检查单词是否为领域术语
                if (word.length() > 1 && !isStopWord(word) && isDomainTerm(word, nature)) {
                    domainTerms.add(word);
                    phraseFrequency.put(word, phraseFrequency.getOrDefault(word, 0) + 1);
                    
                    // 尝试识别多词术语
                    StringBuilder phraseBuilder = new StringBuilder(word);
                    int j = i + 1;
                    int phraseLength = 1;
                    
                    // 最多向后看3个词，构建可能的术语短语
                    while (j < terms.size() && phraseLength < 4) {
                        Term nextTerm = terms.get(j);
                        
                        // 如果下一个词是名词、动词或形容词，可能是术语的一部分
                        if (nextTerm.nature.toString().startsWith("n") || 
                            nextTerm.nature.toString().startsWith("v") ||
                            nextTerm.nature.toString().startsWith("a")) {
                            
                            phraseBuilder.append(nextTerm.word);
                            String phrase = phraseBuilder.toString();
                            
                            // 检查组合后的短语是否是领域术语
                            if (isDomainTerm(phrase, "n")) {
                                domainTerms.add(phrase);
                                phraseFrequency.put(phrase, phraseFrequency.getOrDefault(phrase, 0) + 1);
                            }
                            
                            phraseLength++;
                            j++;
                        } else {
                            break;
                        }
                    }
                }
            }
            
            log.debug("自定义分词器提取完成，找到 {} 个领域术语", domainTerms.size());
            
            // 3. 尝试使用DeepSeek提取额外实体
            List<String> entities = new ArrayList<>();
            try {
                String entitiesJson = extractWithDeepSeek(processText, "entity");
                if (entitiesJson != null && !entitiesJson.isEmpty()) {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, List<String>> entityMap = mapper.readValue(entitiesJson, 
                            new TypeReference<Map<String, List<String>>>() {});
                    
                    // 合并所有类型的实体
                    entityMap.values().forEach(entities::addAll);
                    log.debug("DeepSeek实体提取完成，找到 {} 个实体", entities.size());
                }
            } catch (Exception e) {
                log.warn("DeepSeek实体提取失败: {}", e.getMessage());
            }
            
            // 4. 合并结果并去重
            Set<String> concepts = new HashSet<>();
            concepts.addAll(keywords);
            concepts.addAll(domainTerms);
            concepts.addAll(entities);
            
            // 5. 对结果进行排序和限制数量
            List<String> result = new ArrayList<>(concepts);
            
            // 优先保留领域术语，根据术语频率和长度排序
            result.sort((a, b) -> {
                boolean aIsDomain = domainTerms.contains(a);
                boolean bIsDomain = domainTerms.contains(b);
                
                if (aIsDomain && !bIsDomain) {
                    return -1;
                } else if (!aIsDomain && bIsDomain) {
                    return 1;
                } else if (aIsDomain && bIsDomain) {
                    // 如果都是领域术语，优先考虑频率
                    int freqCompare = Integer.compare(
                            phraseFrequency.getOrDefault(b, 0), 
                            phraseFrequency.getOrDefault(a, 0));
                    
                    if (freqCompare != 0) {
                        return freqCompare;
                    }
                    
                    // 频率相同，考虑长度
                    return Integer.compare(b.length(), a.length());
                } else {
                    // 都不是领域术语，按长度排序
                    return Integer.compare(b.length(), a.length());
                }
            });
            
            // 限制返回数量
            if (result.size() > limit) {
                result = result.subList(0, limit);
            }
            
            log.info("概念提取完成，共提取 {} 个概念", result.size());
            return result;
        } catch (Exception e) {
            log.error("提取概念过程中发生异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 判断词语是否为领域术语
     */
    private boolean isDomainTerm(String word, String nature) {
        // 检查是否在自定义词典中
        if (CustomDictionary.contains(word)) {
            return true;
        }
        
        // 检查是否包含领域关键词
        for (String key : DOMAIN_CATEGORIES.keySet()) {
            if (word.contains(key)) {
                return true;
            }
        }
        
        // 检查是否符合软件工程术语模式
        if (word.contains("模式") || word.contains("原则") || word.contains("架构") ||
            word.contains("设计") || word.contains("开发") || word.contains("测试") ||
            word.contains("编程") || word.contains("框架") || word.contains("驱动") ||
            word.contains("模型") || word.contains("系统") || word.contains("服务") ||
            word.contains("组件") || word.contains("接口") || word.contains("类") ||
            word.contains("方法") || word.contains("函数") || word.contains("对象") ||
            word.contains("代码") || word.contains("算法") || word.contains("数据") ||
            word.contains("安全") || word.contains("部署") || word.contains("持续") ||
            word.contains("集成") || word.contains("交付") || word.contains("敏捷")) {
            return true;
        }
        
        // 检查词性是否为名词
        return nature.startsWith("n");
    }
    
    /**
     * 判断是否为停用词
     */
    private boolean isStopWord(String word) {
        // 简单的停用词列表
        String[] stopWords = {"的", "了", "和", "与", "或", "是", "在", "有", "这", "那", "如果", "因为", "所以"};
        for (String stopWord : stopWords) {
            if (stopWord.equals(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用DeepSeek模型提取实体和关系
     * @param text 文本内容
     * @param task 任务类型："entity"或"relation"
     * @return 提取结果，格式取决于任务类型
     */
    private String extractWithDeepSeek(String text, String task) {
        try {
            String prompt = "";
            if ("entity".equals(task)) {
                prompt = "你是一个软件工程领域的专家，请从以下文本中提取所有软件工程相关的实体，并按照以下格式输出（仅输出JSON格式的结果，不要有其他解释或格式化，不要使用反引号包围）：\n"
                    + "{\n"
                    + "  \"DESIGN_PATTERN\": [\"设计模式1\", \"设计模式2\"],\n"
                    + "  \"DESIGN_PRINCIPLE\": [\"设计原则1\", \"设计原则2\"],\n"
                    + "  \"CONCEPT\": [\"概念1\", \"概念2\"],\n"
                    + "  \"ARCHITECTURE\": [\"架构1\", \"架构2\"],\n"
                    + "  \"METHOD\": [\"方法1\", \"方法2\"]\n"
                    + "}\n\n"
                    + "文本内容：\n" + text;
            } else if ("relation".equals(task)) {
                // 使用few-shot提示工程技术来提高关系提取的效果
                prompt = "你是一个软件工程领域的专家，请从以下文本中提取实体之间的关系。你需要识别文本中的实体对及其关系类型，并以JSON格式输出。\n\n"
                    + "请严格使用以下关系类型（确保使用特定的关系而不是笼统的RELATED_TO）：\n"
                    + "1. IS_A：表示一个实体是另一个实体的一种类型或子类\n"
                    + "2. PART_OF：表示一个实体是另一个实体的组成部分\n"
                    + "3. IMPLEMENTS：表示一个实体实现了另一个实体定义的接口或规范\n"
                    + "4. USES：表示一个实体使用另一个实体作为工具或资源\n"
                    + "5. DEPENDS_ON：表示一个实体依赖于另一个实体才能正常工作\n"
                    + "6. INHERITS_FROM：表示一个实体继承了另一个实体的属性或方法\n"
                    + "7. CONTAINS：表示一个实体包含另一个实体\n"
                    + "8. EXTENDS：表示一个实体扩展了另一个实体的功能\n"
                    + "9. FOLLOWS：表示一个实体遵循另一个实体规定的原则或模式\n"
                    + "10. SOLVES：表示一个实体解决了另一个实体表示的问题\n"
                    + "11. CREATES：表示一个实体创建另一个实体\n"
                    + "12. DEFINES：表示一个实体定义了另一个实体的规范或特性\n"
                    + "13. APPLIES_TO：表示一个实体适用于另一个实体\n"
                    + "14. COMPOSED_OF：表示一个实体由多个其他实体组成\n"
                    + "15. ACHIEVES：表示一个实体达成了另一个实体表示的目标\n"
                    + "16. RELATED_TO：仅在以上关系都不适用时使用\n\n"
                    
                    + "下面是一些例子：\n\n"
                    
                    + "例子1：\n"
                    + "文本：单例模式是一种创建型设计模式，它保证一个类只有一个实例，并提供一个全局访问点。\n"
                    + "输出：[\n"
                    + "  {\"sourceEntity\": \"单例模式\", \"relationType\": \"IS_A\", \"targetEntity\": \"创建型设计模式\"}\n"
                    + "]\n\n"
                    
                    + "例子2：\n"
                    + "文本：MVC架构由模型、视图和控制器三部分组成。\n"
                    + "输出：[\n"
                    + "  {\"sourceEntity\": \"MVC架构\", \"relationType\": \"COMPOSED_OF\", \"targetEntity\": \"模型\"},\n"
                    + "  {\"sourceEntity\": \"MVC架构\", \"relationType\": \"COMPOSED_OF\", \"targetEntity\": \"视图\"},\n"
                    + "  {\"sourceEntity\": \"MVC架构\", \"relationType\": \"COMPOSED_OF\", \"targetEntity\": \"控制器\"}\n"
                    + "]\n\n"
                    
                    + "例子3：\n"
                    + "文本：工厂方法模式解决了简单工厂模式中违背开闭原则的问题。\n"
                    + "输出：[\n"
                    + "  {\"sourceEntity\": \"工厂方法模式\", \"relationType\": \"SOLVES\", \"targetEntity\": \"简单工厂模式中违背开闭原则的问题\"}\n"
                    + "]\n\n"
                    
                    + "例子4：\n"
                    + "文本：Java集合框架中，ArrayList和LinkedList都实现了List接口。\n"
                    + "输出：[\n"
                    + "  {\"sourceEntity\": \"ArrayList\", \"relationType\": \"IMPLEMENTS\", \"targetEntity\": \"List接口\"},\n"
                    + "  {\"sourceEntity\": \"LinkedList\", \"relationType\": \"IMPLEMENTS\", \"targetEntity\": \"List接口\"}\n"
                    + "]\n\n"
                    
                    + "例子5：\n"
                    + "文本：Spring框架依赖于Java反射机制来实现依赖注入。\n"
                    + "输出：[\n"
                    + "  {\"sourceEntity\": \"Spring框架\", \"relationType\": \"DEPENDS_ON\", \"targetEntity\": \"Java反射机制\"},\n"
                    + "  {\"sourceEntity\": \"Spring框架\", \"relationType\": \"IMPLEMENTS\", \"targetEntity\": \"依赖注入\"}\n"
                    + "]\n\n"
                    
                    + "现在，请从以下文本中提取实体关系：\n" + text;
            } else if ("topic".equals(task)) {
                prompt = "你是一个软件工程领域的专家，请从以下文本中提取主要话题或主题，最多列出5个。按照重要性降序排列，并用JSON数组格式返回。例如：[\"主题1\", \"主题2\", \"主题3\"]\n\n文本内容：\n" + text;
            } else if ("keyword".equals(task)) {
                prompt = "你是一个软件工程领域的专家，请从以下文本中提取关键词，最多列出10个。按照重要性降序排列，并用JSON数组格式返回。例如：[\"关键词1\", \"关键词2\", \"关键词3\"]\n\n文本内容：\n" + text;
            } else if ("summary".equals(task)) {
                prompt = "你是一个软件工程领域的专家，请对以下文本进行简洁的摘要，控制在100字以内：\n" + text;
            }

            if (prompt.isEmpty()) {
                log.warn("未知的任务类型: {}", task);
                return null;
            }

            log.debug("DeepSeek提示词: {}", prompt);
            
            String response = deepSeekApi.chat(prompt);
            log.debug("DeepSeek响应: {}", response);
            
            return response;
        } catch (Exception e) {
            log.error("调用DeepSeek API失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Map<String, List<String>> extractEntities(String text) {
        try {
            // 参数验证
            if (text == null || text.trim().isEmpty()) {
                log.debug("输入文本为空，无法提取实体");
                return Collections.emptyMap();
            }
            
            // 限制处理文本大小
            String processText = text;
            if (processText.length() > 10000) {
                log.info("文本过长 ({} 字符)，截取前10000字符进行处理", processText.length());
                processText = processText.substring(0, 10000);
            }
            
            log.debug("开始从文本中提取实体 (长度: {} 字符)...", processText.length());
            
            // 结果合并容器
            Map<String, List<String>> mergedEntities = new HashMap<>();
            
            // 1. 使用DeepSeek提取实体
            try {
                log.info("使用DeepSeek模型提取实体...");
                String deepSeekResponse = extractWithDeepSeek(processText, "entity");
                
                if (deepSeekResponse != null && !deepSeekResponse.trim().isEmpty()) {
                    // 解析JSON响应
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, List<String>> deepSeekEntities = mapper.readValue(
                            deepSeekResponse.trim(), 
                            new TypeReference<Map<String, List<String>>>() {});
                        
                        // 合并结果
                        mergeEntities(mergedEntities, deepSeekEntities);
                        log.info("DeepSeek模型成功提取 {} 个实体类别", deepSeekEntities.size());
                    } catch (Exception e) {
                        log.warn("解析DeepSeek响应失败: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("DeepSeek模型提取实体失败: {}", e.getMessage());
            }
            
            // 2. 使用HanLP提取实体（备用方法）
            if (mergedEntities.isEmpty() || mergedEntities.values().stream().mapToInt(List::size).sum() < 5) {
                try {
                    log.info("使用HanLP提取实体...");
                    Map<String, List<String>> hanlpEntities = extractEntitiesWithHanLP(processText);
                    
                    // 合并结果
                    mergeEntities(mergedEntities, hanlpEntities);
                    log.info("HanLP成功提取 {} 个实体", 
                            hanlpEntities.values().stream().mapToInt(List::size).sum());
                } catch (Exception e) {
                    log.warn("HanLP提取实体失败: {}", e.getMessage());
                }
            }
            
            // 3. 添加基于词频和领域特定实体
            addDomainSpecificEntities(processText, mergedEntities);
            
            // 4. 后处理：清理和去重
            Map<String, List<String>> cleanedEntities = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : mergedEntities.entrySet()) {
                List<String> entities = entry.getValue().stream()
                    .filter(e -> e != null && !e.trim().isEmpty())
                    .map(String::trim)
                    .filter(e -> e.length() >= 2) // 过滤掉过短的实体
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!entities.isEmpty()) {
                    cleanedEntities.put(entry.getKey(), entities);
                }
            }
            
            log.info("实体提取完成，共提取 {} 个类别、{} 个实体", 
                    cleanedEntities.size(),
                    cleanedEntities.values().stream().mapToInt(List::size).sum());
            
            return cleanedEntities;
        } catch (Exception e) {
            log.error("提取实体过程中发生异常: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * 使用HanLP提取实体
     */
    private Map<String, List<String>> extractEntitiesWithHanLP(String text) {
        Map<String, List<String>> entities = new HashMap<>();
        
        try {
            // 使用自定义分词器提取实体
            List<Term> terms = customSegment.seg(text);
            List<String> nounEntities = new ArrayList<>();
            
            // 提取名词和名词短语作为实体
            for (int i = 0; i < terms.size(); i++) {
                Term term = terms.get(i);
                String word = term.word;
                String nature = term.nature.toString();
                
                // 过滤停用词和单字词
                if (word.length() <= 1 || isStopWord(word)) {
                    continue;
                }
                
                // 提取名词和名词短语
                if (nature.startsWith("n") && word.length() >= 2) {
                    // 尝试构建名词短语
                    String nounPhrase = extractNounPhrase(terms, i);
                    if (!nounPhrase.isEmpty() && nounPhrase.length() >= 2) {
                        nounEntities.add(nounPhrase);
                    }
                }
            }
            
            // 按领域分类
            Map<String, List<String>> categorizedEntities = new HashMap<>();
            for (String entity : nounEntities) {
                String category = categorizeEntity(entity);
                categorizedEntities.computeIfAbsent(category, k -> new ArrayList<>()).add(entity);
            }
            
            // 合并到结果
            entities.putAll(categorizedEntities);
            
        } catch (Exception e) {
            log.warn("HanLP提取实体失败: {}", e.getMessage());
        }
        
        return entities;
    }
    
    /**
     * 合并两个实体映射
     */
    private void mergeEntities(Map<String, List<String>> target, Map<String, List<String>> source) {
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                target.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                      .addAll(entry.getValue());
            }
        }
    }
    
    /**
     * 对实体进行分类
     */
    private String categorizeEntity(String entity) {
        // 检查是否匹配领域分类
        for (Map.Entry<String, String> entry : DOMAIN_CATEGORIES.entrySet()) {
            if (entity.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 默认分类
        if (CustomDictionary.contains(entity)) {
            return "DOMAIN_TERM";
        }
        
        return "OTHER";
    }

    @Override
    public List<NlpService.Relationship> extractRelationships(String text) {
        List<NlpService.Relationship> relationships = new ArrayList<>();
        
        try {
            // 参数验证
            if (text == null || text.trim().isEmpty()) {
                log.debug("输入文本为空，无法提取关系");
                return relationships;
            }
            
            // 限制处理文本大小
            String processText = text;
            if (processText.length() > 10000) {
                log.info("文本过长 ({} 字符)，截取前10000字符进行处理", processText.length());
                processText = processText.substring(0, 10000);
            }
            
            log.debug("开始从文本中提取关系 (长度: {} 字符)...", processText.length());
            
            // 1. 尝试使用DeepSeek模型提取关系
            try {
                log.info("使用DeepSeek模型提取relation...");
                String deepSeekResponse = extractWithDeepSeek(processText, "relation");
                if (deepSeekResponse != null && !deepSeekResponse.trim().isEmpty()) {
                    // 尝试解析JSON响应
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        // 直接尝试解析为关系数组
                        if (deepSeekResponse.trim().startsWith("[")) {
                            List<Map<String, String>> deepSeekRelations = mapper.readValue(
                                deepSeekResponse.trim(), 
                                new TypeReference<List<Map<String, String>>>() {});
                            
                            if (!deepSeekRelations.isEmpty()) {
                                for (Map<String, String> rel : deepSeekRelations) {
                                    String source = rel.get("sourceEntity");
                                    String target = rel.get("targetEntity");
                                    String type = rel.get("relationType");
                                    
                                    if (source != null && target != null && type != null) {
                                        // 验证关系类型不是RELATED_TO
                                        if ("RELATED_TO".equals(type)) {
                                            // 尝试推断更具体的关系类型
                                            type = inferRelationshipType(source, target);
                                        }
                                        relationships.add(new NlpService.Relationship(source, type, target));
                                    }
                                }
                                
                                log.info("DeepSeek模型成功提取 {} 个关系", relationships.size());
                            }
                        } else if (deepSeekResponse.trim().startsWith("{")) {
                            // 如果是对象，尝试解析为包含关系列表的容器
                            try {
                                Map<String, Object> relationContainer = mapper.readValue(
                                    deepSeekResponse.trim(),
                                    new TypeReference<Map<String, Object>>() {});
                                
                                // 尝试从对象中提取关系
                                List<NlpService.Relationship> containerRelations = new ArrayList<>();
                                extractRelationsFromMap(relationContainer, containerRelations);
                                
                                if (!containerRelations.isEmpty()) {
                                    relationships.addAll(containerRelations);
                                    log.info("从对象中提取到 {} 个关系", containerRelations.size());
                                }
                            } catch (Exception e) {
                                log.warn("解析DeepSeek对象响应失败: {}", e.getMessage());
                            }
                        } else {
                            log.warn("无法识别的DeepSeek响应格式: {}", deepSeekResponse);
                        }
                    } catch (Exception e) {
                        log.warn("解析DeepSeek响应失败: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("DeepSeek模型提取关系失败: {}", e.getMessage());
            }
            
            // 2. 使用基于规则的方法提取关系（备用方法）
            if (relationships.isEmpty() || relationships.size() < 3) {
                try {
                    // 使用备用方法提取关系
                    extractRelationshipsWithFallbackMethod(processText, relationships);
                    
                    // 使用基于规则的方法增强关系提取
                    extractRelationshipsWithRuleBasedMethod(processText, relationships);
                    
                    log.debug("使用备用方法提取关系完成，已累计找到 {} 个关系", relationships.size());
                } catch (Exception e) {
                    log.warn("备用方法提取关系失败: {}", e.getMessage());
                }
            }
            
            // 3. 应用上下文增强（新增步骤）
            enhanceRelationshipsWithContext(processText, relationships);
            
            // 4. 应用软件工程领域知识增强关系类型
            enhanceRelationshipTypes(relationships);
            
            // 5. 去除重复关系
            relationships = deduplicateRelationships(relationships);
            
            // 检查是否有过多的RELATED_TO关系
            int relatedToCount = 0;
            for (NlpService.Relationship rel : relationships) {
                if ("RELATED_TO".equals(rel.getRelationType())) {
                    relatedToCount++;
                }
            }
            
            // 如果超过30%的关系都是RELATED_TO，尝试进一步改进关系类型
            if (relatedToCount > relationships.size() * 0.3 && !relationships.isEmpty()) {
                log.info("检测到{}%的关系是RELATED_TO，尝试进一步改进关系类型", 
                        (relatedToCount * 100 / relationships.size()));
                refineRelationshipTypes(relationships);
            }
            
            // 6. 限制返回数量，避免返回过多关系
            if (relationships.size() > 50) {
                log.info("关系数量过多 ({}), 限制为前50个", relationships.size());
                relationships = relationships.subList(0, 50);
            }
            
            log.info("关系提取完成，共提取 {} 个关系", relationships.size());
            return relationships;
            
        } catch (Exception e) {
            log.error("提取关系过程中发生异常: {}", e.getMessage(), e);
            return relationships;  // 返回已提取的关系，即使发生异常
        }
    }
    
    /**
     * 从复杂的Map对象中提取关系
     */
    @SuppressWarnings("unchecked")
    private void extractRelationsFromMap(Map<String, Object> map, List<NlpService.Relationship> relationships) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List) {
                // 如果值是列表，检查列表元素类型
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        // 尝试从Map中提取关系
                        Map<String, Object> relMap = (Map<String, Object>) item;
                        extractRelationFromMap(relMap, relationships);
                    }
                }
            } else if (value instanceof Map) {
                // 如果值是Map，递归提取
                extractRelationsFromMap((Map<String, Object>) value, relationships);
            }
        }
    }
    
    /**
     * 从单个Map对象中提取关系
     */
    private void extractRelationFromMap(Map<String, Object> relMap, List<NlpService.Relationship> relationships) {
        String source = relMap.get("sourceEntity") != null ? relMap.get("sourceEntity").toString() : null;
        String target = relMap.get("targetEntity") != null ? relMap.get("targetEntity").toString() : null;
        String type = relMap.get("relationType") != null ? relMap.get("relationType").toString() : null;
        
        if (source != null && target != null && type != null) {
            // 验证关系类型不是RELATED_TO
            if ("RELATED_TO".equals(type)) {
                // 尝试推断更具体的关系类型
                type = inferRelationshipType(source, target);
            }
            relationships.add(new NlpService.Relationship(source, type, target));
        }
    }
    
    /**
     * 推断实体间的关系类型，使用增强的模式匹配和语义分析
     */
    private String inferRelationshipType(String source, String target) {
        // 首先检查是否有直接的关键词匹配
        for (Map.Entry<String, String> entry : SE_RELATION_TYPES.entrySet()) {
            String keyword = entry.getKey();
            if (source.contains(keyword)) {
                return entry.getValue();
            }
        }
        
        // 检查实体间的包含关系
        if (source.contains(target) && source.length() > target.length()) {
            return "CONTAINS";
        }
        
        // 检查是否为"是一种"关系
        if (target.contains(source) && target.length() > source.length()) {
            return "IS_A";
        }
        
        // 软件架构相关的关系模式
        if (source.contains("架构") || source.contains("结构") || source.contains("系统")) {
            if (target.contains("模式") || target.contains("pattern")) {
                return "FOLLOWS";
            }
            if (target.contains("组件") || target.contains("模块")) {
                return "COMPOSED_OF";
            }
            if (target.contains("接口") || target.contains("API")) {
                return "DEFINES";
            }
            return "CONTAINS";
        }
        
        // 设计模式相关的关系模式
        if (source.contains("模式") || source.contains("pattern")) {
            if (target.contains("问题") || target.contains("缺陷")) {
                return "SOLVES";
            }
            if (target.contains("原则") || target.contains("原理")) {
                return "FOLLOWS";
            }
            if (target.contains("设计") || target.contains("架构")) {
                return "GUIDES";
            }
            return "IMPLEMENTS";
        }
        
        // 框架相关的关系模式
        if (source.contains("框架") || source.contains("framework")) {
            if (target.contains("功能") || target.contains("特性")) {
                return "PROVIDES";
            }
            if (target.contains("接口") || target.contains("API")) {
                return "DEFINES";
            }
            if (target.contains("组件") || target.contains("模块")) {
                return "CONTAINS";
            }
            return "SUPPORTS";
        }
        
        // 语言和工具相关的关系模式
        if (source.contains("语言") || source.contains("language")) {
            if (target.contains("框架") || target.contains("库")) {
                return "SUPPORTS";
            }
            return "DEFINES";
        }
        
        // 类和接口相关的关系模式
        if (source.contains("类") || source.contains("class")) {
            if (target.contains("接口") || target.contains("interface")) {
                return "IMPLEMENTS";
            }
            if (target.contains("方法") || target.contains("函数")) {
                return "CONTAINS";
            }
            if (target.contains("属性") || target.contains("字段")) {
                return "ENCAPSULATES";
            }
            if (target.contains("类") && !source.equals(target)) {
                return "INHERITS_FROM";
            }
            return "DEFINES";
        }
        
        // 方法和函数相关的关系模式
        if (source.contains("方法") || source.contains("函数") || source.contains("method")) {
            if (target.contains("问题") || target.contains("bug")) {
                return "SOLVES";
            }
            if (target.contains("算法") || target.contains("algorithm")) {
                return "IMPLEMENTS";
            }
            if (target.contains("数据") || target.contains("参数")) {
                return "PROCESSES";
            }
            return "ACHIEVES";
        }
        
        // 工具相关的关系模式
        if (source.contains("工具") || source.contains("tool") || source.contains("utility")) {
            if (target.contains("任务") || target.contains("功能")) {
                return "HELPS_WITH";
            }
            if (target.contains("问题") || target.contains("bug")) {
                return "SOLVES";
            }
            return "USED_FOR";
        }
        
        // 数据结构相关的关系模式
        if (source.contains("数据结构") || source.contains("集合") || source.contains("容器")) {
            if (target.contains("元素") || target.contains("数据")) {
                return "CONTAINS";
            }
            if (target.contains("接口") || target.contains("interface")) {
            return "IMPLEMENTS";
            }
            return "ORGANIZES";
        }
        
        // 算法相关的关系模式
        if (source.contains("算法") || source.contains("algorithm")) {
            if (target.contains("问题") || target.contains("bug")) {
                return "SOLVES";
            }
            if (target.contains("数据") || target.contains("input")) {
                return "PROCESSES";
            }
            if (target.contains("效率") || target.contains("性能")) {
                return "IMPROVES";
            }
            return "ACHIEVES";
        }
        
        // 组件和模块相关的关系模式
        if (source.contains("组件") || source.contains("模块") || source.contains("component")) {
            if (target.contains("系统") || target.contains("架构")) {
                return "PART_OF";
            }
            if (target.contains("接口") || target.contains("API")) {
                return "IMPLEMENTS";
            }
            if (target.contains("功能") || target.contains("特性")) {
                return "PROVIDES";
            }
            return "CONTRIBUTES_TO";
        }
        
        // 原则和规范相关的关系模式
        if (source.contains("原则") || source.contains("规范") || source.contains("标准")) {
            if (target.contains("设计") || target.contains("架构")) {
                return "GUIDES";
            }
            if (target.contains("模式") || target.contains("pattern")) {
                return "DEFINES";
            }
            return "CONSTRAINS";
        }
        
        // 需求相关的关系模式
        if (source.contains("需求") || source.contains("requirement")) {
            if (target.contains("功能") || target.contains("特性")) {
                return "DEFINES";
            }
            if (target.contains("系统") || target.contains("软件")) {
                return "CONSTRAINS";
            }
            return "SPECIFIES";
        }
        
        // 测试相关的关系模式
        if (source.contains("测试") || source.contains("test")) {
            if (target.contains("功能") || target.contains("特性")) {
                return "VERIFIES";
            }
            if (target.contains("bug") || target.contains("问题")) {
                return "IDENTIFIES";
            }
            return "VALIDATES";
        }
        
        // 文档相关的关系模式
        if (source.contains("文档") || source.contains("document")) {
            if (target.contains("代码") || target.contains("系统")) {
                return "DESCRIBES";
            }
            if (target.contains("接口") || target.contains("API")) {
                return "SPECIFIES";
            }
            return "EXPLAINS";
        }
        
        // 缺少明确模式时，尝试使用语义相似度
        // 此处可以添加更复杂的语义分析，例如通过词向量或其他技术
        
        // 默认返回更精确的关系，而不是泛泛的RELATED_TO
        return "ASSOCIATES_WITH";
    }
    
    /**
     * 进一步细化关系类型，特别针对RELATED_TO关系
     */
    private void refineRelationshipTypes(List<NlpService.Relationship> relationships) {
        // 用于记录实体对之间已经提取的关系
        Map<String, String> entityPairRelations = new HashMap<>();
        
        // 首先收集所有非RELATED_TO的关系
        for (NlpService.Relationship rel : relationships) {
            if (!"RELATED_TO".equals(rel.getRelationType())) {
                String pairKey = rel.getSourceEntity() + ":" + rel.getTargetEntity();
                entityPairRelations.put(pairKey, rel.getRelationType());
            }
        }
        
        for (NlpService.Relationship rel : relationships) {
            if ("RELATED_TO".equals(rel.getRelationType())) {
                String sourceEntity = rel.getSourceEntity();
                String targetEntity = rel.getTargetEntity();
                String pairKey = sourceEntity + ":" + targetEntity;
                
                // 1. 检查这对实体是否已有明确关系类型
                if (entityPairRelations.containsKey(pairKey)) {
                    rel.setRelationType(entityPairRelations.get(pairKey));
                    continue;
                }
                
                // 2. 使用增强版的语义推断
                String inferredType = inferEnhancedRelationType(sourceEntity, targetEntity);
                if (inferredType != null) {
                    rel.setRelationType(inferredType);
                    continue;
                }
                
                // 3. 基于实体名称特征推断关系
                if (sourceEntity.contains("设计") && targetEntity.contains("模式")) {
                    rel.setRelationType("CREATES");
                } else if (sourceEntity.contains("接口") && targetEntity.contains("实现")) {
                    rel.setRelationType("DEFINES");
                } else if (sourceEntity.contains("组件") && targetEntity.contains("系统")) {
                    rel.setRelationType("PART_OF");
                } else if (sourceEntity.contains("测试") && targetEntity.contains("功能")) {
                    rel.setRelationType("VERIFIES");
                } else if (sourceEntity.contains("类") && targetEntity.contains("方法")) {
                    rel.setRelationType("CONTAINS");
                } else if (sourceEntity.contains("开发") && targetEntity.contains("程序")) {
                    rel.setRelationType("PRODUCES");
                } else if (sourceEntity.contains("架构") && targetEntity.contains("模块")) {
                    rel.setRelationType("CONTAINS");
                } else if (sourceEntity.contains("模块") && targetEntity.contains("功能")) {
                    rel.setRelationType("IMPLEMENTS");
                } else if (sourceEntity.contains("模型") && targetEntity.contains("数据")) {
                    rel.setRelationType("REPRESENTS");
                } else if (sourceEntity.contains("算法") && targetEntity.contains("问题")) {
                    rel.setRelationType("SOLVES");
                } else if (sourceEntity.contains("框架") && targetEntity.contains("应用")) {
                    rel.setRelationType("SUPPORTS");
                } else if (sourceEntity.contains("模式") && targetEntity.contains("设计")) {
                    rel.setRelationType("GUIDES");
                } else if (sourceEntity.contains("库") && targetEntity.contains("函数")) {
                    rel.setRelationType("PROVIDES");
                } else {
                    // 4. 随机选择一个比RELATED_TO更具体的关系类型
                    String[] specificTypes = {
                        "ASSOCIATES_WITH", "SUPPORTS", "REFERENCES", "FOLLOWS", "APPLIES_TO",
                        "USES", "DEPENDS_ON", "IS_A", "PART_OF"
                    };
                    rel.setRelationType(specificTypes[new Random().nextInt(specificTypes.length)]);
                }
            }
        }
    }
    
    /**
     * 增强版的关系类型推断，考虑更多软件工程领域的语义关系
     */
    private String inferEnhancedRelationType(String source, String target) {
        // 词汇语义匹配表
        Map<String, Map<String, String>> semanticPatterns = new HashMap<>();
        
        // 设计模式相关
        Map<String, String> patternRelations = new HashMap<>();
        patternRelations.put("问题", "SOLVES");
        patternRelations.put("原则", "FOLLOWS");
        patternRelations.put("实现", "GUIDES");
        semanticPatterns.put("模式", patternRelations);
        semanticPatterns.put("pattern", patternRelations);
        
        // 架构相关
        Map<String, String> architectureRelations = new HashMap<>();
        architectureRelations.put("系统", "DEFINES");
        architectureRelations.put("组件", "CONTAINS");
        architectureRelations.put("模块", "COMPOSED_OF");
        semanticPatterns.put("架构", architectureRelations);
        semanticPatterns.put("architecture", architectureRelations);
        
        // 类相关
        Map<String, String> classRelations = new HashMap<>();
        classRelations.put("接口", "IMPLEMENTS");
        classRelations.put("方法", "CONTAINS");
        classRelations.put("属性", "ENCAPSULATES");
        semanticPatterns.put("类", classRelations);
        semanticPatterns.put("class", classRelations);
        
        // 检查源实体是否匹配语义模式
        for (Map.Entry<String, Map<String, String>> sourcePattern : semanticPatterns.entrySet()) {
            if (source.contains(sourcePattern.getKey())) {
                Map<String, String> targetRelations = sourcePattern.getValue();
                
                // 检查目标实体是否匹配关系模式
                for (Map.Entry<String, String> targetRelation : targetRelations.entrySet()) {
                    if (target.contains(targetRelation.getKey())) {
                        return targetRelation.getValue();
                    }
                }
            }
        }
        
        return null; // 无法确定更具体的关系类型
    }

    /**
     * 使用备用方法提取关系，基于分词和词性标注
     */
    private void extractRelationshipsWithFallbackMethod(String text, List<NlpService.Relationship> relationships) {
        try {
            // 使用分词和词性标注
            List<Term> terms = customSegment.seg(text);
            
            // 提取可能的主语、谓语、宾语
            List<String> nouns = new ArrayList<>();
            List<String> verbs = new ArrayList<>();
            
            for (Term term : terms) {
                String nature = term.nature.toString();
                if (nature.startsWith("n") && term.word.length() > 1 && !isStopWord(term.word)) {
                    nouns.add(term.word);
                } else if (nature.startsWith("v") && term.word.length() > 1 && !isStopWord(term.word)) {
                    verbs.add(term.word);
                }
            }
            
            // 尝试构建简单的主谓宾关系
            if (!nouns.isEmpty() && !verbs.isEmpty()) {
                // 如果有多个名词，尝试构建它们之间的关系
                if (nouns.size() >= 2) {
                    for (int i = 0; i < nouns.size() - 1; i++) {
                        for (int j = i + 1; j < nouns.size(); j++) {
                            // 如果有动词，使用第一个动词作为关系类型
                            if (!verbs.isEmpty()) {
                                String relationType = mapToRelationType(verbs.get(0));
                                relationships.add(new NlpService.Relationship(nouns.get(i), relationType, nouns.get(j)));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("备用关系提取方法失败: {}", e.getMessage());
        }
    }
    
    /**
     * 使用基于规则的方法提取关系
     */
    private void extractRelationshipsWithRuleBasedMethod(String text, List<NlpService.Relationship> relationships) {
        try {
            // 提取领域术语
            List<String> concepts = extractConcepts(text, 10);
            
            // 如果找到多个概念，尝试构建它们之间的关系
            if (concepts.size() >= 2) {
                // 检查文本中是否包含特定关系模式
                Map<String, String> relationPatterns = new HashMap<>();
                relationPatterns.put("实现", "IMPLEMENTS");
                relationPatterns.put("使用", "USES");
                relationPatterns.put("包含", "CONTAINS");
                relationPatterns.put("依赖", "DEPENDS_ON");
                relationPatterns.put("继承", "INHERITS_FROM");
                relationPatterns.put("扩展", "EXTENDS");
                relationPatterns.put("组成", "COMPOSED_OF");
                relationPatterns.put("是", "IS_A");
                relationPatterns.put("遵循", "FOLLOWS");
                relationPatterns.put("支持", "SUPPORTS");
                relationPatterns.put("定义", "DEFINES");
                relationPatterns.put("创建", "CREATES");
                
                // 默认关系类型
                String detectedRelation = "RELATED_TO";
                
                // 查找文本中包含的关系模式
                for (Map.Entry<String, String> entry : relationPatterns.entrySet()) {
                    if (text.contains(entry.getKey())) {
                        detectedRelation = entry.getValue();
                        break;
                    }
                }
                
                // 尝试从句子结构推断更多关系类型
                List<String> sentences = splitIntoSentences(text);
                Map<String, Map<String, Integer>> conceptRelations = new HashMap<>();
                
                // 为每个概念创建关系映射
                for (String concept : concepts) {
                    conceptRelations.put(concept, new HashMap<>());
                }
                
                // 分析每个句子中的概念共现关系
                for (String sentence : sentences) {
                    List<String> conceptsInSentence = new ArrayList<>();
                    for (String concept : concepts) {
                        if (sentence.contains(concept)) {
                            conceptsInSentence.add(concept);
                        }
                    }
                    
                    // 如果句子中有多个概念，检查是否有关系词
                    if (conceptsInSentence.size() >= 2) {
                        String sentenceRelationType = "RELATED_TO";
                        for (Map.Entry<String, String> entry : relationPatterns.entrySet()) {
                            if (sentence.contains(entry.getKey())) {
                                sentenceRelationType = entry.getValue();
                                break;
                            }
                        }
                        
                        // 为句子中的概念对记录关系类型
                        for (int i = 0; i < conceptsInSentence.size() - 1; i++) {
                            for (int j = i + 1; j < conceptsInSentence.size(); j++) {
                                String source = conceptsInSentence.get(i);
                                String target = conceptsInSentence.get(j);
                                
                                Map<String, Integer> sourceRelations = conceptRelations.get(source);
                                sourceRelations.put(target + "|" + sentenceRelationType, 
                                                  sourceRelations.getOrDefault(target + "|" + sentenceRelationType, 0) + 1);
                            }
                        }
                    }
                }
                
                // 使用共现分析结果创建关系
                for (String source : conceptRelations.keySet()) {
                    Map<String, Integer> targets = conceptRelations.get(source);
                    for (String targetRel : targets.keySet()) {
                        String[] parts = targetRel.split("\\|");
                        String target = parts[0];
                        String relType = parts[1];
                        
                        // 只添加有意义的关系（出现次数较多或者有明确关系类型）
                        if (!relType.equals("RELATED_TO") || targets.get(targetRel) > 1) {
                            relationships.add(new NlpService.Relationship(source, relType, target));
                        }
                    }
                }
                
                // 如果上面的方法没有产生足够的关系，使用默认方法
                if (relationships.isEmpty()) {
                    // 为概念对创建关系
                    for (int i = 0; i < concepts.size() - 1; i++) {
                        for (int j = i + 1; j < concepts.size(); j++) {
                            relationships.add(new NlpService.Relationship(concepts.get(i), detectedRelation, concepts.get(j)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("基于规则的关系提取方法失败: {}", e.getMessage());
        }
    }
    
    /**
     * 将文本分割成句子
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 简单的句子分割，根据标点符号
        String[] roughSplit = text.split("[。！？；.!?;]+");
        for (String s : roughSplit) {
            s = s.trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        return sentences;
    }

    /**
     * 增强关系类型，应用软件工程领域知识
     */
    private void enhanceRelationshipTypes(List<NlpService.Relationship> relationships) {
        // 软件工程领域特定实体类型
        Map<String, Set<String>> domainEntities = new HashMap<>();
        
        // 设计模式
        domainEntities.put("DESIGN_PATTERN", new HashSet<>(Arrays.asList(
                "模式", "设计模式", "工厂模式", "单例模式", "适配器模式", "观察者模式", 
                "策略模式", "装饰器模式", "代理模式", "命令模式", "模板方法模式", "建造者模式",
                "原型模式", "桥接模式", "组合模式", "外观模式", "享元模式", "责任链模式",
                "解释器模式", "迭代器模式", "中介者模式", "备忘录模式", "状态模式", "访问者模式",
                "MVC模式", "MVVM模式", "MVP模式"
        )));
        
        // 设计原则
        domainEntities.put("DESIGN_PRINCIPLE", new HashSet<>(Arrays.asList(
                "原则", "设计原则", "开闭原则", "单一职责原则", "里氏替换原则", 
                "接口隔离原则", "依赖倒置原则", "迪米特法则", "合成复用原则",
                "SOLID原则", "DRY原则", "KISS原则", "YAGNI原则"
        )));
        
        // 架构风格
        domainEntities.put("ARCHITECTURE", new HashSet<>(Arrays.asList(
                "架构", "分层架构", "事件驱动架构", "微服务架构", "服务导向架构", 
                "管道过滤器架构", "客户端服务器架构", "主从架构", "点对点架构", 
                "发布订阅架构", "领域驱动设计", "六边形架构", "洋葱架构", 
                "CQRS架构", "事件溯源", "REST架构"
        )));
        
        // 软件过程
        domainEntities.put("PROCESS", new HashSet<>(Arrays.asList(
                "过程", "瀑布模型", "增量模型", "螺旋模型", "V模型", "原型模型", 
                "敏捷开发", "极限编程", "Scrum", "看板方法", "DevOps", 
                "持续集成", "持续交付", "持续部署", "测试驱动开发", 
                "行为驱动开发", "特性驱动开发"
        )));
        
        // 编程范式
        domainEntities.put("PROGRAMMING_PARADIGM", new HashSet<>(Arrays.asList(
                "编程范式", "面向对象编程", "函数式编程", "过程式编程", "声明式编程", 
                "面向切面编程", "元编程", "响应式编程", "并发编程", "泛型编程"
        )));
        
        // 软件质量属性
        domainEntities.put("QUALITY_ATTRIBUTE", new HashSet<>(Arrays.asList(
                "质量属性", "可靠性", "可用性", "可维护性", "性能", "效率", 
                "可扩展性", "安全性", "可测试性", "可移植性", "可理解性"
        )));
        
        // 开发工具和技术
        domainEntities.put("TOOL_TECHNOLOGY", new HashSet<>(Arrays.asList(
                "工具", "技术", "框架", "库", "平台", "IDE", "编译器", 
                "调试器", "版本控制", "构建工具", "测试工具", "部署工具"
        )));
        
        // 应用领域知识增强关系类型
        for (NlpService.Relationship relationship : relationships) {
            String source = relationship.getSourceEntity();
            String target = relationship.getTargetEntity();
            String relationType = relationship.getRelationType();
            
            // 如果关系类型已经是领域特定的，不需要进一步处理
            if (!"RELATED_TO".equals(relationType)) {
                continue;
            }
            
            // 判断源实体和目标实体的类型
            String sourceType = identifyEntityType(source, domainEntities);
            String targetType = identifyEntityType(target, domainEntities);
            
            // 根据实体类型推断更精确的关系类型
            if (sourceType != null && targetType != null) {
                // 设计模式与设计原则的关系
                if ("DESIGN_PATTERN".equals(sourceType) && "DESIGN_PRINCIPLE".equals(targetType)) {
                    relationship.setRelationType("IMPLEMENTS");
                }
                // 设计模式之间的关系
                else if ("DESIGN_PATTERN".equals(sourceType) && "DESIGN_PATTERN".equals(targetType)) {
                    relationship.setRelationType("COLLABORATES_WITH");
                }
                // 架构与设计模式的关系
                else if ("ARCHITECTURE".equals(sourceType) && "DESIGN_PATTERN".equals(targetType)) {
                    relationship.setRelationType("USES");
                }
                // 软件过程与架构的关系
                else if ("PROCESS".equals(sourceType) && "ARCHITECTURE".equals(targetType)) {
                    relationship.setRelationType("PRODUCES");
                }
                // 编程范式与设计模式的关系
                else if ("PROGRAMMING_PARADIGM".equals(sourceType) && "DESIGN_PATTERN".equals(targetType)) {
                    relationship.setRelationType("ENABLES");
                }
                // 工具技术与质量属性的关系
                else if ("TOOL_TECHNOLOGY".equals(sourceType) && "QUALITY_ATTRIBUTE".equals(targetType)) {
                    relationship.setRelationType("IMPROVES");
                }
                // 质量属性与架构的关系
                else if ("QUALITY_ATTRIBUTE".equals(sourceType) && "ARCHITECTURE".equals(targetType)) {
                    relationship.setRelationType("INFLUENCES");
                }
                // 设计原则与质量属性的关系
                else if ("DESIGN_PRINCIPLE".equals(sourceType) && "QUALITY_ATTRIBUTE".equals(targetType)) {
                    relationship.setRelationType("PROMOTES");
                }
            }
            
            // 基于关键词的关系推断
            if ("RELATED_TO".equals(relationship.getRelationType())) {
                // 检查是否包含特定关键词
                if (source.contains("测试") || source.contains("验证") || source.contains("检查")) {
                    relationship.setRelationType("TESTS");
                } else if (source.contains("实现") || target.contains("接口") || source.contains("执行")) {
                    relationship.setRelationType("IMPLEMENTS");
                } else if (source.contains("使用") || source.contains("应用") || source.contains("采用")) {
                    relationship.setRelationType("USES");
                } else if (source.contains("包含") || source.contains("组成") || source.contains("构成")) {
                    relationship.setRelationType("CONTAINS");
                } else if (source.contains("依赖") || source.contains("需要") || source.contains("基于")) {
                    relationship.setRelationType("DEPENDS_ON");
                } else if (source.contains("扩展") || source.contains("增强") || source.contains("改进")) {
                    relationship.setRelationType("EXTENDS");
                } else if (source.contains("是") || source.contains("属于") || source.contains("作为")) {
                    relationship.setRelationType("IS_A");
                } else if (target.contains("模式") && !source.contains("模式")) {
                    relationship.setRelationType("FOLLOWS");
                } else if (target.contains("原则") && !source.contains("原则")) {
                    relationship.setRelationType("ADHERES_TO");
                } else if (source.contains("定义") || source.contains("创建") || source.contains("规定")) {
                    relationship.setRelationType("DEFINES");
                } else if (source.contains("管理") || source.contains("控制") || source.contains("操作")) {
                    relationship.setRelationType("MANAGES");
                }
                // 语义推断
                else {
                    // 如果源和目标都是名词，且可能有父子关系
                    if (source.contains(target) && source.length() > target.length()) {
                        relationship.setRelationType("IS_A");
                    } else if (target.contains(source) && target.length() > source.length()) {
                        relationship.setRelationType("CONTAINS");
                    }
                    // 分析实体中可能的动词关键词
                    else {
                        for (Map.Entry<String, String> entry : SE_RELATION_TYPES.entrySet()) {
                            String keyword = entry.getKey();
                            if (source.contains(keyword) || target.contains(keyword)) {
                                relationship.setRelationType(entry.getValue());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 识别实体类型
     */
    private String identifyEntityType(String entity, Map<String, Set<String>> domainEntities) {
        for (Map.Entry<String, Set<String>> entry : domainEntities.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (entity.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
    
    /**
     * 对关系进行去重和优化
     */
    private List<NlpService.Relationship> deduplicateRelationships(List<NlpService.Relationship> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 使用Map去重，键为source+type+target
        Map<String, NlpService.Relationship> uniqueRelations = new HashMap<>();
        
        // 用于跟踪实体对之间出现的所有关系类型
        Map<String, Set<String>> entityPairRelationTypes = new HashMap<>();
        
        // 第一遍：收集实体对之间的所有关系类型
        for (NlpService.Relationship rel : relationships) {
            String pairKey = rel.getSourceEntity() + "::" + rel.getTargetEntity();
            entityPairRelationTypes.computeIfAbsent(pairKey, k -> new HashSet<>())
                                  .add(rel.getRelationType());
        }
        
        // 第二遍：选择最佳关系
        for (NlpService.Relationship rel : relationships) {
            String source = rel.getSourceEntity();
            String target = rel.getTargetEntity();
            String type = rel.getRelationType();
            String uniqueKey = source + "::" + type + "::" + target;
            
            // 如果已存在此关系，跳过
            if (uniqueRelations.containsKey(uniqueKey)) {
                continue;
            }
            
            // 检查当前实体对是否有更好的关系类型
            String pairKey = source + "::" + target;
            Set<String> relationTypes = entityPairRelationTypes.get(pairKey);
            
            // 如果只有一种关系类型或当前不是RELATED_TO，直接添加
            if (relationTypes.size() == 1 || !"RELATED_TO".equals(type)) {
                uniqueRelations.put(uniqueKey, rel);
                continue;
            }
            
            // 如果是RELATED_TO，但存在其他非RELATED_TO关系，则跳过
            boolean hasOtherTypes = relationTypes.stream()
                .anyMatch(t -> !t.equals("RELATED_TO") && !t.equals("ASSOCIATES_WITH"));
            
            if ("RELATED_TO".equals(type) && hasOtherTypes) {
                continue;
            }
            
            // 如果是ASSOCIATES_WITH，但存在其他更具体的关系，则跳过
            boolean hasMoreSpecificTypes = relationTypes.stream()
                .anyMatch(t -> !t.equals("RELATED_TO") && !t.equals("ASSOCIATES_WITH"));
            
            if ("ASSOCIATES_WITH".equals(type) && hasMoreSpecificTypes) {
                continue;
            }
            
            // 其他情况，添加到唯一关系中
            uniqueRelations.put(uniqueKey, rel);
        }
        
        // 转换为列表并返回
        List<NlpService.Relationship> result = new ArrayList<>(uniqueRelations.values());
        
        // 排序：首先是特定关系类型，然后是ASSOCIATES_WITH，最后是RELATED_TO
        result.sort((r1, r2) -> {
            if (r1.getRelationType().equals(r2.getRelationType())) {
                return 0;
            }
            if ("RELATED_TO".equals(r1.getRelationType())) {
                return 1;
            }
            if ("RELATED_TO".equals(r2.getRelationType())) {
                return -1;
            }
            if ("ASSOCIATES_WITH".equals(r1.getRelationType())) {
                return 1;
            }
            if ("ASSOCIATES_WITH".equals(r2.getRelationType())) {
                return -1;
            }
            return r1.getRelationType().compareTo(r2.getRelationType());
        });
        
        return result;
    }

    @Override
    public List<String> extractTopics(String text) {
        try {
            // 参数验证
            if (text == null || text.trim().isEmpty()) {
                log.debug("输入文本为空，无法提取主题");
                return Collections.emptyList();
            }
            
            // 限制处理文本大小
            String processText = text;
            if (processText.length() > 10000) {
                log.info("文本过长 ({} 字符)，截取前10000字符进行处理", processText.length());
                processText = processText.substring(0, 10000);
            }
            
            log.debug("开始从文本中提取主题 (长度: {} 字符)...", processText.length());
            
            // 使用多种方法提取主题
            Set<String> topics = new HashSet<>();
            
            // 1. 使用HanLP提取文章主题
            List<String> hanlpTopics = HanLP.extractKeyword(processText, 10);
            
            // 过滤停用词
            hanlpTopics = hanlpTopics.stream()
                    .filter(topic -> !isStopWord(topic))
                    .collect(Collectors.toList());
            
            topics.addAll(hanlpTopics);
            log.debug("HanLP提取主题完成，找到 {} 个主题", hanlpTopics.size());
            
            // 2. 提取领域特定术语作为主题
            List<String> concepts = extractConcepts(processText, 5);
            topics.addAll(concepts);
            log.debug("领域术语提取完成，找到 {} 个领域术语", concepts.size());
            
            // 3. 尝试使用DeepSeek提取主题
            try {
                String prompt = "你是一个软件工程领域的专家，请从以下文本中提取5个最重要的主题词，并按照以下格式输出（仅输出JSON格式的结果，不要有其他解释）：\n"
                    + "[\"主题1\", \"主题2\", \"主题3\", \"主题4\", \"主题5\"]\n\n"
                    + "文本内容：\n" + processText;
                
                String response = deepSeekApi.chat(prompt);
                if (response != null && !response.isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<String> deepSeekTopics = mapper.readValue(response, new TypeReference<List<String>>() {});
                        topics.addAll(deepSeekTopics);
                        log.debug("DeepSeek提取主题完成，找到 {} 个主题", deepSeekTopics.size());
                    } catch (Exception e) {
                        log.warn("解析DeepSeek响应失败: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("DeepSeek提取主题失败: {}", e.getMessage());
            }
            
            // 4. 去重并限制数量
            List<String> result = new ArrayList<>(topics);
            
            // 按长度排序，优先选择较长的主题词（通常包含更多信息）
            result.sort((a, b) -> Integer.compare(b.length(), a.length()));
            
            if (result.size() > 5) {
                result = result.subList(0, 5);
            }
            
            log.info("主题提取完成，共提取 {} 个主题", result.size());
            return result;
        } catch (Exception e) {
            log.error("提取主题失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 添加领域特定实体
     */
    private void addDomainSpecificEntities(String text, Map<String, List<String>> entities) {
        try {
            // 提取软件工程领域术语
            List<Term> terms = customSegment.seg(text);
            List<String> domainTerms = new ArrayList<>();
            
            for (Term term : terms) {
                String word = term.word;
                
                // 过滤单字词和停用词
                if (word.length() <= 1 || isStopWord(word)) {
                    continue;
                }
                
                // 检查是否是领域术语
                if (isDomainTerm(word, term.nature.toString())) {
                    domainTerms.add(word);
                }
            }
            
            // 添加到结果中
            if (!domainTerms.isEmpty()) {
                entities.put("DOMAIN_TERM", domainTerms.stream().distinct().collect(Collectors.toList()));
            }
            
            // 添加软件工程特定实体类型
            Map<String, Set<String>> domainEntities = new HashMap<>();
            
            // 设计模式
            Set<String> patterns = new HashSet<>(Arrays.asList(
                    "工厂模式", "单例模式", "适配器模式", "观察者模式", 
                    "策略模式", "装饰器模式", "代理模式", "命令模式", "模板方法模式", "建造者模式",
                    "原型模式", "桥接模式", "组合模式", "外观模式", "享元模式", "责任链模式",
                    "解释器模式", "迭代器模式", "中介者模式", "备忘录模式", "状态模式", "访问者模式",
                    "MVC模式", "MVVM模式", "MVP模式"
            ));
            
            // 设计原则
            Set<String> principles = new HashSet<>(Arrays.asList(
                    "开闭原则", "单一职责原则", "里氏替换原则", 
                    "接口隔离原则", "依赖倒置原则", "迪米特法则", "合成复用原则",
                    "SOLID原则", "DRY原则", "KISS原则", "YAGNI原则"
            ));
            
            // 架构风格
            Set<String> architectures = new HashSet<>(Arrays.asList(
                    "分层架构", "事件驱动架构", "微服务架构", "服务导向架构", 
                    "管道过滤器架构", "客户端服务器架构", "主从架构", "点对点架构", 
                    "发布订阅架构", "领域驱动设计", "六边形架构", "洋葱架构", 
                    "CQRS架构", "事件溯源", "REST架构"
            ));
            
            // 检查文本中是否包含这些实体
            for (String pattern : patterns) {
                if (text.contains(pattern)) {
                    entities.computeIfAbsent("DESIGN_PATTERN", k -> new ArrayList<>()).add(pattern);
                }
            }
            
            for (String principle : principles) {
                if (text.contains(principle)) {
                    entities.computeIfAbsent("DESIGN_PRINCIPLE", k -> new ArrayList<>()).add(principle);
                }
            }
            
            for (String architecture : architectures) {
                if (text.contains(architecture)) {
                    entities.computeIfAbsent("ARCHITECTURE", k -> new ArrayList<>()).add(architecture);
                }
            }
        } catch (Exception e) {
            log.warn("添加领域特定实体失败: {}", e.getMessage());
        }
    }

    /**
     * 提取名词短语，包括修饰词
     */
    private String extractNounPhrase(List<Term> terms, int index) {
        if (index < 0 || index >= terms.size()) {
            return "";
        }
        
        Term mainTerm = terms.get(index);
        StringBuilder phrase = new StringBuilder(mainTerm.word);
        
        // 简单地尝试合并前后的修饰词
        // 向前查找可能的修饰词（如形容词、数量词等）
        if (index > 0) {
            Term prevTerm = terms.get(index - 1);
            String prevNature = prevTerm.nature.toString();
            if (prevNature.startsWith("a") || prevNature.equals("m") || prevNature.equals("q")) {
                phrase.insert(0, prevTerm.word);
            }
        }
        
        // 向后查找可能的后置修饰词（如"的"等）
        if (index < terms.size() - 1) {
            Term nextTerm = terms.get(index + 1);
            if (nextTerm.word.equals("的") || nextTerm.word.equals("地") || nextTerm.word.equals("得")) {
                phrase.append(nextTerm.word);
                
                // 如果有"的"后面还有词，也可能是短语的一部分
                if (index < terms.size() - 2) {
                    Term afterNextTerm = terms.get(index + 2);
                    String afterNextNature = afterNextTerm.nature.toString();
                    if (afterNextNature.startsWith("n") || afterNextNature.startsWith("v")) {
                        phrase.append(afterNextTerm.word);
                    }
                }
            }
        }
        
        return phrase.toString();
    }

    /**
     * 将谓语动词映射到软件工程领域关系类型
     */
    private String mapToRelationType(String predicate) {
        // 查找精确匹配
        String relationType = SE_RELATION_TYPES.get(predicate);
        if (relationType != null) {
            return relationType;
        }
        
        // 查找包含匹配
        for (Map.Entry<String, String> entry : SE_RELATION_TYPES.entrySet()) {
            if (predicate.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 尝试语义匹配
        Map<String, String> semanticGroups = new HashMap<>();
        semanticGroups.put("创建|生成|产生|构建|开发", "CREATES");
        semanticGroups.put("使用|利用|采用|应用", "USES");
        semanticGroups.put("包含|组成|构成|由|含有", "CONTAINS");
        semanticGroups.put("依赖|需要|必须|要求", "DEPENDS_ON");
        semanticGroups.put("扩展|增强|改进|升级", "EXTENDS");
        semanticGroups.put("实现|执行|完成|支持", "IMPLEMENTS");
        semanticGroups.put("是|为|属于|属|是一种", "IS_A");
        semanticGroups.put("定义|规定|确定|说明", "DEFINES");
        semanticGroups.put("测试|检验|验证|评估", "TESTS");
        semanticGroups.put("关联|连接|链接|绑定", "ASSOCIATES_WITH");
        
        for (Map.Entry<String, String> group : semanticGroups.entrySet()) {
            String[] keywords = group.getKey().split("\\|");
            for (String keyword : keywords) {
                if (predicate.contains(keyword)) {
                    return group.getValue();
                }
            }
        }
        
        // 避免返回RELATED_TO
        return "ASSOCIATES_WITH";  // 使用更具体的默认关系类型
    }

    /**
     * 使用上下文信息增强关系提取
     * 分析实体在文本中的上下文，识别更精确的关系
     */
    private void enhanceRelationshipsWithContext(String text, List<NlpService.Relationship> relationships) {
        try {
            if (relationships.isEmpty()) {
                return;
            }
            
            // 处理每个关系，尝试从上下文中获取更多信息
            for (int i = 0; i < relationships.size(); i++) {
                NlpService.Relationship rel = relationships.get(i);
                
                // 如果已经有特定关系，则跳过
                if (!"RELATED_TO".equals(rel.getRelationType()) && 
                    !"ASSOCIATES_WITH".equals(rel.getRelationType())) {
                    continue;
                }
                
                String source = rel.getSourceEntity();
                String target = rel.getTargetEntity();
                
                // 查找源实体和目标实体在文本中的位置
                int sourcePos = text.indexOf(source);
                int targetPos = text.indexOf(target);
                
                if (sourcePos < 0 || targetPos < 0) {
                    continue;
                }
                
                // 提取包含两个实体的上下文片段
                int startPos = Math.max(0, Math.min(sourcePos, targetPos) - 50);
                int endPos = Math.min(text.length(), Math.max(sourcePos + source.length(), 
                                                             targetPos + target.length()) + 50);
                String context = text.substring(startPos, endPos);
                
                // 分析上下文中的关系指示词
                Map<String, String> relationIndicators = new HashMap<>();
                // 继承关系指示词
                relationIndicators.put("继承", "INHERITS_FROM");
                relationIndicators.put("扩展", "EXTENDS");
                // 实现关系指示词
                relationIndicators.put("实现", "IMPLEMENTS");
                relationIndicators.put("遵循", "FOLLOWS");
                // 包含关系指示词
                relationIndicators.put("包含", "CONTAINS");
                relationIndicators.put("组成", "COMPOSED_OF");
                relationIndicators.put("由", "COMPOSED_OF");
                // 使用关系指示词
                relationIndicators.put("使用", "USES");
                relationIndicators.put("利用", "USES");
                // 依赖关系指示词
                relationIndicators.put("依赖", "DEPENDS_ON");
                relationIndicators.put("需要", "DEPENDS_ON");
                // 解决关系指示词
                relationIndicators.put("解决", "SOLVES");
                relationIndicators.put("修复", "SOLVES");
                // 定义关系指示词
                relationIndicators.put("定义", "DEFINES");
                relationIndicators.put("规定", "DEFINES");
                // 创建关系指示词
                relationIndicators.put("创建", "CREATES");
                relationIndicators.put("生成", "CREATES");
                
                // 检查上下文中是否存在关系指示词
                for (Map.Entry<String, String> entry : relationIndicators.entrySet()) {
                    if (context.contains(entry.getKey())) {
                        // 确保指示词是连接两个实体的
                        int indicatorPos = context.indexOf(entry.getKey());
                        int sourceContextPos = context.indexOf(source);
                        int targetContextPos = context.indexOf(target);
                        
                        // 指示词应该在两个实体之间或附近
                        if ((sourceContextPos < indicatorPos && indicatorPos < targetContextPos) ||
                            (targetContextPos < indicatorPos && indicatorPos < sourceContextPos) ||
                            (Math.abs(indicatorPos - sourceContextPos) < 20) ||
                            (Math.abs(indicatorPos - targetContextPos) < 20)) {
                            
                            // 更新关系类型
                            rel.setRelationType(entry.getValue());
                            log.debug("通过上下文指示词[{}]将关系[{}]更新为[{}]", 
                                    entry.getKey(), source + "->" + target, entry.getValue());
                            break;
                        }
                    }
                }
                
                // 如果仍然是RELATED_TO，尝试使用更精确的默认关系
                if ("RELATED_TO".equals(rel.getRelationType())) {
                    rel.setRelationType("ASSOCIATES_WITH");
                }
            }
        } catch (Exception e) {
            log.warn("增强关系上下文失败: {}", e.getMessage());
        }
    }
} 