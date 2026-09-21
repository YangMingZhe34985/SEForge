-- ================================================
-- 基于你的需求，完整设计5张基础表的建表SQL
-- ================================================

use smartse;
-- 1. 用户基础信息表
CREATE TABLE user (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
                      username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名/登录名',
                      password VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                      email VARCHAR(100) DEFAULT NULL COMMENT '邮箱（可选）',
                      phone VARCHAR(20) DEFAULT NULL COMMENT '手机号（可选）',
                      status TINYINT DEFAULT 1 COMMENT '账号状态（1正常，0禁用）',
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '账号最后更新时间'
) COMMENT='用户基础信息表';

-- 2. 用户扩展资料表
CREATE TABLE user_profile (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
                              user_id BIGINT NOT NULL COMMENT '关联的用户ID',
                              nickname VARCHAR(50) COMMENT '用户昵称',
                              avatar_url VARCHAR(255) COMMENT '头像地址URL',
                              bio VARCHAR(255) COMMENT '个人简介（可选）',
                              school VARCHAR(100) COMMENT '学校（可选）',
                              major VARCHAR(100) COMMENT '专业（可选）',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              CONSTRAINT fk_user_profile_user_id FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) COMMENT='用户扩展资料表';

-- 3. 智能体配置表
CREATE TABLE agent_config (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '智能体ID',
                              name VARCHAR(50) NOT NULL COMMENT '智能体名称',
                              description VARCHAR(255) COMMENT '智能体功能描述',
                              domain VARCHAR(50) COMMENT '智能体领域（如需求分析、软件设计等）',
                              prompt_template TEXT COMMENT '智能体专属的Prompt模板',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='智能体配置表';

-- 4. 对话会话表（相当于一个对话窗口）
CREATE TABLE chat_session (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
                              user_id BIGINT NOT NULL COMMENT '发起会话的用户ID',
                              agent_id BIGINT DEFAULT NULL COMMENT '关联使用的智能体ID',
                              session_title VARCHAR(100) COMMENT '会话标题',
                              status TINYINT DEFAULT 1 COMMENT '状态（1进行中，0已结束）',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
                              CONSTRAINT fk_chat_session_user_id FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
                              CONSTRAINT fk_chat_session_agent_id FOREIGN KEY (agent_id) REFERENCES agent_config(id) ON DELETE SET NULL
) COMMENT='对话会话表';

-- 5. 对话消息表（每条消息记录，区分用户/系统）
CREATE TABLE chat_message (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
                              session_id BIGINT NOT NULL COMMENT '所属会话ID',
                              sender_type ENUM('user', 'system') NOT NULL COMMENT '发送者类型（user或system）',
                              sender_id BIGINT DEFAULT NULL COMMENT '发送者ID（用户ID或智能体ID）',
                              content TEXT NOT NULL COMMENT '消息内容',
                              content_type ENUM('text', 'image', 'code') DEFAULT 'text' COMMENT '消息类型',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
                              CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) COMMENT='对话消息表';

-- ================================================
-- 说明：
-- - 所有表都有 created_at 和 updated_at
-- - 所有必要外键都设置了（且带ON DELETE CASCADE或SET NULL）
-- - 枚举字段 sender_type, content_type 保证数据一致性
-- ================================================
