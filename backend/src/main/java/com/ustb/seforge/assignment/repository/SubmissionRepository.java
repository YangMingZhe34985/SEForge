package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id")
    Optional<Submission> findForGrading(@Param("id") Long id);
    Optional<Submission> findByIdAndAssignmentIdAndUserId(Long id, Long assignmentId, Long userId);
    Optional<Submission> findByAssignmentIdAndUserIdAndSubmissionKey(Long assignmentId, Long userId, String submissionKey);
    Optional<Submission> findFirstByAssignmentIdAndUserIdAndStatusOrderByAttemptNoDesc(
            Long assignmentId, Long userId, SubmissionStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Submission s
            where s.assignmentId = :assignmentId and s.userId = :userId and s.status = :status
            order by s.attemptNo desc
            """)
    List<Submission> findDraftForUpdate(@Param("assignmentId") Long assignmentId,
                                        @Param("userId") Long userId,
                                        @Param("status") SubmissionStatus status);
    Optional<Submission> findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(Long assignmentId, Long userId);
    long countByAssignmentIdAndUserId(Long assignmentId, Long userId);
    long countByAssignmentIdAndUserIdAndStatusIn(
            Long assignmentId, Long userId, Collection<SubmissionStatus> statuses);
    List<Submission> findAllByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId);
    List<Submission> findAllByAssignmentIdAndStatusInOrderBySubmittedAtDesc(
            Long assignmentId, Collection<SubmissionStatus> statuses);
    List<Submission> findAllByCourseIdAndUserIdOrderByUpdatedAtDesc(Long courseId, Long userId);
}
