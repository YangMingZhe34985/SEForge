package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.TutorInteraction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutorInteractionRepository extends JpaRepository<TutorInteraction, Long> {
    List<TutorInteraction> findAllByUserIdAndAssignmentIdOrderByCreatedAtDesc(Long userId, Long assignmentId);
    long countByCourseId(Long courseId);
}
