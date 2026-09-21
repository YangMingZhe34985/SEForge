package com.ustb.smartse.modules.knowledgebase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识库条目实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_knowledge_entry")
public class KnowledgeEntry implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 条目标题
     */
    private String title;
    
    /**
     * 条目内容
     */
    private String content;
    
    /**
     * 来源类型 (1:课程大纲, 2:习题库, 3:UML案例库, 4:教材PDF, 5:MOOC视频)
     */
    private Integer sourceType;
    
    /**
     * 来源ID（关联到原始内容）
     */
    private String sourceId;
    
    /**
     * 知识点分类
     */
    private String category;
    
    /**
     * 条目在Milvus中的ID
     */
    private String vectorId;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
} 