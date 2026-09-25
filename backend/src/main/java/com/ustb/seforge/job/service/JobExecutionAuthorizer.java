package com.ustb.seforge.job.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job identifiers locate current authority; they never grant authority themselves. */
@Service
public class JobExecutionAuthorizer {
    private final UserRepository users;
    private final CourseAccessService courses;
    public JobExecutionAuthorizer(UserRepository users, CourseAccessService courses) {
        this.users = users; this.courses = courses;
    }
    @Transactional(readOnly = true)
    public void authorize(JobSnapshot job) {
        if (job.ownerUserId() == null || job.courseId() == null) throw denied();
        users.findById(job.ownerUserId()).filter(u -> u.isEnabled() && !u.isLocked()).orElseThrow(this::denied);
        // Every currently registered job kind is submitted by course teaching staff.
        // Resource existence/state is subsequently reloaded by its business handler.
        courses.requireTeachingStaff(job.courseId(), job.ownerUserId());
    }
    private AppException denied() { return new AppException(ErrorCode.ACCESS_DENIED, "Job execution is no longer authorized"); }
}
