package com.ustb.seforge.common.audit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.api.RegisterRequest;
import com.ustb.seforge.identity.api.UserView;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.service.IdentityService;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
// Other suites use csrf(), which replaces the cached filter's token repository.
// Verify the real cookie/header contract against a fresh, unmodified context.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class AdminAuditLogIntegrationTest {
    private static final String PASSWORD = "audit-test-password";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired IdentityService identityService;
    @Autowired AuditService auditService;

    @Test
    void administratorCanReadAuditTrailAndStudentsCannot() throws Exception {
        seedGlobalRoles();
        UserView admin = identityService.createUser(new CreateUserRequest(
                "audit-admin@example.test", "audit-admin", PASSWORD, "审计管理员",
                AccountType.TEACHER, Set.of(GlobalRole.USER, GlobalRole.ADMIN)));
        UserView student = identityService.registerStudent(new RegisterRequest(
                "audit-student@example.test", "audit-student", PASSWORD, "审计学生", "2026-AUDIT"));
        auditService.record(admin.id(), null, "ADMIN_USER_CREATE", "USER", student.id(),
                AuditService.SUCCEEDED);
        auditService.record(null, null, "AUTH_LOGIN", "USER", student.id(), AuditService.REJECTED);

        MockHttpSession adminSession = login("audit-admin");
        // The login itself is audited by AuthController, so match entries by content
        // instead of relying on an exact total or ordering.
        mockMvc.perform(get("/api/v1/admin/audit-logs").session(adminSession)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.items[0].traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.action == 'ADMIN_USER_CREATE')].actorUsername",
                        contains("audit-admin")))
                .andExpect(jsonPath("$.data.items[?(@.action == 'ADMIN_USER_CREATE')].outcome",
                        contains("SUCCEEDED")))
                .andExpect(jsonPath("$.data.items[?(@.outcome == 'REJECTED')].action",
                        contains("AUTH_LOGIN")));

        MockHttpSession studentSession = login("audit-student");
        mockMvc.perform(get("/api/v1/admin/audit-logs").session(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * Logs in through the real SPA CSRF contract (GET /auth/csrf, then cookie + header)
     * instead of the csrf() post-processor, which would permanently swap the shared
     * CsrfFilter's token repository inside the cached test context.
     */
    private MockHttpSession login(String username) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                csrf.getResponse().getContentAsString(), "$.data.token");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s","portal":"%s"}
                                """.formatted(username.equals("audit-student") ? "2026-AUDIT" : username,
                                        PASSWORD, username.equals("audit-student") ? "STUDENT" : "ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void seedGlobalRoles() {
        jdbcTemplate.update("""
                merge into roles (code, name, version, created_at, updated_at)
                key (code) values ('USER', 'User', 0, current_timestamp, current_timestamp)
                """);
        jdbcTemplate.update("""
                merge into roles (code, name, version, created_at, updated_at)
                key (code) values ('ADMIN', 'Administrator', 0, current_timestamp, current_timestamp)
                """);
    }
}
