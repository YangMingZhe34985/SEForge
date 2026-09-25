package com.ustb.seforge.ai;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.ustb.seforge.ai.infrastructure.AiHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"seforge.ai.enabled=true", "seforge.ai.deepseek-api-key=", "seforge.ai.dashscope-api-key="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AiDegradedStartupTest {
    @Autowired MockMvc mvc;
    @Autowired AiHealthIndicator ai;
    @Autowired JdbcTemplate jdbc;

    @Test void missingKeysDoNotBlockStudentIdentityOrCourseApi() throws Exception {
        assertThat(ai.health().getStatus().getCode()).isEqualTo("DEGRADED");
        jdbc.update("insert into roles(version,code,name,created_at,updated_at) values(0,'USER','User',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"degraded@example.invalid","username":"degraded-student","studentNo":"2026092401","displayName":"Student","password":"phase2-test-password"}
                """)).andExpect(status().isCreated());
        var login = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"2026092401","password":"phase2-test-password","portal":"STUDENT"}
                """)).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/v1/courses").session(session)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
