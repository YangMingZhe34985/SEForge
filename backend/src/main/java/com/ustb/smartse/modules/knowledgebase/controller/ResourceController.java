package com.ustb.smartse.modules.knowledgebase.controller;

import com.ustb.smartse.common.api.ApiResult;
import com.ustb.smartse.modules.knowledgebase.service.ContentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一的资源上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    @Autowired
    private ContentProcessingService contentProcessingService;
    
    /**
     * 上传PDF资源
     */
    @PostMapping("/pdf/upload")
    public ApiResult<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return uploadResource(file, ContentProcessingService.RESOURCE_TYPE_PDF, category, overwrite);
    }
    
    /**
     * 上传视频资源
     */
    @PostMapping("/video/upload")
    public ApiResult<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return uploadResource(file, ContentProcessingService.RESOURCE_TYPE_VIDEO, category, overwrite);
    }
    
    /**
     * 通用资源上传处理
     */
    private ApiResult<Map<String, Object>> uploadResource(
            MultipartFile file, String resourceType, String category, boolean overwrite) {
        try {
            // 1. 基本验证
            if (file == null || file.isEmpty()) {
                return ApiResult.error("文件为空，请选择有效文件");
            }
            
            // 2. 保存临时文件
            Path tempPath = Files.createTempFile("upload_", "_" + resourceType);
            File tempFile = tempPath.toFile();
            file.transferTo(tempFile);
            
            try {
                // 3. 处理资源
                ContentProcessingService.ProcessOptions options = new ContentProcessingService.ProcessOptions();
                options.setOverwriteExisting(overwrite);
                
                ContentProcessingService.ResourceProcessResult result = contentProcessingService.processResource(
                    tempFile, resourceType, category, options);
                
                // 4. 返回结果
                if (result.isSuccess()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("totalEntries", result.getTotalEntries());
                    data.put("addedEntries", result.getAddedEntries());
                    data.put("failedEntries", result.getFailedEntries());
                    
                    return ApiResult.success(data, "资源处理成功");
                } else {
                    return ApiResult.error("资源处理失败: " + result.getErrorMessage());
                }
            } finally {
                // 5. 清理临时文件
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            log.error("资源上传处理失败", e);
            return ApiResult.error("资源上传处理失败: " + e.getMessage());
        }
    }
} 