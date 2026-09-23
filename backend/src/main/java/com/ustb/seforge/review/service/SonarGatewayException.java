package com.ustb.seforge.review.service;

/** A deterministic scanner, SonarQube server or SonarQube response failure. */
public class SonarGatewayException extends RuntimeException {
    public SonarGatewayException(String message) {
        super(message);
    }

    public SonarGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
