package com.ustb.seforge.content.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.repository.CourseRepository;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes vector publication and reconciliation across API/worker processes. */
@Service
public class CourseIndexWriteService {
    private final CourseRepository courses;
    public CourseIndexWriteService(CourseRepository courses) { this.courses = courses; }

    @Transactional
    public <T> T execute(Long courseId, Supplier<T> action) {
        courses.findForUpdate(courseId).orElseThrow(() ->
                new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found"));
        return action.get();
    }
}
