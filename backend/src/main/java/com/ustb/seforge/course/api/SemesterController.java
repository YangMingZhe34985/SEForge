package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.course.service.CourseService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/semesters")
public class SemesterController {
    private final CourseService courseService;

    public SemesterController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiEnvelope<List<SemesterView>> list() {
        return ApiEnvelope.success(courseService.listSemesters());
    }
}
