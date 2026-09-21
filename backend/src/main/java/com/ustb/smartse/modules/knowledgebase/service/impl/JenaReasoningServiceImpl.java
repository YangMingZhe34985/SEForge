package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.ustb.smartse.modules.knowledgebase.service.JenaReasoningService;
import com.ustb.smartse.modules.knowledgebase.service.NlpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.*;

/**
 * Apache Jena推理服务实现类
 */
@Slf4j
@Service
public class JenaReasoningServiceImpl implements JenaReasoningService {

    @Autowired
    private NlpService nlpService;

    private OntModel ontologyModel;
    private String namespace = "http://www.ustb.edu.cn/smartse/ontology#";
    private String ontologyPath = "ontology/software-engineering.owl";

    @PostConstruct
    public void init() {
        try {
            log.info("初始化Jena本体模型...");
            
            // 创建本体模型，使用OWL推理
            ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
            
            try {
                // 尝试加载本体文件
                ClassPathResource resource = new ClassPathResource(ontologyPath);
                if (resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        ontologyModel.read(is, namespace, "RDF/XML");
                        log.info("成功加载本体文件: {}", ontologyPath);
                    }
                } else {
                    log.info("本体文件不存在，将创建新的本体模型");
                    createInitialOntology();
                }
            } catch (Exception e) {
                log.warn("加载本体文件失败，将创建新的本体模型", e);
                createInitialOntology();
            }
            
            log.info("Jena本体模型初始化完成");
        } catch (Exception e) {
            log.error("初始化Jena本体模型失败", e);
        }
    }

    /**
     * 创建初始本体模型
     */
    private void createInitialOntology() {
        try {
            // 创建基本类
            Resource designPrincipleClass = ontologyModel.createResource(namespace + "DesignPrinciple");
            Resource designPatternClass = ontologyModel.createResource(namespace + "DesignPattern");
            Resource applicationScenarioClass = ontologyModel.createResource(namespace + "ApplicationScenario");
            
            // 添加类型
            ontologyModel.add(designPrincipleClass, RDF.type, RDFS.Class);
            ontologyModel.add(designPatternClass, RDF.type, RDFS.Class);
            ontologyModel.add(applicationScenarioClass, RDF.type, RDFS.Class);
            
            // 创建属性
            Property hasName = ontologyModel.createProperty(namespace + "hasName");
            Property hasDescription = ontologyModel.createProperty(namespace + "hasDescription");
            Property appliesTo = ontologyModel.createProperty(namespace + "appliesTo");
            Property inheritsFrom = ontologyModel.createProperty(namespace + "inheritsFrom");
            Property relatesTo = ontologyModel.createProperty(namespace + "relatesTo");
            
            // 添加属性类型
            ontologyModel.add(hasName, RDF.type, RDF.Property);
            ontologyModel.add(hasDescription, RDF.type, RDF.Property);
            ontologyModel.add(appliesTo, RDF.type, RDF.Property);
            ontologyModel.add(inheritsFrom, RDF.type, RDF.Property);
            ontologyModel.add(relatesTo, RDF.type, RDF.Property);
            
            // 添加属性域和值域
            ontologyModel.add(hasName, RDFS.domain, RDFS.Resource);
            ontologyModel.add(hasName, RDFS.range, RDFS.Literal);
            
            ontologyModel.add(hasDescription, RDFS.domain, RDFS.Resource);
            ontologyModel.add(hasDescription, RDFS.range, RDFS.Literal);
            
            ontologyModel.add(appliesTo, RDFS.domain, designPrincipleClass);
            ontologyModel.add(appliesTo, RDFS.range, applicationScenarioClass);
            
            ontologyModel.add(inheritsFrom, RDFS.domain, designPatternClass);
            ontologyModel.add(inheritsFrom, RDFS.range, designPatternClass);
            
            ontologyModel.add(relatesTo, RDFS.domain, RDFS.Resource);
            ontologyModel.add(relatesTo, RDFS.range, RDFS.Resource);
            
            // 添加一些示例数据
            // 设计原则
            Resource srp = ontologyModel.createResource(namespace + "SingleResponsibilityPrinciple");
            ontologyModel.add(srp, RDF.type, designPrincipleClass);
            ontologyModel.add(srp, hasName, "单一职责原则");
            ontologyModel.add(srp, hasDescription, "一个类应该只有一个引起它变化的原因");
            
            Resource ocp = ontologyModel.createResource(namespace + "OpenClosedPrinciple");
            ontologyModel.add(ocp, RDF.type, designPrincipleClass);
            ontologyModel.add(ocp, hasName, "开闭原则");
            ontologyModel.add(ocp, hasDescription, "软件实体应该对扩展开放，对修改关闭");
            
            // 应用场景
            Resource refactoring = ontologyModel.createResource(namespace + "Refactoring");
            ontologyModel.add(refactoring, RDF.type, applicationScenarioClass);
            ontologyModel.add(refactoring, hasName, "代码重构");
            
            Resource moduleDesign = ontologyModel.createResource(namespace + "ModuleDesign");
            ontologyModel.add(moduleDesign, RDF.type, applicationScenarioClass);
            ontologyModel.add(moduleDesign, hasName, "模块设计");
            
            // 设计模式
            Resource strategyPattern = ontologyModel.createResource(namespace + "StrategyPattern");
            ontologyModel.add(strategyPattern, RDF.type, designPatternClass);
            ontologyModel.add(strategyPattern, hasName, "策略模式");
            
            Resource templatePattern = ontologyModel.createResource(namespace + "TemplateMethodPattern");
            ontologyModel.add(templatePattern, RDF.type, designPatternClass);
            ontologyModel.add(templatePattern, hasName, "模板方法模式");
            
            // 添加关系
            ontologyModel.add(srp, appliesTo, moduleDesign);
            ontologyModel.add(ocp, appliesTo, refactoring);
            ontologyModel.add(ocp, appliesTo, moduleDesign);
            
            ontologyModel.add(strategyPattern, relatesTo, templatePattern);
            
            log.info("初始本体模型创建完成");
            
            // 保存本体模型
            saveOntology();
        } catch (Exception e) {
            log.error("创建初始本体模型失败", e);
        }
    }

    /**
     * 保存本体模型到文件
     */
    private void saveOntology() {
        try {
            File directory = new File("src/main/resources/ontology");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File file = new File("src/main/resources/" + ontologyPath);
            try (FileOutputStream out = new FileOutputStream(file)) {
                ontologyModel.write(out, "RDF/XML");
                log.info("本体模型已保存到: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("保存本体模型失败", e);
        }
    }

    @Override
    public List<Map<String, String>> inferPrincipleApplications(String principleName) {
        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX se: <" + namespace + ">\n" +
                "SELECT ?principleName ?scenarioName ?description\n" +
                "WHERE {\n" +
                "  ?principle rdf:type se:DesignPrinciple .\n" +
                "  ?principle se:hasName ?principleName .\n" +
                "  FILTER(regex(?principleName, \"" + principleName + "\", \"i\")) .\n" +
                "  ?principle se:appliesTo ?scenario .\n" +
                "  ?scenario se:hasName ?scenarioName .\n" +
                "  OPTIONAL { ?scenario se:hasDescription ?description }\n" +
                "}";
        
        return executeQuery(queryString);
    }

    @Override
    public List<Map<String, String>> inferPatternInheritance(String patternName) {
        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX se: <" + namespace + ">\n" +
                "SELECT ?patternName ?parentName ?description\n" +
                "WHERE {\n" +
                "  ?pattern rdf:type se:DesignPattern .\n" +
                "  ?pattern se:hasName ?patternName .\n" +
                "  FILTER(regex(?patternName, \"" + patternName + "\", \"i\")) .\n" +
                "  ?pattern se:inheritsFrom ?parent .\n" +
                "  ?parent se:hasName ?parentName .\n" +
                "  OPTIONAL { ?parent se:hasDescription ?description }\n" +
                "}";
        
        return executeQuery(queryString);
    }

    @Override
    public List<Map<String, String>> inferRelatedPatterns(String patternName) {
        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX se: <" + namespace + ">\n" +
                "SELECT ?patternName ?relatedName ?description\n" +
                "WHERE {\n" +
                "  ?pattern rdf:type se:DesignPattern .\n" +
                "  ?pattern se:hasName ?patternName .\n" +
                "  FILTER(regex(?patternName, \"" + patternName + "\", \"i\")) .\n" +
                "  ?pattern se:relatesTo ?related .\n" +
                "  ?related se:hasName ?relatedName .\n" +
                "  OPTIONAL { ?related se:hasDescription ?description }\n" +
                "}";
        
        return executeQuery(queryString);
    }

    @Override
    public boolean addKnowledgeToOntology(String title, String content, String category) {
        try {
            // 提取概念
            List<String> concepts = nlpService.extractConcepts(content, 10);
            
            // 提取关系
            List<NlpService.Relationship> relationships = nlpService.extractRelationships(content);
            
            // 根据分类确定资源类型
            String resourceType;
            if (category.contains("设计模式")) {
                resourceType = "DesignPattern";
            } else if (category.contains("原则")) {
                resourceType = "DesignPrinciple";
            } else if (category.contains("场景") || category.contains("应用")) {
                resourceType = "ApplicationScenario";
            } else {
                resourceType = "KnowledgeResource";
            }
            
            // 创建资源
            Resource resource = ontologyModel.createResource(namespace + title.replaceAll("\\s+", ""));
            Resource resourceClass = ontologyModel.getResource(namespace + resourceType);
            Property hasName = ontologyModel.getProperty(namespace + "hasName");
            Property hasDescription = ontologyModel.getProperty(namespace + "hasDescription");
            
            // 添加类型和属性
            ontologyModel.add(resource, RDF.type, resourceClass);
            ontologyModel.add(resource, hasName, title);
            
            // 添加描述（使用内容的前200个字符作为描述）
            String description = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            ontologyModel.add(resource, hasDescription, description);
            
            // 添加概念
            for (String concept : concepts) {
                Resource conceptResource = ontologyModel.createResource(namespace + concept.replaceAll("\\s+", ""));
                ontologyModel.add(conceptResource, hasName, concept);
                
                // 添加关系
                Property relatesTo = ontologyModel.getProperty(namespace + "relatesTo");
                ontologyModel.add(resource, relatesTo, conceptResource);
            }
            
            // 添加关系
            for (NlpService.Relationship relationship : relationships) {
                String sourceEntity = relationship.getSourceEntity();
                String relationType = relationship.getRelationType();
                String targetEntity = relationship.getTargetEntity();
                
                // 创建或获取源实体和目标实体
                Resource sourceResource = ontologyModel.createResource(namespace + sourceEntity.replaceAll("\\s+", ""));
                Resource targetResource = ontologyModel.createResource(namespace + targetEntity.replaceAll("\\s+", ""));
                
                // 添加名称
                ontologyModel.add(sourceResource, hasName, sourceEntity);
                ontologyModel.add(targetResource, hasName, targetEntity);
                
                // 创建关系属性
                Property relationProperty = ontologyModel.createProperty(namespace + relationType.replaceAll("\\s+", ""));
                
                // 添加关系
                ontologyModel.add(sourceResource, relationProperty, targetResource);
            }
            
            // 保存本体模型
            saveOntology();
            
            return true;
        } catch (Exception e) {
            log.error("添加知识到本体模型失败", e);
            return false;
        }
    }

    @Override
    public List<Map<String, String>> executeQuery(String queryString) {
        try {
            List<Map<String, String>> results = new ArrayList<>();
            
            // 创建查询
            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, ontologyModel)) {
                ResultSet resultSet = qexec.execSelect();
                
                // 处理结果
                while (resultSet.hasNext()) {
                    QuerySolution solution = resultSet.nextSolution();
                    Map<String, String> row = new HashMap<>();
                    
                    // 获取查询变量
                    for (String varName : resultSet.getResultVars()) {
                        RDFNode node = solution.get(varName);
                        if (node != null) {
                            if (node.isLiteral()) {
                                row.put(varName, node.asLiteral().getString());
                            } else {
                                row.put(varName, node.toString());
                            }
                        }
                    }
                    
                    results.add(row);
                }
            }
            
            return results;
        } catch (Exception e) {
            log.error("执行SPARQL查询失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<List<Object>> findReasoningPaths(String startConcept, int reasoningDepth) {
        List<List<Object>> paths = new ArrayList<>();
        
        // 构建SPARQL查询
        String queryString = buildReasoningPathQuery(startConcept, reasoningDepth);
        
        try {
            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, ontologyModel)) {
                ResultSet resultSet = qexec.execSelect();
                while (resultSet.hasNext()) {
                    QuerySolution solution = resultSet.nextSolution();
                    List<Object> path = new ArrayList<>();
                    
                    // 添加起始概念
                    path.add(startConcept);
                    
                    // 添加路径中的其他节点和关系
                    for (int i = 1; i <= reasoningDepth; i++) {
                        String relationVarName = "rel" + i;
                        String conceptVarName = "concept" + i;
                        
                        if (solution.contains(relationVarName)) {
                            path.add(solution.get(relationVarName).toString());
                        }
                        
                        if (solution.contains(conceptVarName)) {
                            path.add(solution.get(conceptVarName).toString());
                        } else {
                            break;
                        }
                    }
                    
                    paths.add(path);
                }
            }
        } catch (Exception e) {
            log.error("查找推理路径失败", e);
        }
        
        return paths;
    }

    @Override
    public Map<String, Object> executeQuery(String startConcept, String query) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 替换查询中的参数
            String finalQuery = query.replace("${concept}", startConcept);
            
            Query sparqlQuery = QueryFactory.create(finalQuery);
            try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, ontologyModel)) {
                ResultSet resultSet = qexec.execSelect();
                
                List<Map<String, String>> rows = new ArrayList<>();
                while (resultSet.hasNext()) {
                    QuerySolution solution = resultSet.nextSolution();
                    Map<String, String> row = new HashMap<>();
                    
                    solution.varNames().forEachRemaining(varName -> {
                        if (solution.get(varName) != null) {
                            row.put(varName, solution.get(varName).toString());
                        }
                    });
                    
                    rows.add(row);
                }
                
                result.put("rows", rows);
                result.put("count", rows.size());
            }
        } catch (Exception e) {
            log.error("执行查询失败: {}", query, e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 构建用于查找推理路径的SPARQL查询
     * 
     * @param startConcept 起始概念
     * @param depth 推理深度
     * @return SPARQL查询字符串
     */
    private String buildReasoningPathQuery(String startConcept, int depth) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("PREFIX se: <http://www.semanticweb.org/software-engineering#>\n");
        queryBuilder.append("SELECT ");
        
        // 添加变量
        for (int i = 1; i <= depth; i++) {
            queryBuilder.append("?rel").append(i).append(" ?concept").append(i).append(" ");
        }
        
        queryBuilder.append("\nWHERE {\n");
        queryBuilder.append("  ?start se:name \"").append(startConcept).append("\" .\n");
        
        // 构建路径模式
        for (int i = 1; i <= depth; i++) {
            if (i == 1) {
                queryBuilder.append("  ?start ?rel1 ?concept1 .\n");
            } else {
                queryBuilder.append("  ?concept").append(i-1)
                        .append(" ?rel").append(i)
                        .append(" ?concept").append(i).append(" .\n");
            }
        }
        
        queryBuilder.append("}");
        return queryBuilder.toString();
    }
} 