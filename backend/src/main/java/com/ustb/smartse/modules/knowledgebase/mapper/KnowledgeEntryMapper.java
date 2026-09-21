package com.ustb.smartse.modules.knowledgebase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库条目Mapper接口
 */
@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {
    
    /**
     * 根据关键词进行BM25文本检索
     * @param keyword 关键词
     * @param limit 返回数量限制
     * @return 知识条目列表
     */
    @Select("SELECT *, " +
            "MATCH(title, content) AGAINST(#{keyword} IN BOOLEAN MODE) AS relevance " +
            "FROM kb_knowledge_entry " +
            "WHERE MATCH(title, content) AGAINST(#{keyword} IN BOOLEAN MODE) " +
            "ORDER BY relevance DESC " +
            "LIMIT #{limit}")
    List<KnowledgeEntry> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 根据分类查询知识条目
     * @param category 分类
     * @return 知识条目列表
     */
    List<KnowledgeEntry> selectByCategory(@Param("category") String category);
} 