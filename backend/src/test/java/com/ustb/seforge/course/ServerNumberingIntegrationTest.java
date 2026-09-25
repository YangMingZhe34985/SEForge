package com.ustb.seforge.course;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.course.api.CreateSemesterRequest;
import com.ustb.seforge.course.service.CourseService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ServerNumberingIntegrationTest {
    @Autowired CourseService courses;
    @Autowired ObjectMapper mapper;

    @Test
    void ignoresClientCodeAndAllocatesDistinctNumbersUnderConcurrency() throws Exception {
        var request = mapper.readValue("""
                {"code":"CLIENT-CONTROLLED","name":"Concurrent semester",
                 "startsOn":"2026-09-01","endsOn":"2027-01-31","status":"PLANNED"}
                """, CreateSemesterRequest.class);
        assertThat(request.code()).isNull();
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> executor.submit(() -> courses.createSemester(request).code())).toList();
            var codes = new java.util.HashSet<String>();
            for (var future : futures) {
                String code = future.get();
                assertThat(code).matches("SEM-[0-9]{8,}");
                assertThat(codes.add(code)).isTrue();
            }
        }
    }
}
