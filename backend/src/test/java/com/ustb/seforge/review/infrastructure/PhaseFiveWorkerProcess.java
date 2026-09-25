package com.ustb.seforge.review.infrastructure;

/** Separate real worker JVM used exclusively by the Review crash-recovery test. */
public final class PhaseFiveWorkerProcess {
    public static void main(String[] args) {
        var context = new org.springframework.boot.builder.SpringApplicationBuilder(com.ustb.seforge.SEForgeApplication.class, Storage.class).run(args);
        context.getBean(com.ustb.seforge.job.infrastructure.RedisJobWorker.class);
        System.out.println("PHASE5_WORKER_READY");
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    public static class Storage {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        com.ustb.seforge.content.infrastructure.ObjectStorage realStorage(com.ustb.seforge.config.SEForgeProperties properties) {
            return new com.ustb.seforge.content.infrastructure.MinioObjectStorage(properties);
        }
    }
}
