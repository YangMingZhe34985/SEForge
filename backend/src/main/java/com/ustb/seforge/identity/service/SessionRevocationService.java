package com.ustb.seforge.identity.service;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class SessionRevocationService {
    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevocationService(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositoryProvider) {
        this.sessions = repositoryProvider.getIfAvailable();
    }

    public int revokePrincipal(String principalName) {
        if (sessions == null || principalName == null || principalName.isBlank()) return 0;
        var ids = new ArrayList<>(sessions.findByPrincipalName(principalName).keySet());
        ids.forEach(sessions::deleteById);
        if (!ids.isEmpty()) log.info("Revoked {} active session(s) after account authorization changed", ids.size());
        return ids.size();
    }
}
