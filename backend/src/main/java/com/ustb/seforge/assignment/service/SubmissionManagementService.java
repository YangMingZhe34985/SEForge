package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.api.TeacherSubmissionView;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.identity.repository.UserRepository;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionManagementService {
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final SubmissionService submissionViews;
    private final UserRepository users;
    private final CourseAccessService courseAccess;

    public SubmissionManagementService(AssignmentRepository assignments, SubmissionRepository submissions,
                                       SubmissionService submissionViews, UserRepository users,
                                       CourseAccessService courseAccess) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.submissionViews = submissionViews;
        this.users = users;
        this.courseAccess = courseAccess;
    }

    @Transactional(readOnly = true)
    public List<TeacherSubmissionView> listSubmitted(Long assignmentId, Long userId) {
        Assignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Assignment not found"));
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        return submissions.findAllByAssignmentIdAndStatusInOrderBySubmittedAtDesc(assignmentId,
                        EnumSet.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED))
                .stream().map(value -> new TeacherSubmissionView(value.getUserId(),
                        users.findById(value.getUserId()).map(user -> user.getUsername()).orElse(null),
                        submissionViews.view(value))).toList();
    }
}
