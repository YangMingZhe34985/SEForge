package com.ustb.smartse.modules.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ustb.smartse.modules.crawler.entity.CrawledDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 爬取文档Mapper接口
 */
@Mapper
public interface CrawledDocumentMapper extends BaseMapper<CrawledDocument> {
    
    /**
     * 检查URL是否已存在
     * @param url URL
     * @return 存在数量
     */
    @Select("SELECT COUNT(*) FROM crawler_document WHERE url = #{url}")
    int countByUrl(@Param("url") String url);
    
    /**
     * 查询待处理的文档
     * @param limit 限制数量
     * @return 文档列表
     */
    @Select("SELECT * FROM crawler_document WHERE process_status = 0 LIMIT #{limit}")
    List<CrawledDocument> findPendingDocuments(@Param("limit") int limit);
    
    /**
     * 查询待导入的文档
     * @param limit 限制数量
     * @return 文档列表
     */
    @Select("SELECT * FROM crawler_document WHERE process_status = 2 AND import_status = 0 LIMIT #{limit}")
    List<CrawledDocument> findPendingImportDocuments(@Param("limit") int limit);
    
    /**
     * 按分类统计文档数量
     * @return 统计结果
     */
    @Select("SELECT category, COUNT(*) as count FROM crawler_document GROUP BY category")
    List<CrawlerStatistics> countByCategory();
    
    /**
     * 更新处理状态
     * @param id 文档ID
     * @param status 状态
     * @return 影响行数
     */
    @Update("UPDATE crawler_document SET process_status = #{status}, process_time = NOW() WHERE id = #{id}")
    int updateProcessStatus(@Param("id") Long id, @Param("status") Integer status);
    
    /**
     * 更新导入状态
     * @param id 文档ID
     * @param status 状态
     * @return 影响行数
     */
    @Update("UPDATE crawler_document SET import_status = #{status}, import_time = NOW() WHERE id = #{id}")
    int updateImportStatus(@Param("id") Long id, @Param("status") Integer status);
    
    /**
     * 统计结果类
     */
    class CrawlerStatistics {
        private String category;
        private Integer count;
        
        public String getCategory() {
            return category;
        }
        
        public void setCategory(String category) {
            this.category = category;
        }
        
        public Integer getCount() {
            return count;
        }
        
        public void setCount(Integer count) {
            this.count = count;
        }
    }
} 