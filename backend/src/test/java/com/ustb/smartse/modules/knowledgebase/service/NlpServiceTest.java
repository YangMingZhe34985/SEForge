package com.ustb.smartse.modules.knowledgebase.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NlpServiceTest {

    @Autowired
    private NlpService nlpService;

    @Test
    @DisplayName("测试关系提取功能")
    public void testExtractRelationships() {
        // 准备测试文本
        String text = "单例模式确保一个类只有一个实例，并提供一个全局访问点。" +
                "工厂模式定义了一个创建对象的接口，但由子类决定要实例化的类是哪一个。" +
                "观察者模式定义了对象之间的一对多依赖关系，当一个对象改变状态时，它的所有依赖者都会收到通知并自动更新。" +
                "适配器模式使得原本由于接口不兼容而不能一起工作的那些类可以一起工作。" +
                "开闭原则要求软件实体应该对扩展开放，对修改关闭。" +
                "单一职责原则规定一个类应该只有一个发生变化的原因。" +
                "依赖倒置原则要求高层模块不应该依赖低层模块，两者都应该依赖抽象。";

        // 执行关系提取
        List<NlpService.Relationship> relationships = nlpService.extractRelationships(text);

        // 验证结果
        assertNotNull(relationships, "提取的关系列表不应为空");
        assertFalse(relationships.isEmpty(), "提取的关系列表不应为空");

        // 输出提取到的关系，便于调试
        System.out.println("提取到的关系数量: " + relationships.size());
        for (NlpService.Relationship rel : relationships) {
            System.out.println(rel.getSourceEntity() + " --[" + rel.getRelationType() + "]--> " + rel.getTargetEntity());
        }

        // 验证是否提取到了预期的关系
        boolean foundSingletonRelation = false;
        boolean foundFactoryRelation = false;
        boolean foundObserverRelation = false;
        boolean foundAdapterRelation = false;

        for (NlpService.Relationship rel : relationships) {
            if (rel.getSourceEntity().contains("单例模式") && rel.getTargetEntity().contains("实例")) {
                foundSingletonRelation = true;
            } else if (rel.getSourceEntity().contains("工厂模式") && rel.getTargetEntity().contains("接口")) {
                foundFactoryRelation = true;
            } else if (rel.getSourceEntity().contains("观察者模式") && rel.getTargetEntity().contains("依赖关系")) {
                foundObserverRelation = true;
            } else if (rel.getSourceEntity().contains("适配器模式") && rel.getTargetEntity().contains("接口")) {
                foundAdapterRelation = true;
            }
        }

        // 至少应该提取到一个预期的关系
        assertTrue(foundSingletonRelation || foundFactoryRelation || foundObserverRelation || foundAdapterRelation,
                "应该至少提取到一个有关设计模式的关系");
    }

    @Test
    @DisplayName("测试概念提取功能")
    public void testExtractConcepts() {
        // 准备测试文本
        String text = "软件工程是一门应用计算机科学、数学及管理学等原理，以系统化、规范化、可度量的方法来开发和维护软件的工程学科。" +
                "软件设计模式是软件开发人员在软件开发过程中面临的一般问题的解决方案。" +
                "设计模式是一套被反复使用、多数人知晓的、经过分类编目的、代码设计经验的总结。" +
                "使用设计模式是为了重用代码、让代码更容易被他人理解、保证代码可靠性。";

        // 执行概念提取
        List<String> concepts = nlpService.extractConcepts(text, 10);

        // 验证结果
        assertNotNull(concepts, "提取的概念列表不应为空");
        assertFalse(concepts.isEmpty(), "提取的概念列表不应为空");

        // 输出提取到的概念，便于调试
        System.out.println("提取到的概念数量: " + concepts.size());
        for (String concept : concepts) {
            System.out.println("- " + concept);
        }

        // 验证是否提取到了预期的概念
        assertTrue(concepts.stream().anyMatch(c -> c.contains("软件工程")), "应该提取到'软件工程'概念");
        assertTrue(concepts.stream().anyMatch(c -> c.contains("设计模式")), "应该提取到'设计模式'概念");
    }

    @Test
    @DisplayName("测试实体提取功能")
    public void testExtractEntities() {
        // 准备测试文本
        String text = "软件架构是有关软件整体结构与组件的抽象描述，用于指导大型软件系统各个方面的设计。" +
                "微服务架构是一种架构模式，它提倡将单一应用程序划分成一组小的服务，服务之间互相协调、互相配合，为用户提供最终价值。" +
                "领域驱动设计是一种软件开发方法，它将实现聚焦在核心领域概念上。";

        // 执行实体提取
        Map<String, List<String>> entities = nlpService.extractEntities(text);

        // 验证结果
        assertNotNull(entities, "提取的实体映射不应为空");
        assertFalse(entities.isEmpty(), "提取的实体映射不应为空");

        // 输出提取到的实体，便于调试
        System.out.println("提取到的实体类型数量: " + entities.size());
        for (Map.Entry<String, List<String>> entry : entities.entrySet()) {
            System.out.println("类型: " + entry.getKey());
            for (String entity : entry.getValue()) {
                System.out.println("  - " + entity);
            }
        }

        // 验证是否提取到了预期的实体
        boolean foundArchitectureEntity = false;
        boolean foundMicroserviceEntity = false;
        boolean foundDDDEntity = false;

        for (Map.Entry<String, List<String>> entry : entities.entrySet()) {
            for (String entity : entry.getValue()) {
                if (entity.contains("软件架构")) {
                    foundArchitectureEntity = true;
                } else if (entity.contains("微服务架构")) {
                    foundMicroserviceEntity = true;
                } else if (entity.contains("领域驱动设计")) {
                    foundDDDEntity = true;
                }
            }
        }

        // 至少应该提取到一个预期的实体
        assertTrue(foundArchitectureEntity || foundMicroserviceEntity || foundDDDEntity,
                "应该至少提取到一个有关软件架构的实体");
    }
} 