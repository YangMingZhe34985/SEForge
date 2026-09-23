package com.ustb.seforge.review.service;

import com.ustb.seforge.review.api.ExternalSonarFinding;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Infrastructure boundary for an authenticated SonarQube analysis. */
public interface SonarGateway {
    Analysis analyze(ScanRequest request);

    record ScanRequest(
            Path workspaceDirectory,
            Path sourceDirectory,
            String projectKey,
            String projectName) {

        public ScanRequest {
            if (workspaceDirectory == null || sourceDirectory == null) {
                throw new IllegalArgumentException("Sonar workspace and source directory are required");
            }
            if (projectKey == null || !projectKey.matches("[A-Za-z0-9_.:-]{1,200}")) {
                throw new IllegalArgumentException("Invalid Sonar project key");
            }
            if (projectName == null || projectName.isBlank()) {
                throw new IllegalArgumentException("Sonar project name is required");
            }
        }
    }

    record Analysis(
            String projectKey,
            String computeTaskId,
            String analysisId,
            String qualityGate,
            Map<String, String> measures,
            List<ExternalSonarFinding> findings) {

        public Analysis {
            measures = measures == null ? Map.of() : Map.copyOf(measures);
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }
}
