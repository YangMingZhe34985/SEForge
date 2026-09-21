package com.ustb.smartse.modules.knowledgebase.service;

import java.io.File;
import java.util.List;

/**
 * 视频字幕提取服务接口
 */
public interface VideoSubtitleService {
    
    /**
     * 从视频文件中提取字幕
     * @param videoFile 视频文件
     * @return 提取的字幕文本列表
     */
    List<String> extractSubtitles(File videoFile);
    
    /**
     * 从视频文件中提取字幕并保存到知识库
     * @param videoFile 视频文件
     * @param category 分类
     * @return 是否成功
     */
    boolean extractAndSaveSubtitles(File videoFile, String category);
    
    /**
     * 从视频URL下载并提取字幕
     * @param videoUrl 视频URL
     * @param category 分类
     * @return 是否成功
     */
    boolean downloadAndExtractSubtitles(String videoUrl, String category);
} 