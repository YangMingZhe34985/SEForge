package com.ustb.seforge.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CookieCsrfTokenRepository csrfTokenRepository;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    Filter springSecurityFilterChain;

    @BeforeEach
    void restoreConfiguredCsrfTokenRepository() {
        // spring-security-test's csrf() post-processor reflectively swaps the live
        // CsrfFilter's repository with a session-backed test double for the whole cached
        // context (WebTestUtils#setCsrfTokenRepository). Restore the configured cookie
        // repository so the XSRF-TOKEN contract below cannot depend on method order.
        if (springSecurityFilterChain instanceof FilterChainProxy filterChainProxy) {
            for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
                for (Filter filter : chain.getFilters()) {
                    if (filter instanceof CsrfFilter) {
                        ReflectionTestUtils.setField(filter, "tokenRepository", csrfTokenRepository);
                    }
                }
            }
        }
    }

    @Test
    void unauthenticatedApiUsesStructured401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void csrfEndpointInitializesSpaTokenCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void stateChangingRequestWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@example.com","username":"student","password":"secure-pass-123","displayName":"Student"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void validationErrorsUseApiErrorContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","username":"x","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void registerFromDisallowedOriginIsRejectedByCorsBeforeCsrf() throws Exception {
        String csrfToken = fetchSpaCsrfToken();
        // Even a fully CSRF-valid registration must be rejected when the browser Origin is
        // outside the allow-list: the CORS gate runs before the CSRF gate and answers with
        // its own plain-text 403 body, never the JSON ACCESS_DENIED envelope.
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Origin", "http://evil.example")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cors-denied@example.test","username":"cors-denied","password":"cors-denied-pass","displayName":"Denied"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid CORS request"));
    }

    @Test
    void registerFromAllowedOriginWithCsrfCreatesStudent() throws Exception {
        // H2 create-drop leaves the roles table empty (Flyway is off under the test profile).
        jdbcTemplate.update("""
                merge into roles (code, name, version, created_at, updated_at)
                key (code) values ('USER', 'User', 0, current_timestamp, current_timestamp)
                """);
        String csrfToken = fetchSpaCsrfToken();
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Origin", "http://localhost:5173")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cors-allowed@example.test","username":"cors-allowed","password":"cors-allowed-pass","displayName":"Allowed"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(jsonPath("$.data.username").value("cors-allowed"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    /**
     * Walks the real SPA contract: GET /auth/csrf must set the XSRF-TOKEN cookie and
     * return the same raw token in the body for use in the X-XSRF-TOKEN header.
     */
    private String fetchSpaCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        Cookie xsrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie, "GET /auth/csrf must set the XSRF-TOKEN cookie");
        String bodyToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        assertEquals(bodyToken, xsrfCookie.getValue(), "body token must match the XSRF-TOKEN cookie");
        return xsrfCookie.getValue();
    }
}
