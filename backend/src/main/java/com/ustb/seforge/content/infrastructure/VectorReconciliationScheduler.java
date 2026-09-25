package com.ustb.seforge.content.infrastructure;

import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.service.VectorIndexReconciliationService;
import com.ustb.seforge.content.service.VectorIndexVersionPolicy;
import com.ustb.seforge.course.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class VectorReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(VectorReconciliationScheduler.class);

    private final SEForgeProperties properties;
    private final CourseRepository courses;
    private final VectorIndexVersionPolicy versions;
    private final VectorIndexReconciliationService reconciliation;

    public VectorReconciliationScheduler(SEForgeProperties properties, CourseRepository courses,
                                         VectorIndexVersionPolicy versions,
                                         VectorIndexReconciliationService reconciliation) {
        this.properties = properties;
        this.courses = courses;
        this.versions = versions;
        this.reconciliation = reconciliation;
    }

    @Scheduled(initialDelayString = "${seforge.vector-store.reconciliation-initial-delay:5m}",
            fixedDelayString = "${seforge.vector-store.reconciliation-interval:15m}")
    public void reconcile() {
        if (!properties.getVectorStore().isEnabled()) return;
        for (var course : courses.findAll()) {
            for (String version : versions.managedVersions()) {
                try {
                    var result = reconciliation.reconcile(course.getId(), version);
                    if (result.orphansDeleted() > 0 || !result.missingVectorIds().isEmpty()) {
                        log.info("Vector reconciliation course={} version={} deletedOrphans={} missingDetected={} repaired={}",
                                course.getId(), version, result.orphansDeleted(),
                                result.missingVectorIds().size(), result.repairedVectors());
                    }
                } catch (RuntimeException exception) {
                    log.warn("Vector reconciliation failed for course={} version={}: {}",
                            course.getId(), version, exception.getMessage());
                }
            }
        }
    }
}
