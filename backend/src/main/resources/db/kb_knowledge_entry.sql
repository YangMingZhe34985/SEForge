-- 创建知识库条目表
CREATE TABLE IF NOT EXISTS `kb_knowledge_entry` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(255) NOT NULL COMMENT '条目标题',
  `content` text NOT NULL COMMENT '条目内容',
  `source_type` tinyint(4) NOT NULL COMMENT '来源类型(1:课程大纲, 2:习题库, 3:UML案例库, 4:教材PDF, 5:MOOC视频)',
  `source_id` varchar(100) DEFAULT NULL COMMENT '来源ID',
  `category` varchar(100) DEFAULT NULL COMMENT '知识点分类',
  `vector_id` varchar(100) DEFAULT NULL COMMENT '向量ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_source_type` (`source_type`),
  FULLTEXT KEY `idx_fulltext` (`title`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库条目表';

-- 添加示例数据
INSERT INTO `kb_knowledge_entry` (`title`, `content`, `source_type`, `source_id`, `category`, `create_time`, `update_time`) VALUES
('单一职责原则', '单一职责原则（Single Responsibility Principle，SRP）是面向对象设计的基本原则之一。它规定一个类应该只有一个引起它变化的原因。每个类应该只负责一项职责或功能。', 1, 'outline_001', '设计原则', NOW(), NOW()),
('开闭原则', '开闭原则（Open-Closed Principle，OCP）规定软件实体（类、模块、函数等）应该对扩展开放，对修改关闭。这意味着当需要添加新功能时，不应该修改现有代码，而是通过添加新代码来实现。', 1, 'outline_002', '设计原则', NOW(), NOW()),
('工厂模式', '工厂模式是一种创建型设计模式，它提供了一种创建对象的最佳方式。在工厂模式中，我们在创建对象时不会对客户端暴露创建逻辑，并且是通过使用一个共同的接口来指向新创建的对象。', 1, 'outline_003', '设计模式', NOW(), NOW()),
('UML类图', 'UML类图是一种结构图，描述系统的静态结构。它展示了系统中的类、类的属性、方法和类之间的关系。类图是最常用的UML图之一，用于对系统进行静态建模。', 2, 'exercise_001', 'UML', NOW(), NOW()),
('面向对象程序设计案例：银行账户系统', '本案例实现了一个简单的银行账户系统，展示了继承、封装和多态的基本概念。系统包含不同类型的账户（储蓄账户、支票账户），每种账户都有特定的行为和属性。', 3, 'uml_001', '面向对象程序设计', NOW(), NOW());