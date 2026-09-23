package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentQuestionRepository extends JpaRepository<AssignmentQuestion, Long> {
    List<AssignmentQuestion> findAllByAssignmentIdOrderBySortOrderAscIdAsc(Long assignmentId);
    Optional<AssignmentQuestion> findByIdAndAssignmentId(Long id, Long assignmentId);
    long countByAssignmentId(Long assignmentId);
}
