/*
 Navicat Premium Data Transfer

 Source Server         : test
 Source Server Type    : MySQL
 Source Server Version : 50723
 Source Host           : localhost:3306
 Source Schema         : smartse

 Target Server Type    : MySQL
 Target Server Version : 50723
 File Encoding         : 65001

 Date: 13/05/2025 17:22:57
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agent_config
-- ----------------------------
DROP TABLE IF EXISTS `agent_config`;
CREATE TABLE `agent_config`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '智能体ID',
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '智能体名称',
  `description` varchar(255) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '智能体功能描述',
  `domain` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '智能体领域（如需求分析、软件设计等）',
  `prompt_template` text CHARACTER SET utf8 COLLATE utf8_bin NULL COMMENT '智能体专属的Prompt模板',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8 COLLATE = utf8_bin COMMENT = '智能体配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of agent_config
-- ----------------------------
INSERT INTO `agent_config` VALUES (1, '概念解释智能体', '负责解答软件工程相关基础概念问题', '软件工程基础', '你是一名专业的软件工程讲师，请详细且通俗地解释用户提出的概念问题。', '2025-04-28 15:38:37', '2025-04-28 15:38:37');
INSERT INTO `agent_config` VALUES (2, '需求分析智能体', '负责帮助用户分析与建模需求', '需求工程', '你是一名资深需求工程师，请根据用户的问题，提炼并分析系统需求，必要时用用例模型表述。', '2025-04-28 15:38:56', '2025-04-28 15:39:50');
INSERT INTO `agent_config` VALUES (3, '软件设计智能体', '负责指导系统结构与模块设计', '软件设计', '你是一位经验丰富的软件架构师，请针对用户的问题，提出合理的软件系统设计方案，包括模块划分、接口设计等。', '2025-04-28 15:39:17', '2025-04-28 15:39:55');
INSERT INTO `agent_config` VALUES (4, '软件测试智能体', '负责生成测试用例与测试计划', '软件测试', '你是一名专业的软件测试工程师，请根据用户的问题，制定合适的测试策略、测试用例或缺陷分析。', '2025-04-28 15:41:41', '2025-04-28 15:41:41');
INSERT INTO `agent_config` VALUES (5, '代码评审智能体', '负责审查并优化用户提交的代码片段', '代码审查', '你是一名优秀的代码评审专家，请认真分析用户给出的代码，指出潜在的问题并提出改进建议。', '2025-04-28 15:42:08', '2025-04-28 15:42:08');

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint(20) NOT NULL COMMENT '所属会话ID',
  `sender_type` enum('user','system','assistant','tool','function') CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '发送者类型（user或system）',
  `sender_id` bigint(20) NULL DEFAULT NULL COMMENT '发送者ID（用户ID或智能体ID）',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息内容',
  `content_type` enum('text','image','code') CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT 'text' COMMENT '消息类型',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_chat_message_session_id`(`session_id`) USING BTREE,
  CONSTRAINT `fk_chat_message_session_id` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1922216673983389699 CHARACTER SET = utf8 COLLATE = utf8_bin COMMENT = '对话消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_message
-- ----------------------------
INSERT INTO `chat_message` VALUES (1916779465879392258, 1916779465850032130, 'user', 1, '什么是软件工程？', 'text', '2025-04-28 17:00:11');
INSERT INTO `chat_message` VALUES (1916779615070785537, 1916779465850032130, 'system', 0, '好的！让我用最清晰的方式为你解释「软件工程」这个概念。\n\n---\n\n### **1. 先看字面意思**\n- **软件**：你手机里的微信、电脑上的游戏、医院的挂号系统……所有你看不见摸不着但能用的“数字工具”都是软件。\n- **工程**：盖一座桥要画图纸、算材料、控制工期——这是工程。**软件工程就是把盖桥的严谨方法，用到开发软件上**。\n\n---\n\n### **2. 打个比方**\n假设你要做一款外卖App：\n- **非工程做法**：直接写代码，想到哪写到哪，结果漏洞百出，用户一用就闪退。\n- **软件工程做法**：\n  - **需求分析**：先问用户要什么（能点餐？能支付？能评价？）。\n  - **设计**：画流程图、设计界面（像建筑图纸）。\n  - **写代码**：按设计分模块开发。\n  - **测试**：模拟用户疯狂点击找Bug。\n  - **维护**：上线后根据反馈持续优化。\n\n**软件工程就是让开发软件像建大楼一样，有流程、少出错、易维护。**\n\n---\n\n### **3. 为什么需要软件工程？**\n- **避免灾难**：没有工程思维的软件就像豆腐渣工程，比如：\n  - 银行系统崩溃导致无法取钱。\n  - 游戏服务器每秒卡顿。\n- **团队协作**：几十人同时开发，需要统一规则（比如代码怎么写、进度怎么管）。\n- **控制成本**：60%的软件项目会超预算或失败，工程化能降低风险。\n\n---\n\n### **4. 软件工程的核心思想**\n- **系统化**：不靠程序员个人能力，而是用标准化流程。\n- **可重复**：成功经验能复制到其他项目（比如微信的迭代模式）。\n- **质量保障**：通过测试、文档、代码审查确保软件可靠。\n\n---\n\n### **5. 举个现实例子**\n**Windows系统更新**：\n- 每次更新前，微软会：\n  1. 分析用户需求（比如修复安全漏洞）。\n  2. 设计更新方案（哪些代码要改？如何兼容旧版本？）。\n  3. 开发后在小范围测试。\n  4. 分批推送，监控故障率。\n  5. 出问题快速回滚（撤销更新）。\n——这就是软件工程的实践。\n\n---\n\n### **总结**\n软件工程 = **用科学方法高效造出靠谱的软件**。  \n它像一本“软件建造指南”，告诉你：\n✅ **做什么**（需求）→ ✅ **怎么做**（设计）→ ✅ **怎么做好**（质量）→ ✅ **怎么持续改进**（维护）。\n\n这样解释清楚了吗？如果有具体想了解的环节（比如测试、敏捷开发），可以继续深入！', 'text', '2025-04-28 17:00:47');
INSERT INTO `chat_message` VALUES (1916782329427218433, 1916779465850032130, 'user', 1, '什么是软件测试？', 'text', '2025-04-28 17:11:34');
INSERT INTO `chat_message` VALUES (1916782373505159169, 1916779465850032130, 'system', 0, '{\n  \"answer\": \"软件测试是软件开发过程中的一个重要环节，它通过运行程序或系统来检查其是否满足预期的需求、功能、性能等标准，并发现潜在的缺陷或错误。简单来说，就像给软件做‘体检’，目的是确保软件的质量和可靠性。测试可以包括功能测试（验证功能是否正常）、性能测试（检查速度、稳定性等）、安全测试（防止漏洞）等多种类型。通过测试，开发者能及时修复问题，提升用户体验。\"\n}', 'text', '2025-04-28 17:11:45');
INSERT INTO `chat_message` VALUES (1917190194853888002, 1916779465850032130, 'user', 1, '什么是软件工程？', 'text', '2025-04-29 20:12:17');

-- ----------------------------
-- Table structure for chat_session
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint(20) NOT NULL COMMENT '发起会话的用户ID',
  `agent_id` bigint(20) NULL DEFAULT NULL COMMENT '关联使用的智能体ID',
  `session_title` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '会话标题',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态（1进行中，0已结束）',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '最后活跃时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_chat_session_user_id`(`user_id`) USING BTREE,
  INDEX `fk_chat_session_agent_id`(`agent_id`) USING BTREE,
  CONSTRAINT `fk_chat_session_agent_id` FOREIGN KEY (`agent_id`) REFERENCES `agent_config` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_chat_session_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1922126967467728898 CHARACTER SET = utf8 COLLATE = utf8_bin COMMENT = '对话会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_session
-- ----------------------------
INSERT INTO `chat_session` VALUES (1916779465850032130, 1, 1, '新会话 1745830811351', 1, '2025-04-28 17:00:11', '2025-04-28 17:00:11');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '用户名/登录名',
  `password` varchar(255) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '加密后的密码',
  `email` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '邮箱（可选）',
  `phone` varchar(20) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '手机号（可选）',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '账号状态（1正常，0禁用）',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '账号最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1921050653278019586 CHARACTER SET = utf8 COLLATE = utf8_bin COMMENT = '用户基础信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'test001', 'e10adc3949ba59abbe56e057f20f883e', '111@163.com', '111111', 1, '2025-04-28 16:27:27', '2025-05-04 13:31:21');
INSERT INTO `user` VALUES (1921050653278019585, 'test002', 'e10adc3949ba59abbe56e057f20f883e', NULL, NULL, 1, '2025-05-10 11:52:21', '2025-05-10 11:52:21');

-- ----------------------------
-- Table structure for user_profile
-- ----------------------------
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '关联的用户ID',
  `nickname` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(255) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '头像地址URL',
  `bio` varchar(255) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '个人简介（可选）',
  `school` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '学校（可选）',
  `major` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '专业（可选）',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_user_profile_user_id`(`user_id`) USING BTREE,
  CONSTRAINT `fk_user_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1921050653336739842 CHARACTER SET = utf8 COLLATE = utf8_bin COMMENT = '用户扩展资料表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_profile
-- ----------------------------
INSERT INTO `user_profile` VALUES (1, 1, '李四', 'http://example.com/avatar.jpg', '热爱编程', '北京科技大学', '人工智能', '2025-04-28 16:27:37', '2025-05-10 11:39:31');
INSERT INTO `user_profile` VALUES (1921050653336739841, 1921050653278019585, '新用户', NULL, '', '', '', '2025-05-10 11:52:21', '2025-05-10 11:52:21');

SET FOREIGN_KEY_CHECKS = 1;
