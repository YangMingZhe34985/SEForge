package com.ustb.seforge.course;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.course.domain.Course;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseResource;
import com.ustb.seforge.course.domain.ResourceType;
import com.ustb.seforge.course.domain.Semester;
import com.ustb.seforge.course.domain.SemesterStatus;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.course.repository.CourseResourceRepository;
import com.ustb.seforge.course.repository.SemesterRepository;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.api.UserView;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.service.IdentityService;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PhaseOneAuthorizationIntegrationTest {
    private static final String PASSWORD = "phase-one-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired IdentityService identityService;
    @Autowired SemesterRepository semesterRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseMemberRepository memberRepository;
    @Autowired CourseResourceRepository resourceRepository;

    @Test
    void phaseOneHttpAuthorizationMatrixUsesServerSessionAndDatabaseMembership() throws Exception {
        seedGlobalRoles();
        UserView teacher = createUser("phase1-teacher", AccountType.TEACHER);
        UserView student = createUser("phase1-student", AccountType.STUDENT);
        UserView outsider = createUser("phase1-outsider", AccountType.STUDENT);

        Semester semester = semesterRepository.save(new Semester(
                "P1-2026", "Phase 1 Acceptance", LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 7, 31), SemesterStatus.ACTIVE));
        Course ownedCourse = courseRepository.save(new Course(
                "P1-OWNED", "Owned course", null, semester.getId(), teacher.id()));
        Course otherCourse = courseRepository.save(new Course(
                "P1-OTHER", "Other course", null, semester.getId(), teacher.id()));
        memberRepository.save(new CourseMember(
                ownedCourse.getId(), null, teacher.id(), CourseMemberRole.TEACHER));
        memberRepository.save(new CourseMember(
                otherCourse.getId(), null, teacher.id(), CourseMemberRole.TEACHER));
        memberRepository.save(new CourseMember(
                ownedCourse.getId(), null, student.id(), CourseMemberRole.STUDENT));
        CourseResource ownedResource = resourceRepository.save(new CourseResource(
                ownedCourse.getId(), null, teacher.id(), "Course notes", null,
                ResourceType.DOCUMENT, "courses/" + ownedCourse.getId() + "/notes.pdf",
                "application/pdf", 128L));
        CourseResource otherResource = resourceRepository.save(new CourseResource(
                otherCourse.getId(), null, teacher.id(), "Private notes", null,
                ResourceType.DOCUMENT, "courses/" + otherCourse.getId() + "/private.pdf",
                "application/pdf", 256L));

        MockHttpSession teacherSession = login(teacher.username());
        MockHttpSession studentSession = login(student.username());
        MockHttpSession outsiderSession = login(outsider.username());

        // A client-controlled identity/role header cannot replace the authenticated session principal.
        mockMvc.perform(get("/api/v1/auth/me")
                        .session(studentSession)
                        .header("X-User-Id", teacher.id())
                        .header("X-Role", "ADMIN")
                        .header("X-Authorities", "ROLE_ADMIN,ROLE_TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(student.id()))
                .andExpect(jsonPath("$.data.username").value(student.username()))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        // Global ADMIN access is derived from the server-created principal, not browser state.
        mockMvc.perform(get("/api/v1/admin/users")
                        .session(studentSession)
                        .header("X-User-Id", teacher.id())
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Course visibility is derived from active database membership.
        mockMvc.perform(get("/api/v1/courses/{courseId}", ownedCourse.getId())
                        .session(outsiderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources", ownedCourse.getId())
                        .session(outsiderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // A STUDENT cannot forge a course TEACHER role to mutate course structure.
        mockMvc.perform(post("/api/v1/courses/{courseId}/classes", ownedCourse.getId())
                        .session(studentSession)
                        .with(csrf())
                        .header("X-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FORGED","name":"Forged class","capacity":30,"primaryClass":false}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/invites", ownedCourse.getId())
                        .session(studentSession)
                        .with(csrf())
                        .header("X-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberRole":"STUDENT","maxUses":2}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Resource lookup is course-scoped, preventing cross-course IDOR disclosure.
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}",
                        ownedCourse.getId(), otherResource.getId()).session(studentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}",
                        ownedCourse.getId(), ownedResource.getId()).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownedResource.getId()));

        // Positive Phase 1 closure: teacher issues an invite; outsider joins and gains read access.
        MvcResult inviteResult = mockMvc.perform(post("/api/v1/courses/{courseId}/invites", ownedCourse.getId())
                        .session(teacherSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberRole":"STUDENT","maxUses":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseId").value(ownedCourse.getId()))
                .andReturn();
        JsonNode inviteBody = objectMapper.readTree(inviteResult.getResponse().getContentAsByteArray());
        String inviteCode = inviteBody.path("data").path("code").asText();

        mockMvc.perform(post("/api/v1/courses/join")
                        .session(outsiderSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of("inviteCode", inviteCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownedCourse.getId()))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources", ownedCourse.getId())
                        .session(outsiderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ownedResource.getId()));
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

    private UserView createUser(String username, AccountType accountType) {
        return identityService.createUser(new CreateUserRequest(
                username + "@example.test", username, PASSWORD, username, accountType, Set.of(GlobalRole.USER)));
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                "identifier", username,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
