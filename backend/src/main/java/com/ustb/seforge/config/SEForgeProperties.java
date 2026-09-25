package com.ustb.seforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "seforge")
public class SEForgeProperties {

    private final Ai ai = new Ai();
    private final Storage storage = new Storage();
    private final VectorStore vectorStore = new VectorStore();
    private final Jobs jobs = new Jobs();
    private final Sonar sonar = new Sonar();

    public Ai getAi() { return ai; }
    public Storage getStorage() { return storage; }
    public VectorStore getVectorStore() { return vectorStore; }
    public Jobs getJobs() { return jobs; }
    public Sonar getSonar() { return sonar; }

    public static class Ai {
        private boolean enabled;
        private String deepseekApiKey = "";
        private String deepseekBaseUrl = "https://api.deepseek.com/v1";
        private String fastModel = "deepseek-chat";
        private String reasoningModel = "deepseek-reasoner";
        private String codingModel = "deepseek-chat";
        private String dashscopeApiKey = "";
        private String dashscopeBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String fallbackModel = "qwen-plus";
        private String embeddingModel = "text-embedding-v2";
        private int embeddingDimension = 1536;
        private Duration timeout = Duration.ofSeconds(45);
        private int maxRetries = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDeepseekApiKey() { return deepseekApiKey; }
        public void setDeepseekApiKey(String value) { this.deepseekApiKey = value; }
        public String getDeepseekBaseUrl() { return deepseekBaseUrl; }
        public void setDeepseekBaseUrl(String value) { this.deepseekBaseUrl = value; }
        public String getFastModel() { return fastModel; }
        public void setFastModel(String value) { this.fastModel = value; }
        public String getReasoningModel() { return reasoningModel; }
        public void setReasoningModel(String value) { this.reasoningModel = value; }
        public String getCodingModel() { return codingModel; }
        public void setCodingModel(String value) { this.codingModel = value; }
        public String getDashscopeApiKey() { return dashscopeApiKey; }
        public void setDashscopeApiKey(String value) { this.dashscopeApiKey = value; }
        public String getDashscopeBaseUrl() { return dashscopeBaseUrl; }
        public void setDashscopeBaseUrl(String value) { this.dashscopeBaseUrl = value; }
        public String getFallbackModel() { return fallbackModel; }
        public void setFallbackModel(String value) { this.fallbackModel = value; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String value) { this.embeddingModel = value; }
        public int getEmbeddingDimension() { return embeddingDimension; }
        public void setEmbeddingDimension(int value) { this.embeddingDimension = value; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration value) { this.timeout = value; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int value) { this.maxRetries = value; }
    }

    public static class Storage {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "seforge-app";
        private String secretKey = "";
        private String bucket = "seforge";
        private long courseQuotaBytes = 2L * 1024 * 1024 * 1024;
        private long attachmentUserQuotaBytes = 512L * 1024 * 1024;
        private long attachmentCourseQuotaBytes = 2L * 1024 * 1024 * 1024;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String value) { this.endpoint = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String value) { this.accessKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { this.secretKey = value; }
        public String getBucket() { return bucket; }
        public void setBucket(String value) { this.bucket = value; }
        public long getCourseQuotaBytes() { return courseQuotaBytes; }
        public void setCourseQuotaBytes(long value) { this.courseQuotaBytes = value; }
        public long getAttachmentUserQuotaBytes() { return attachmentUserQuotaBytes; }
        public void setAttachmentUserQuotaBytes(long value) { this.attachmentUserQuotaBytes = value; }
        public long getAttachmentCourseQuotaBytes() { return attachmentCourseQuotaBytes; }
        public void setAttachmentCourseQuotaBytes(long value) { this.attachmentCourseQuotaBytes = value; }
    }

    public static class VectorStore {
        private boolean enabled;
        private String host = "localhost";
        private int port = 19530;
        private String collectionPrefix = "seforge_chunks";
        private String activeVersion = "";
        private String writeVersion = "";
        private int reconciliationPageSize = 1000;
        private int reconciliationBatchSize = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getHost() { return host; }
        public void setHost(String value) { this.host = value; }
        public int getPort() { return port; }
        public void setPort(int value) { this.port = value; }
        public String getCollectionPrefix() { return collectionPrefix; }
        public void setCollectionPrefix(String value) { this.collectionPrefix = value; }
        public String getActiveVersion() { return activeVersion; }
        public void setActiveVersion(String value) { this.activeVersion = value; }
        public String getWriteVersion() { return writeVersion; }
        public void setWriteVersion(String value) { this.writeVersion = value; }
        public int getReconciliationPageSize() { return reconciliationPageSize; }
        public void setReconciliationPageSize(int value) { this.reconciliationPageSize = value; }
        public int getReconciliationBatchSize() { return reconciliationBatchSize; }
        public void setReconciliationBatchSize(int value) { this.reconciliationBatchSize = value; }
    }

    public static class Jobs {
        private boolean enabled;
        private String stream = "seforge:jobs";
        private String group = "seforge-workers";
        private int maxAttempts = 3;
        private Duration leaseDuration = Duration.ofMinutes(10);
        private Duration queuedStaleAfter = Duration.ofMinutes(2);
        private int recoveryBatchSize = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getStream() { return stream; }
        public void setStream(String value) { this.stream = value; }
        public String getGroup() { return group; }
        public void setGroup(String value) { this.group = value; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int value) { this.maxAttempts = value; }
        public Duration getLeaseDuration() { return leaseDuration; }
        public void setLeaseDuration(Duration value) { this.leaseDuration = value; }
        public Duration getQueuedStaleAfter() { return queuedStaleAfter; }
        public void setQueuedStaleAfter(Duration value) { this.queuedStaleAfter = value; }
        public int getRecoveryBatchSize() { return recoveryBatchSize; }
        public void setRecoveryBatchSize(int value) { this.recoveryBatchSize = value; }
    }

    public static class Sonar {
        private boolean enabled;
        private String serverUrl = "http://localhost:9000";
        private String token = "";
        private String scannerExecutable = "sonar-scanner";
        private Duration scannerTimeout = Duration.ofMinutes(3);
        private Duration computeTimeout = Duration.ofMinutes(2);
        private Duration apiTimeout = Duration.ofSeconds(15);
        private Duration pollInterval = Duration.ofSeconds(1);
        private int maxFindings = 100;
        private int maxProcessOutputBytes = 262_144;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String value) { this.serverUrl = value; }
        public String getToken() { return token; }
        public void setToken(String value) { this.token = value; }
        public String getScannerExecutable() { return scannerExecutable; }
        public void setScannerExecutable(String value) { this.scannerExecutable = value; }
        public Duration getScannerTimeout() { return scannerTimeout; }
        public void setScannerTimeout(Duration value) { this.scannerTimeout = value; }
        public Duration getComputeTimeout() { return computeTimeout; }
        public void setComputeTimeout(Duration value) { this.computeTimeout = value; }
        public Duration getApiTimeout() { return apiTimeout; }
        public void setApiTimeout(Duration value) { this.apiTimeout = value; }
        public Duration getPollInterval() { return pollInterval; }
        public void setPollInterval(Duration value) { this.pollInterval = value; }
        public int getMaxFindings() { return maxFindings; }
        public void setMaxFindings(int value) { this.maxFindings = value; }
        public int getMaxProcessOutputBytes() { return maxProcessOutputBytes; }
        public void setMaxProcessOutputBytes(int value) { this.maxProcessOutputBytes = value; }
    }
}
