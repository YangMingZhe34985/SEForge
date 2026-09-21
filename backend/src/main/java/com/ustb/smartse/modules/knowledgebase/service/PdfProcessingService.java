package com.ustb.smartse.modules.knowledgebase.service;

import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * PDF处理服务接口
 */
public interface PdfProcessingService {
    
    /**
     * 从PDF文件中提取文本内容
     * 
     * @param pdfFile PDF文件
     * @return 提取的文本内容
     */
    String extractText(File pdfFile);
    
    /**
     * 从PDF输入流中提取文本内容
     * 
     * @param inputStream PDF输入流
     * @return 提取的文本内容
     */
    String extractText(InputStream inputStream);
    
    /**
     * 从PDF文件中提取结构化内容（章节、段落等）
     * 
     * @param pdfFile PDF文件
     * @return 结构化内容的映射
     */
    Map<String, Object> extractStructuredContent(File pdfFile);
    
    /**
     * 从PDF文件中提取图像
     * 
     * @param pdfFile PDF文件
     * @param outputDir 输出目录
     * @return 提取的图像文件列表
     */
    List<File> extractImages(File pdfFile, String outputDir);
    
    /**
     * 处理PDF文件并将内容保存到知识库
     * 
     * @param pdfFile PDF文件
     * @param category 知识分类
     * @return 是否成功
     */
    boolean processPdfToKnowledgeBase(File pdfFile, String category);
    
    /**
     * 处理PDF文件并返回知识条目列表（不保存到数据库）
     * 
     * @param pdfFile PDF文件
     * @param category 知识分类
     * @return 知识条目列表
     */
    List<KnowledgeEntry> processPdfToEntries(File pdfFile, String category);
} 