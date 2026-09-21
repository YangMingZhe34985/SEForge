package com.ustb.smartse.modules.knowledgebase.service;

import java.io.File;

/**
 * 统一的内容处理服务接口
 * 负责处理各种资源（PDF、视频等）并将其导入知识库
 */
public interface ContentProcessingService {
    
    /**
     * 处理资源文件并导入知识库
     * 
     * @param resourceFile 资源文件
     * @param resourceType 资源类型
     * @param category 分类
     * @param options 处理选项
     * @return 处理结果
     */
    ResourceProcessResult processResource(File resourceFile, String resourceType, String category, ProcessOptions options);
    
    /**
     * 资源类型常量
     */
    String RESOURCE_TYPE_PDF = "pdf";
    String RESOURCE_TYPE_VIDEO = "video";
    String RESOURCE_TYPE_WEBPAGE = "webpage";
    
    /**
     * 处理选项类
     */
    class ProcessOptions {
        private boolean overwriteExisting = false;
        private boolean splitByChapters = true;
        
        public boolean isOverwriteExisting() {
            return overwriteExisting;
        }
        
        public void setOverwriteExisting(boolean overwriteExisting) {
            this.overwriteExisting = overwriteExisting;
        }
        
        public boolean isSplitByChapters() {
            return splitByChapters;
        }
        
        public void setSplitByChapters(boolean splitByChapters) {
            this.splitByChapters = splitByChapters;
        }
    }
    
    /**
     * 资源处理结果类
     */
    class ResourceProcessResult {
        private boolean success;
        private String errorMessage;
        private int totalEntries;
        private int addedEntries;
        private java.util.List<String> failedEntries = new java.util.ArrayList<>();
        
        public static Builder builder() {
            return new Builder();
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public int getTotalEntries() {
            return totalEntries;
        }
        
        public int getAddedEntries() {
            return addedEntries;
        }
        
        public java.util.List<String> getFailedEntries() {
            return failedEntries;
        }
        
        public static class Builder {
            private ResourceProcessResult result = new ResourceProcessResult();
            
            public Builder success(boolean success) {
                result.success = success;
                return this;
            }
            
            public Builder errorMessage(String errorMessage) {
                result.errorMessage = errorMessage;
                return this;
            }
            
            public Builder totalEntries(int totalEntries) {
                result.totalEntries = totalEntries;
                return this;
            }
            
            public Builder addedEntries(int addedEntries) {
                result.addedEntries = addedEntries;
                return this;
            }
            
            public Builder failedEntries(java.util.List<String> failedEntries) {
                result.failedEntries = failedEntries;
                return this;
            }
            
            public ResourceProcessResult build() {
                return result;
            }
        }
    }
} 