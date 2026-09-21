package com.ustb.smartse.modules.knowledgebase.utils;

import com.ustb.smartse.modules.knowledgebase.dto.BatchImportDTO;
import com.ustb.smartse.modules.knowledgebase.dto.ConceptNodeDTO;
import com.ustb.smartse.modules.knowledgebase.dto.RelationDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 软件工程领域知识图谱预设数据
 */
@Slf4j
public class SoftwareEngineeringKnowledgePresets {

    /**
     * 获取软件工程基础概念预设数据
     *
     * @return 批量导入数据对象
     */
    public static BatchImportDTO getBasicConcepts() {
        List<ConceptNodeDTO> concepts = new ArrayList<>();
        List<RelationDTO> relations = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 软件工程核心概念
        concepts.add(ConceptNodeDTO.builder()
                .name("软件工程")
                .definition("应用系统化、规范化和可量化的方法来开发、运行和维护软件的工程学科")
                .categories(Arrays.asList("核心概念", "学科"))
                .example("软件工程涵盖了软件开发的全生命周期，包括需求分析、设计、编码、测试、部署和维护等阶段")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("软件开发生命周期")
                .definition("软件的规划、创建、测试和部署的过程")
                .categories(Arrays.asList("过程", "方法论"))
                .example("瀑布模型、敏捷开发、螺旋模型都是不同的软件开发生命周期模型")
                .createTime(now)
                .updateTime(now)
                .build());

        // 软件开发方法论
        concepts.add(ConceptNodeDTO.builder()
                .name("瀑布模型")
                .definition("一种线性顺序的软件开发模型，每个阶段完成后才能进入下一个阶段")
                .categories(Arrays.asList("开发模型", "方法论"))
                .example("在瀑布模型中，开发阶段通常包括需求分析、系统设计、实现、测试、部署和维护")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("敏捷开发")
                .definition("强调以人为本、迭代、增量和适应性开发的软件开发方法")
                .categories(Arrays.asList("开发模型", "方法论"))
                .example("Scrum、XP（极限编程）和看板方法都是敏捷开发的具体实践")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("Scrum")
                .definition("一种迭代式增量软件开发过程，强调团队自组织和产品负责人的作用")
                .categories(Arrays.asList("敏捷方法", "过程框架"))
                .example("Scrum团队通常由产品负责人、Scrum Master和开发团队组成，他们在Sprint中完成工作")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增开发方法论
        concepts.add(ConceptNodeDTO.builder()
                .name("螺旋模型")
                .definition("结合了瀑布模型的系统性和原型开发的迭代性，特别强调风险分析")
                .categories(Arrays.asList("开发模型", "方法论"))
                .example("螺旋模型在每次迭代中包含计划、风险分析、工程实现和评审四个阶段")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("DevOps")
                .definition("结合软件开发(Dev)和IT运维(Ops)的实践，强调自动化和团队协作")
                .categories(Arrays.asList("开发模型", "方法论", "运维"))
                .example("持续集成、持续交付和基础设施即代码是DevOps的核心实践")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("极限编程")
                .definition("一种敏捷软件开发方法和工程实践，强调编程技术、清晰沟通和团队协作")
                .categories(Arrays.asList("敏捷方法", "编程实践"))
                .example("结对编程、测试驱动开发和持续集成是XP的核心实践")
                .createTime(now)
                .updateTime(now)
                .build());

        // 软件工程活动
        concepts.add(ConceptNodeDTO.builder()
                .name("需求工程")
                .definition("发现、分析、记录和验证软件需求的过程")
                .categories(Arrays.asList("软件工程活动", "过程"))
                .example("需求获取、需求分析、需求规格说明和需求验证是需求工程的主要活动")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("软件设计")
                .definition("定义软件系统的架构、组件、接口和其他特性的过程")
                .categories(Arrays.asList("软件工程活动", "过程"))
                .example("软件设计包括架构设计、详细设计和界面设计等")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("软件测试")
                .definition("评估和验证软件产品或服务质量的过程")
                .categories(Arrays.asList("软件工程活动", "过程"))
                .example("单元测试、集成测试、系统测试和验收测试是软件测试的不同级别")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增软件工程活动
        concepts.add(ConceptNodeDTO.builder()
                .name("软件维护")
                .definition("在软件交付后修改和更新软件以纠正缺陷并改进性能的过程")
                .categories(Arrays.asList("软件工程活动", "过程"))
                .example("软件维护包括纠正性维护、适应性维护、完善性维护和预防性维护")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("配置管理")
                .definition("识别、组织和控制软件变更的过程，确保系统完整性和可追溯性")
                .categories(Arrays.asList("软件工程活动", "过程"))
                .example("版本控制、变更控制和构建管理是配置管理的主要活动")
                .createTime(now)
                .updateTime(now)
                .build());

        // 软件架构
        concepts.add(ConceptNodeDTO.builder()
                .name("软件架构")
                .definition("软件系统的基本结构，包括软件元素、它们之间的关系以及软件设计和演化的原理")
                .categories(Arrays.asList("设计", "结构"))
                .example("MVC、微服务和分层架构都是常见的软件架构模式")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增软件架构模式
        concepts.add(ConceptNodeDTO.builder()
                .name("微服务架构")
                .definition("将应用程序构建为一系列小型、自治的服务，每个服务运行在自己的进程中")
                .categories(Arrays.asList("架构模式", "分布式系统"))
                .example("Netflix和Amazon等公司广泛采用微服务架构来提高系统可扩展性和弹性")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("MVC模式")
                .definition("将应用程序分为模型(Model)、视图(View)和控制器(Controller)三个组件")
                .categories(Arrays.asList("架构模式", "设计模式"))
                .example("许多Web框架如Spring MVC、Ruby on Rails和Django都采用MVC模式")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("设计模式")
                .definition("解决软件设计中常见问题的可复用解决方案")
                .categories(Arrays.asList("设计", "最佳实践"))
                .example("单例模式、工厂模式和观察者模式是常见的设计模式")
                .createTime(now)
                .updateTime(now)
                .build());

        // 软件质量
        concepts.add(ConceptNodeDTO.builder()
                .name("软件质量")
                .definition("软件产品满足明确和隐含需求的能力")
                .categories(Arrays.asList("质量", "属性"))
                .example("可靠性、可用性、可维护性、性能和安全性都是软件质量的重要特性")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("代码重构")
                .definition("在不改变软件外部行为的前提下，改善其内部结构的过程")
                .categories(Arrays.asList("质量改进", "最佳实践"))
                .example("提取方法、重命名变量和简化条件表达式都是常见的重构技术")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增软件质量概念
        concepts.add(ConceptNodeDTO.builder()
                .name("技术债务")
                .definition("在软件开发过程中为了快速交付而做出的妥协决策所累积的隐性成本")
                .categories(Arrays.asList("质量", "项目管理"))
                .example("使用临时解决方案、延迟重构和文档不足都会导致技术债务")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("持续集成")
                .definition("开发人员频繁地将代码集成到共享仓库，每次集成都通过自动化构建和测试进行验证")
                .categories(Arrays.asList("开发实践", "DevOps"))
                .example("Jenkins、GitHub Actions和GitLab CI是常用的持续集成工具")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("持续交付")
                .definition("确保软件可以随时可靠地发布到生产环境的实践")
                .categories(Arrays.asList("开发实践", "DevOps"))
                .example("自动化部署流水线、环境配置管理和发布管理是持续交付的关键要素")
                .createTime(now)
                .updateTime(now)
                .build());

        // 构建关系
        // 软件工程的组成部分
        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("软件开发生命周期")
                .relationType("包含")
                .confidence(1.0)
                .example("软件工程学科包含对软件开发生命周期的研究和应用")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("需求工程")
                .relationType("包含")
                .confidence(1.0)
                .example("软件工程过程中的第一个关键阶段通常是需求工程")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("软件设计")
                .relationType("包含")
                .confidence(1.0)
                .example("软件设计是软件工程中的核心活动之一")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("软件测试")
                .relationType("包含")
                .confidence(1.0)
                .example("软件测试是软件工程中确保质量的关键环节")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("软件架构")
                .relationType("使用")
                .confidence(1.0)
                .example("软件工程中使用软件架构来指导系统的整体设计")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增软件工程关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("软件维护")
                .relationType("包含")
                .confidence(1.0)
                .example("软件维护是软件工程生命周期中最长的阶段")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件工程")
                .targetNode("配置管理")
                .relationType("使用")
                .confidence(1.0)
                .example("软件工程过程中使用配置管理来控制变更和维护系统完整性")
                .createTime(now)
                .updateTime(now)
                .build());

        // 软件开发模型关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件开发生命周期")
                .targetNode("瀑布模型")
                .relationType("包含")
                .confidence(1.0)
                .example("瀑布模型是最早形式化的软件开发生命周期模型之一")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件开发生命周期")
                .targetNode("敏捷开发")
                .relationType("包含")
                .confidence(1.0)
                .example("敏捷开发是一种迭代式的软件开发生命周期模型")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("敏捷开发")
                .targetNode("Scrum")
                .relationType("包含")
                .confidence(1.0)
                .example("Scrum是最流行的敏捷开发框架之一")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增开发模型关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件开发生命周期")
                .targetNode("螺旋模型")
                .relationType("包含")
                .confidence(1.0)
                .example("螺旋模型是一种风险驱动的软件开发生命周期模型")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件开发生命周期")
                .targetNode("DevOps")
                .relationType("演化")
                .confidence(0.9)
                .example("DevOps可以看作是软件开发生命周期的现代演化")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("敏捷开发")
                .targetNode("极限编程")
                .relationType("包含")
                .confidence(1.0)
                .example("极限编程是最早的敏捷方法之一")
                .createTime(now)
                .updateTime(now)
                .build());

        // 设计相关关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件设计")
                .targetNode("软件架构")
                .relationType("产生")
                .confidence(1.0)
                .example("软件设计过程中会产生软件架构")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件设计")
                .targetNode("设计模式")
                .relationType("使用")
                .confidence(1.0)
                .example("在软件设计中经常使用设计模式来解决常见问题")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增架构关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件架构")
                .targetNode("微服务架构")
                .relationType("包含")
                .confidence(1.0)
                .example("微服务架构是一种流行的软件架构风格")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件架构")
                .targetNode("MVC模式")
                .relationType("包含")
                .confidence(1.0)
                .example("MVC是一种常见的软件架构模式")
                .createTime(now)
                .updateTime(now)
                .build());

        // 质量相关关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("软件质量")
                .relationType("提高")
                .confidence(1.0)
                .example("软件测试的主要目标是提高软件质量")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("代码重构")
                .targetNode("软件质量")
                .relationType("提高")
                .confidence(1.0)
                .example("代码重构通过改善代码结构来提高软件质量")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增质量关系
        relations.add(RelationDTO.builder()
                .sourceNode("技术债务")
                .targetNode("软件质量")
                .relationType("降低")
                .confidence(1.0)
                .example("技术债务累积会降低软件质量")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("持续集成")
                .targetNode("软件质量")
                .relationType("提高")
                .confidence(1.0)
                .example("持续集成通过早期发现问题来提高软件质量")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("持续交付")
                .targetNode("软件质量")
                .relationType("提高")
                .confidence(0.9)
                .example("持续交付通过自动化流程减少人为错误，提高软件质量")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("DevOps")
                .targetNode("持续集成")
                .relationType("使用")
                .confidence(1.0)
                .example("持续集成是DevOps实践的核心组成部分")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("DevOps")
                .targetNode("持续交付")
                .relationType("使用")
                .confidence(1.0)
                .example("持续交付是DevOps实践的关键环节")
                .createTime(now)
                .updateTime(now)
                .build());

        return BatchImportDTO.builder()
                .concepts(concepts)
                .relations(relations)
                .build();
    }

    /**
     * 获取软件设计模式预设数据
     *
     * @return 批量导入数据对象
     */
    public static BatchImportDTO getDesignPatterns() {
        List<ConceptNodeDTO> concepts = new ArrayList<>();
        List<RelationDTO> relations = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 设计模式分类
        concepts.add(ConceptNodeDTO.builder()
                .name("创建型模式")
                .definition("处理对象创建机制的设计模式，试图以适合特定情况的方式创建对象")
                .categories(Arrays.asList("设计模式", "分类"))
                .example("单例模式、工厂方法模式、抽象工厂模式、建造者模式和原型模式")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("结构型模式")
                .definition("处理类和对象组合的设计模式，关注类和对象如何组合以形成更大的结构")
                .categories(Arrays.asList("设计模式", "分类"))
                .example("适配器模式、桥接模式、组合模式、装饰器模式、外观模式、享元模式和代理模式")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("行为型模式")
                .definition("处理对象之间责任分配的设计模式，关注对象之间的通信方式")
                .categories(Arrays.asList("设计模式", "分类"))
                .example("观察者模式、策略模式、命令模式、模板方法模式和迭代器模式")
                .createTime(now)
                .updateTime(now)
                .build());

        // 常见设计模式
        concepts.add(ConceptNodeDTO.builder()
                .name("单例模式")
                .definition("确保一个类只有一个实例，并提供一个全局访问点")
                .categories(Arrays.asList("设计模式", "创建型模式"))
                .example("数据库连接池、线程池和配置管理器通常使用单例模式实现")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("工厂方法模式")
                .definition("定义一个用于创建对象的接口，但让子类决定实例化哪个类")
                .categories(Arrays.asList("设计模式", "创建型模式"))
                .example("日志记录器工厂可以根据配置创建不同类型的日志记录器")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增创建型模式
        concepts.add(ConceptNodeDTO.builder()
                .name("抽象工厂模式")
                .definition("提供一个接口，用于创建相关或依赖对象的家族，而不指定具体类")
                .categories(Arrays.asList("设计模式", "创建型模式"))
                .example("GUI工具包使用抽象工厂创建不同操作系统下的界面组件")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("建造者模式")
                .definition("将一个复杂对象的构建与它的表示分离，使得同样的构建过程可以创建不同的表示")
                .categories(Arrays.asList("设计模式", "创建型模式"))
                .example("StringBuilder类和文档转换器是建造者模式的例子")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("原型模式")
                .definition("通过复制现有的实例来创建新的实例，而不是通过实例化类")
                .categories(Arrays.asList("设计模式", "创建型模式"))
                .example("Java中的clone()方法实现了原型模式")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("观察者模式")
                .definition("定义对象间的一种一对多依赖关系，使得当一个对象状态改变时，所有依赖它的对象都会得到通知并自动更新")
                .categories(Arrays.asList("设计模式", "行为型模式"))
                .example("事件监听系统、消息发布订阅机制和MVC架构中的视图更新")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("策略模式")
                .definition("定义一系列算法，将每个算法封装起来，并使它们可以互换")
                .categories(Arrays.asList("设计模式", "行为型模式"))
                .example("排序算法选择、支付方式选择和验证策略选择")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增行为型模式
        concepts.add(ConceptNodeDTO.builder()
                .name("命令模式")
                .definition("将请求封装成对象，从而使用户可以参数化不同的请求、队列或日志请求，以及支持可撤销的操作")
                .categories(Arrays.asList("设计模式", "行为型模式"))
                .example("GUI中的按钮点击事件、事务处理和命令队列")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("迭代器模式")
                .definition("提供一种方法顺序访问一个聚合对象中的各个元素，而不暴露其内部表示")
                .categories(Arrays.asList("设计模式", "行为型模式"))
                .example("Java中的Iterator接口和foreach循环")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("模板方法模式")
                .definition("定义一个操作中的算法骨架，将一些步骤延迟到子类中实现")
                .categories(Arrays.asList("设计模式", "行为型模式"))
                .example("框架中的钩子方法和Java中的抽象类")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("适配器模式")
                .definition("将一个类的接口转换成客户希望的另外一个接口，使得原本由于接口不兼容而不能一起工作的类可以一起工作")
                .categories(Arrays.asList("设计模式", "结构型模式"))
                .example("数据格式转换、第三方库集成和旧系统接口适配")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增结构型模式
        concepts.add(ConceptNodeDTO.builder()
                .name("装饰器模式")
                .definition("动态地给一个对象添加一些额外的职责，比子类更灵活")
                .categories(Arrays.asList("设计模式", "结构型模式"))
                .example("Java I/O流类和GUI组件装饰")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("代理模式")
                .definition("为其他对象提供一种代理以控制对这个对象的访问")
                .categories(Arrays.asList("设计模式", "结构型模式"))
                .example("远程代理、虚拟代理和保护代理")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("组合模式")
                .definition("将对象组合成树形结构以表示'部分-整体'的层次结构")
                .categories(Arrays.asList("设计模式", "结构型模式"))
                .example("文件系统、GUI组件树和组织架构")
                .createTime(now)
                .updateTime(now)
                .build());

        // 设计原则
        concepts.add(ConceptNodeDTO.builder()
                .name("SOLID原则")
                .definition("面向对象设计的五个基本原则的首字母缩写")
                .categories(Arrays.asList("设计原则", "最佳实践"))
                .example("单一职责原则、开闭原则、里氏替换原则、接口隔离原则和依赖倒置原则")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("开闭原则")
                .definition("软件实体应该对扩展开放，对修改关闭")
                .categories(Arrays.asList("设计原则", "SOLID"))
                .example("使用抽象和多态来实现功能扩展而不修改现有代码")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("单一职责原则")
                .definition("一个类应该只有一个引起它变化的原因")
                .categories(Arrays.asList("设计原则", "SOLID"))
                .example("将复杂类拆分为多个职责单一的类")
                .createTime(now)
                .updateTime(now)
                .build());

        // 建立关系
        relations.add(RelationDTO.builder()
                .sourceNode("设计模式")
                .targetNode("创建型模式")
                .relationType("包含")
                .confidence(1.0)
                .example("设计模式按照意图可以分为创建型、结构型和行为型三类")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("设计模式")
                .targetNode("结构型模式")
                .relationType("包含")
                .confidence(1.0)
                .example("设计模式按照意图可以分为创建型、结构型和行为型三类")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("设计模式")
                .targetNode("行为型模式")
                .relationType("包含")
                .confidence(1.0)
                .example("设计模式按照意图可以分为创建型、结构型和行为型三类")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("创建型模式")
                .targetNode("单例模式")
                .relationType("包含")
                .confidence(1.0)
                .example("单例模式是最简单的创建型设计模式之一")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("创建型模式")
                .targetNode("工厂方法模式")
                .relationType("包含")
                .confidence(1.0)
                .example("工厂方法模式是最常用的创建型设计模式之一")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增创建型模式关系
        relations.add(RelationDTO.builder()
                .sourceNode("创建型模式")
                .targetNode("抽象工厂模式")
                .relationType("包含")
                .confidence(1.0)
                .example("抽象工厂模式是创建型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("创建型模式")
                .targetNode("建造者模式")
                .relationType("包含")
                .confidence(1.0)
                .example("建造者模式是创建型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("创建型模式")
                .targetNode("原型模式")
                .relationType("包含")
                .confidence(1.0)
                .example("原型模式是创建型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("行为型模式")
                .targetNode("观察者模式")
                .relationType("包含")
                .confidence(1.0)
                .example("观察者模式是使用最广泛的行为型设计模式之一")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("行为型模式")
                .targetNode("策略模式")
                .relationType("包含")
                .confidence(1.0)
                .example("策略模式是一种常用的行为型设计模式")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增行为型模式关系
        relations.add(RelationDTO.builder()
                .sourceNode("行为型模式")
                .targetNode("命令模式")
                .relationType("包含")
                .confidence(1.0)
                .example("命令模式是行为型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("行为型模式")
                .targetNode("迭代器模式")
                .relationType("包含")
                .confidence(1.0)
                .example("迭代器模式是行为型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("行为型模式")
                .targetNode("模板方法模式")
                .relationType("包含")
                .confidence(1.0)
                .example("模板方法模式是行为型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("结构型模式")
                .targetNode("适配器模式")
                .relationType("包含")
                .confidence(1.0)
                .example("适配器模式是一种常用的结构型设计模式")
                .createTime(now)
                .updateTime(now)
                .build());

        // 新增结构型模式关系
        relations.add(RelationDTO.builder()
                .sourceNode("结构型模式")
                .targetNode("装饰器模式")
                .relationType("包含")
                .confidence(1.0)
                .example("装饰器模式是结构型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("结构型模式")
                .targetNode("代理模式")
                .relationType("包含")
                .confidence(1.0)
                .example("代理模式是结构型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("结构型模式")
                .targetNode("组合模式")
                .relationType("包含")
                .confidence(1.0)
                .example("组合模式是结构型设计模式的一种")
                .createTime(now)
                .updateTime(now)
                .build());

        // 设计原则关系
        relations.add(RelationDTO.builder()
                .sourceNode("设计模式")
                .targetNode("SOLID原则")
                .relationType("遵循")
                .confidence(0.9)
                .example("好的设计模式通常遵循SOLID设计原则")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("SOLID原则")
                .targetNode("开闭原则")
                .relationType("包含")
                .confidence(1.0)
                .example("开闭原则是SOLID原则中的O")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("SOLID原则")
                .targetNode("单一职责原则")
                .relationType("包含")
                .confidence(1.0)
                .example("单一职责原则是SOLID原则中的S")
                .createTime(now)
                .updateTime(now)
                .build());

        // 模式之间的关系
        relations.add(RelationDTO.builder()
                .sourceNode("工厂方法模式")
                .targetNode("抽象工厂模式")
                .relationType("相关")
                .confidence(0.9)
                .example("抽象工厂模式通常使用工厂方法模式来实现")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("装饰器模式")
                .targetNode("代理模式")
                .relationType("相似")
                .confidence(0.8)
                .example("装饰器模式和代理模式都是包装另一个对象，但目的不同")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("策略模式")
                .targetNode("命令模式")
                .relationType("对比")
                .confidence(0.7)
                .example("策略模式关注算法的选择，命令模式关注请求的封装")
                .createTime(now)
                .updateTime(now)
                .build());

        return BatchImportDTO.builder()
                .concepts(concepts)
                .relations(relations)
                .build();
    }

    /**
     * 获取软件测试预设数据
     *
     * @return 批量导入数据对象
     */
    public static BatchImportDTO getSoftwareTesting() {
        List<ConceptNodeDTO> concepts = new ArrayList<>();
        List<RelationDTO> relations = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 测试类型
        concepts.add(ConceptNodeDTO.builder()
                .name("单元测试")
                .definition("验证软件中的最小可测试单元是否按照预期工作的测试")
                .categories(Arrays.asList("测试类型", "开发测试"))
                .example("使用JUnit测试Java类的方法")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("集成测试")
                .definition("验证软件组件或子系统之间交互是否正确的测试")
                .categories(Arrays.asList("测试类型", "开发测试"))
                .example("测试数据库访问层与业务逻辑层之间的交互")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("系统测试")
                .definition("验证整个软件系统是否符合规格说明的测试")
                .categories(Arrays.asList("测试类型", "QA测试"))
                .example("验证电子商务网站的订单流程是否按照需求运行")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("验收测试")
                .definition("验证软件是否满足用户需求和可被接受的测试")
                .categories(Arrays.asList("测试类型", "用户测试"))
                .example("用户使用新系统完成关键业务任务的测试")
                .createTime(now)
                .updateTime(now)
                .build());

        // 测试方法
        concepts.add(ConceptNodeDTO.builder()
                .name("黑盒测试")
                .definition("不考虑内部结构，只关注输入和预期输出的测试方法")
                .categories(Arrays.asList("测试方法", "功能测试"))
                .example("使用等价类划分和边界值分析测试登录功能")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("白盒测试")
                .definition("基于程序内部结构和逻辑的测试方法")
                .categories(Arrays.asList("测试方法", "结构测试"))
                .example("代码覆盖率测试，确保所有代码路径都被执行到")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("回归测试")
                .definition("验证最近的程序或环境变更没有对现有功能产生负面影响的测试")
                .categories(Arrays.asList("测试方法", "维护测试"))
                .example("在修复bug或添加新功能后，重新运行之前的测试用例")
                .createTime(now)
                .updateTime(now)
                .build());

        // 测试自动化
        concepts.add(ConceptNodeDTO.builder()
                .name("测试自动化")
                .definition("使用特殊软件工具执行测试并比较实际结果与预期结果的过程")
                .categories(Arrays.asList("测试实践", "效率提升"))
                .example("使用Selenium自动化测试Web应用的UI交互")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("持续集成")
                .definition("频繁地将代码集成到共享仓库，并自动化构建和测试的实践")
                .categories(Arrays.asList("开发实践", "DevOps"))
                .example("使用Jenkins在每次代码提交后自动构建和测试项目")
                .createTime(now)
                .updateTime(now)
                .build());

        // 添加更多测试相关概念
        concepts.add(ConceptNodeDTO.builder()
                .name("性能测试")
                .definition("评估系统在特定负载条件下响应时间、吞吐量和资源利用率的测试")
                .categories(Arrays.asList("测试类型", "非功能测试"))
                .example("使用JMeter测试Web应用在1000个并发用户下的响应时间")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("安全测试")
                .definition("识别系统中可能存在的安全漏洞和风险的测试")
                .categories(Arrays.asList("测试类型", "非功能测试"))
                .example("使用OWASP ZAP进行Web应用的渗透测试")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("测试驱动开发")
                .definition("先编写测试用例，然后编写满足这些测试用例的代码的开发方法")
                .categories(Arrays.asList("开发方法", "敏捷实践"))
                .example("在实现功能前，先编写单元测试描述预期行为")
                .createTime(now)
                .updateTime(now)
                .build());
        // 版本控制
        concepts.add(ConceptNodeDTO.builder()
                .name("版本控制")
                .definition("跟踪和管理代码、文档等文件更改的系统")
                .categories(Arrays.asList("工具", "实践"))
                .example("Git、SVN 是常用的版本控制系统")
                .createTime(now)
                .updateTime(now)
                .build());

// 持续集成
        concepts.add(ConceptNodeDTO.builder()
                .name("持续集成")
                .definition("频繁地将代码合并到主干，并通过自动化构建与测试来验证")
                .categories(Arrays.asList("DevOps", "实践"))
                .example("Jenkins、GitHub Actions 可用于实现持续集成")
                .createTime(now)
                .updateTime(now)
                .build());

// 持续部署
        concepts.add(ConceptNodeDTO.builder()
                .name("持续部署")
                .definition("在持续集成基础上，将通过的构建自动部署到生产环境")
                .categories(Arrays.asList("DevOps", "实践"))
                .example("Spinnaker、Argo CD 支持持续部署流程")
                .createTime(now)
                .updateTime(now)
                .build());

// 测试驱动开发（TDD）
        concepts.add(ConceptNodeDTO.builder()
                .name("测试驱动开发")
                .definition("先编写测试用例，再开发满足测试的功能代码的开发方法")
                .categories(Arrays.asList("开发实践", "敏捷"))
                .example("JUnit、pytest 等框架可配合 TDD 使用")
                .createTime(now)
                .updateTime(now)
                .build());

// 行为驱动开发（BDD）
        concepts.add(ConceptNodeDTO.builder()
                .name("行为驱动开发")
                .definition("以用户行为或业务价值为导向的测试和开发方法")
                .categories(Arrays.asList("开发实践", "敏捷"))
                .example("Cucumber、SpecFlow 等工具可用于 BDD")
                .createTime(now)
                .updateTime(now)
                .build());

// 代码评审
        concepts.add(ConceptNodeDTO.builder()
                .name("代码评审")
                .definition("对同事提交的代码进行审查以发现缺陷并分享最佳实践")
                .categories(Arrays.asList("质量保障", "协作"))
                .example("GitHub Pull Request、Gerrit 都支持代码评审流程")
                .createTime(now)
                .updateTime(now)
                .build());

// 配置管理
        concepts.add(ConceptNodeDTO.builder()
                .name("配置管理")
                .definition("管理软件配置项及其版本的过程")
                .categories(Arrays.asList("工具", "实践"))
                .example("Ansible、Chef、Puppet 用于自动化配置管理")
                .createTime(now)
                .updateTime(now)
                .build());

// 容器化
        concepts.add(ConceptNodeDTO.builder()
                .name("容器化")
                .definition("使用轻量级、可移植的容器来打包和部署应用程序")
                .categories(Arrays.asList("部署", "技术"))
                .example("Docker、Podman 将应用及依赖封装到容器镜像中")
                .createTime(now)
                .updateTime(now)
                .build());

// 编排
        concepts.add(ConceptNodeDTO.builder()
                .name("容器编排")
                .definition("管理大规模容器生命周期和服务发现的自动化工具")
                .categories(Arrays.asList("部署", "技术"))
                .example("Kubernetes、Docker Swarm 用于容器编排")
                .createTime(now)
                .updateTime(now)
                .build());

// 安全运维（DevSecOps）
        concepts.add(ConceptNodeDTO.builder()
                .name("DevSecOps")
                .definition("在 DevOps 流程中集成安全实践的理念")
                .categories(Arrays.asList("DevOps", "安全"))
                .example("使用 SAST、DAST 工具在 CI/CD 中自动扫描安全漏洞")
                .createTime(now)
                .updateTime(now)
                .build());

// 软件度量
        concepts.add(ConceptNodeDTO.builder()
                .name("软件度量")
                .definition("通过关键指标（如代码复杂度、测试覆盖率）衡量软件质量和效率")
                .categories(Arrays.asList("质量管理", "分析"))
                .example("SonarQube 可用于收集和展示软件度量数据")
                .createTime(now)
                .updateTime(now)
                .build());

        // 建立关系
        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("单元测试")
                .relationType("包含")
                .confidence(1.0)
                .example("软件测试过程中最基础的测试级别是单元测试")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("集成测试")
                .relationType("包含")
                .confidence(1.0)
                .example("在单元测试之后通常进行集成测试")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("系统测试")
                .relationType("包含")
                .confidence(1.0)
                .example("系统测试验证整个软件系统的功能和非功能需求")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("验收测试")
                .relationType("包含")
                .confidence(1.0)
                .example("验收测试是软件测试的最后阶段，通常由客户或用户执行")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("黑盒测试")
                .relationType("使用")
                .confidence(1.0)
                .example("软件测试常使用黑盒测试方法验证功能正确性")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("白盒测试")
                .relationType("使用")
                .confidence(1.0)
                .example("软件测试中使用白盒测试确保代码覆盖率")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("测试自动化")
                .relationType("采用")
                .confidence(1.0)
                .example("现代软件测试实践中广泛采用测试自动化提高效率")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("测试自动化")
                .targetNode("持续集成")
                .relationType("支持")
                .confidence(1.0)
                .example("测试自动化是实现有效持续集成的关键要素")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("单元测试")
                .targetNode("白盒测试")
                .relationType("通常使用")
                .confidence(0.9)
                .example("单元测试通常采用白盒测试方法，关注代码内部逻辑")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("系统测试")
                .targetNode("黑盒测试")
                .relationType("通常使用")
                .confidence(0.9)
                .example("系统测试通常采用黑盒测试方法，关注整体功能")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("性能测试")
                .relationType("包含")
                .confidence(1.0)
                .example("性能测试是软件测试的一个重要分支，关注系统的性能特性")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("软件测试")
                .targetNode("安全测试")
                .relationType("包含")
                .confidence(1.0)
                .example("安全测试是软件测试的一个专门领域，关注系统的安全性")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("敏捷开发")
                .targetNode("测试驱动开发")
                .relationType("使用")
                .confidence(0.9)
                .example("测试驱动开发是敏捷开发中常用的实践方法")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("测试驱动开发")
                .targetNode("单元测试")
                .relationType("依赖")
                .confidence(1.0)
                .example("测试驱动开发主要依赖于单元测试来指导开发过程")
                .createTime(now)
                .updateTime(now)
                .build());
        // 版本控制 —— 使用 工具 —— 软件开发生命周期
        relations.add(RelationDTO.builder()
                .sourceNode("软件开发生命周期")
                .targetNode("版本控制")
                .relationType("使用")
                .confidence(1.0)
                .example("在各阶段的代码管理中，需要使用版本控制系统")
                .createTime(now)
                .updateTime(now)
                .build());

// 持续集成 —— 演化 自 敏捷开发
        relations.add(RelationDTO.builder()
                .sourceNode("敏捷开发")
                .targetNode("持续集成")
                .relationType("演化")
                .confidence(0.9)
                .example("持续集成是敏捷开发的一种自动化实践")
                .createTime(now)
                .updateTime(now)
                .build());

// 持续部署 —— 演化 自 持续交付
        relations.add(RelationDTO.builder()
                .sourceNode("持续交付")
                .targetNode("持续部署")
                .relationType("演化")
                .confidence(0.9)
                .example("持续部署在持续交付基础上实现自动上生产")
                .createTime(now)
                .updateTime(now)
                .build());

// 测试驱动开发 —— 提高 —— 软件质量
        relations.add(RelationDTO.builder()
                .sourceNode("测试驱动开发")
                .targetNode("软件质量")
                .relationType("提高")
                .confidence(1.0)
                .example("TDD 保证了代码的可测试性与可靠性，从而提高质量")
                .createTime(now)
                .updateTime(now)
                .build());

// 行为驱动开发 —— 扩展 —— 测试驱动开发
        relations.add(RelationDTO.builder()
                .sourceNode("测试驱动开发")
                .targetNode("行为驱动开发")
                .relationType("扩展")
                .confidence(0.9)
                .example("BDD 在 TDD 基础上更关注业务行为描述")
                .createTime(now)
                .updateTime(now)
                .build());

// 代码评审 —— 提高 —— 软件质量
        relations.add(RelationDTO.builder()
                .sourceNode("代码评审")
                .targetNode("软件质量")
                .relationType("提高")
                .confidence(1.0)
                .example("通过同行审查能早发现缺陷，提升代码质量")
                .createTime(now)
                .updateTime(now)
                .build());

// 容器化 —— 使用 —— 部署
        relations.add(RelationDTO.builder()
                .sourceNode("部署")
                .targetNode("容器化")
                .relationType("使用")
                .confidence(1.0)
                .example("容器化技术简化了部署流程")
                .createTime(now)
                .updateTime(now)
                .build());

// 容器编排 —— 扩展 —— 容器化
        relations.add(RelationDTO.builder()
                .sourceNode("容器化")
                .targetNode("容器编排")
                .relationType("扩展")
                .confidence(0.9)
                .example("容器编排在大规模部署中管理容器生命周期")
                .createTime(now)
                .updateTime(now)
                .build());

// DevSecOps —— 集成 —— DevOps
        relations.add(RelationDTO.builder()
                .sourceNode("DevOps")
                .targetNode("DevSecOps")
                .relationType("扩展")
                .confidence(0.9)
                .example("DevSecOps 在 DevOps 流程中引入安全扫描与合规检查")
                .createTime(now)
                .updateTime(now)
                .build());

// 软件度量 —— 支持 —— 持续改进
        relations.add(RelationDTO.builder()
                .sourceNode("软件度量")
                .targetNode("持续改进")
                .relationType("支持")
                .confidence(0.9)
                .example("通过度量数据指导持续改进实践")
                .createTime(now)
                .updateTime(now)
                .build());
        return BatchImportDTO.builder()
                .concepts(concepts)
                .relations(relations)
                .build();
    }

    /**
     * 获取人工智能和RAG相关概念预设数据
     *
     * @return 批量导入数据对象
     */
    public static BatchImportDTO getAIAndRAGConcepts() {
        List<ConceptNodeDTO> concepts = new ArrayList<>();
        List<RelationDTO> relations = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 核心概念
        concepts.add(ConceptNodeDTO.builder()
                .name("人工智能")
                .definition("研究和开发能够模拟、延伸和扩展人类智能的理论、方法、技术及应用系统的计算机科学分支")
                .categories(Arrays.asList("核心概念", "学科"))
                .example("机器学习、自然语言处理、计算机视觉和专家系统等都是人工智能的分支")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("机器学习")
                .definition("研究计算机如何在没有明确编程的情况下学习的科学")
                .categories(Arrays.asList("人工智能", "技术"))
                .example("监督学习、无监督学习和强化学习是机器学习的主要范式")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("深度学习")
                .definition("基于人工神经网络的机器学习子领域，通过多层次的数据表示学习")
                .categories(Arrays.asList("机器学习", "技术"))
                .example("卷积神经网络(CNN)和循环神经网络(RNN)是常见的深度学习架构")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("自然语言处理")
                .definition("研究计算机与人类语言交互的领域，特别是如何编程计算机以有效地处理大量自然语言数据")
                .categories(Arrays.asList("人工智能", "技术"))
                .example("机器翻译、情感分析和问答系统都是NLP的应用")
                .createTime(now)
                .updateTime(now)
                .build());

        // RAG相关概念
        concepts.add(ConceptNodeDTO.builder()
                .name("检索增强生成")
                .definition("一种结合信息检索和文本生成的技术，通过检索相关信息来增强生成模型的输出质量")
                .categories(Arrays.asList("人工智能", "技术", "NLP"))
                .example("基于用户问题检索相关文档，然后利用这些文档生成准确答案")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("向量数据库")
                .definition("专门用于存储和检索向量嵌入的数据库系统")
                .categories(Arrays.asList("数据库", "AI基础设施"))
                .example("Pinecone、Milvus和Faiss是常见的向量数据库")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("语义搜索")
                .definition("基于内容含义而非关键词匹配的搜索方法")
                .categories(Arrays.asList("信息检索", "NLP技术"))
                .example("使用向量嵌入计算查询和文档之间的语义相似度")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("知识图谱")
                .definition("以图结构组织和表示知识的数据库，包含实体及其关系")
                .categories(Arrays.asList("知识表示", "数据结构"))
                .example("Google Knowledge Graph和DBpedia是大规模知识图谱的例子")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("大语言模型")
                .definition("基于深度学习的大规模语言模型，能够理解和生成人类语言")
                .categories(Arrays.asList("人工智能", "NLP", "模型"))
                .example("GPT-4、Claude和Llama是知名的大语言模型")
                .createTime(now)
                .updateTime(now)
                .build());

        concepts.add(ConceptNodeDTO.builder()
                .name("嵌入模型")
                .definition("将文本、图像等数据转换为数值向量的模型，捕获语义信息")
                .categories(Arrays.asList("人工智能", "表示学习"))
                .example("Word2Vec、BERT Embeddings和Sentence Transformers")
                .createTime(now)
                .updateTime(now)
                .build());

        // 建立关系
        relations.add(RelationDTO.builder()
                .sourceNode("人工智能")
                .targetNode("机器学习")
                .relationType("包含")
                .confidence(1.0)
                .example("机器学习是人工智能的一个主要分支")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("机器学习")
                .targetNode("深度学习")
                .relationType("包含")
                .confidence(1.0)
                .example("深度学习是机器学习的一个子领域")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("人工智能")
                .targetNode("自然语言处理")
                .relationType("包含")
                .confidence(1.0)
                .example("自然语言处理是人工智能的一个重要应用领域")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("自然语言处理")
                .targetNode("检索增强生成")
                .relationType("应用")
                .confidence(1.0)
                .example("检索增强生成是自然语言处理的一种应用技术")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("检索增强生成")
                .targetNode("向量数据库")
                .relationType("使用")
                .confidence(1.0)
                .example("检索增强生成通常使用向量数据库进行高效的相似性搜索")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("检索增强生成")
                .targetNode("大语言模型")
                .relationType("使用")
                .confidence(1.0)
                .example("检索增强生成使用大语言模型来生成基于检索内容的回答")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("检索增强生成")
                .targetNode("语义搜索")
                .relationType("使用")
                .confidence(1.0)
                .example("检索增强生成依赖语义搜索来找到相关内容")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("语义搜索")
                .targetNode("嵌入模型")
                .relationType("依赖")
                .confidence(1.0)
                .example("语义搜索依赖嵌入模型将文本转换为向量")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("知识图谱")
                .targetNode("检索增强生成")
                .relationType("增强")
                .confidence(0.9)
                .example("知识图谱可以增强RAG系统，提供结构化知识和推理能力")
                .createTime(now)
                .updateTime(now)
                .build());

        relations.add(RelationDTO.builder()
                .sourceNode("自然语言处理")
                .targetNode("知识图谱")
                .relationType("构建")
                .confidence(0.9)
                .example("自然语言处理技术可用于从文本中自动构建知识图谱")
                .createTime(now)
                .updateTime(now)
                .build());

        return BatchImportDTO.builder()
                .concepts(concepts)
                .relations(relations)
                .build();
    }
} 