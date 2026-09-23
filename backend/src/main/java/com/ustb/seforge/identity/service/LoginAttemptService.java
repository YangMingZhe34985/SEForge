package com.ustb.seforge.identity.service;

public interface LoginAttemptService {
    boolean isBlocked(String identifier, String remoteAddress);

    void recordFailure(String identifier, String remoteAddress);

    void clear(String identifier, String remoteAddress);
}
