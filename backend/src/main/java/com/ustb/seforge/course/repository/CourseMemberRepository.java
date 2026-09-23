package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseMemberRepository extends JpaRepository<CourseMember, Long> {
    Optional<CourseMember> findByCourseIdAndUserId(Long courseId, Long userId);
    Optional<CourseMember> findByCourseIdAndUserIdAndStatus(
            Long courseId, Long userId, CourseMemberStatus status);
    List<CourseMember> findAllByCourseIdAndStatusOrderByJoinedAtAsc(Long courseId, CourseMemberStatus status);
    long countByCourseIdAndStatus(Long courseId, CourseMemberStatus status);
    long countByCourseIdAndClassIdAndStatus(
            Long courseId, Long classId, CourseMemberStatus status);
    boolean existsByCourseIdAndUserIdAndStatus(Long courseId, Long userId, CourseMemberStatus status);
    boolean existsByCourseIdAndUserIdAndRoleInAndStatus(
            Long courseId, Long userId, Collection<CourseMemberRole> roles, CourseMemberStatus status);
}
