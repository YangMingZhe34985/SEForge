package com.ustb.seforge.course.service;

import com.ustb.seforge.course.api.AdminOverviewView;
import com.ustb.seforge.course.api.SemesterView;
import com.ustb.seforge.course.domain.CourseStatus;
import com.ustb.seforge.course.domain.SemesterStatus;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.course.repository.SemesterRepository;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOverviewService {
    private final UserProfileRepository profiles;
    private final CourseRepository courses;
    private final SemesterRepository semesters;

    public AdminOverviewService(UserProfileRepository profiles, CourseRepository courses,
                                SemesterRepository semesters) {
        this.profiles = profiles;
        this.courses = courses;
        this.semesters = semesters;
    }

    @Transactional(readOnly = true)
    public AdminOverviewView current() {
        SemesterView current = semesters.findFirstByStatusOrderByStartsOnDesc(SemesterStatus.ACTIVE)
                .map(value -> new SemesterView(value.getId(), value.getCode(), value.getName(),
                        value.getStartsOn(), value.getEndsOn(), value.getStatus()))
                .orElse(null);
        return new AdminOverviewView(
                profiles.countByAccountType(AccountType.TEACHER),
                profiles.countByAccountType(AccountType.STUDENT),
                courses.countByStatus(CourseStatus.ACTIVE),
                courses.countByStatus(CourseStatus.ARCHIVED), current);
    }
}
