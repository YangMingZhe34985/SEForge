-- 创建爬虫文档表
CREATE TABLE IF NOT EXISTS `crawler_document` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(255) NOT NULL COMMENT '文档标题',
  `content` longtext NOT NULL COMMENT '文档内容',
  `url` varchar(1024) NOT NULL COMMENT '文档URL',
  `source` varchar(255) DEFAULT NULL COMMENT '文档来源网站',
  `document_type` varchar(50) NOT NULL COMMENT '文档类型(html, pdf, markdown等)',
  `category` varchar(100) DEFAULT NULL COMMENT '文档分类（设计模式、架构、UML等）',
  `process_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '处理状态：0-待处理, 1-处理中, 2-处理完成, 3-处理失败',
  `import_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否已导入知识库：0-未导入, 1-已导入',
  `crawl_time` datetime NOT NULL COMMENT '爬取时间',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  `import_time` datetime DEFAULT NULL COMMENT '导入知识库时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_url` (`url`(255)),
  KEY `idx_category` (`category`),
  KEY `idx_process_status` (`process_status`),
  KEY `idx_import_status` (`import_status`),
  KEY `idx_crawl_time` (`crawl_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫文档表'; 