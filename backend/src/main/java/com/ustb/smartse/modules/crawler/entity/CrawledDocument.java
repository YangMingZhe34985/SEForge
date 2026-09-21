package com.ustb.smartse.modules.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 爬取的文档实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawler_document")
public class CrawledDocument {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档内容
     */
    private String content;
    
    /**
     * 文档URL
     */
    private String url;
    
    /**
     * 文档来源网站
     */
    private String source;
    
    /**
     * 文档类型(html, pdf, markdown等)
     */
    private String documentType;
    
    /**
     * 文档分类（设计模式、架构、UML等）
     */
    private String category;
    
    /**
     * 处理状态：0-待处理, 1-处理中, 2-处理完成, 3-处理失败
     */
    private Integer processStatus;
    
    /**
     * 是否已导入知识库：0-未导入, 1-已导入
     */
    private Integer importStatus;
    
    /**
     * 爬取时间
     */
    private LocalDateTime crawlTime;
    
    /**
     * 处理时间
     */
    private LocalDateTime processTime;
    
    /**
     * 导入知识库时间
     */
    private LocalDateTime importTime;
} 