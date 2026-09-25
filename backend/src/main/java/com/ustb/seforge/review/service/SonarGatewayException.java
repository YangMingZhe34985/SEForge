package com.ustb.seforge.review.service;

/** A deterministic scanner, SonarQube server or SonarQube response failure. */
public class SonarGatewayException extends RuntimeException {
    public SonarGatewayException(String message) {
        super(message);
    }

    public SonarGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Only fixed configuration messages become public diagnostic codes. */
    public String diagnosticCode() {
        return switch (getMessage() == null ? "" : getMessage()) {
            case "SonarQube token is not configured" -> "SONAR_TOKEN_MISSING";
            case "SonarScanner executable is not configured" -> "SONAR_SCANNER_MISSING";
            case "SonarQube server URL is not configured", "SonarQube server URL must be an HTTP(S) origin" -> "SONAR_URL_INVALID";
            case "SonarQube code review is disabled" -> "SONAR_DISABLED";
            default -> "SONAR_FAILED";
        };
    }
}
