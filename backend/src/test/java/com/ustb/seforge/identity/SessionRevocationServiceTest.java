package com.ustb.seforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.identity.service.SessionRevocationService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class SessionRevocationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void revokesEverySessionOwnedByThePrincipal() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider = mock(ObjectProvider.class);
        doReturn(repository).when(provider).getIfAvailable();
        when(repository.findByPrincipalName("teacher")).thenReturn(Map.of(
                "session-a", mock(Session.class),
                "session-b", mock(Session.class)));
        SessionRevocationService service = new SessionRevocationService(provider);

        assertThat(service.revokePrincipal("teacher")).isEqualTo(2);
        verify(repository).deleteById("session-a");
        verify(repository).deleteById("session-b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void staysAvailableWhenRedisSessionRepositoryIsNotConfigured() {
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(new SessionRevocationService(provider).revokePrincipal("teacher")).isZero();
    }
}
