package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.Rubric;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, Long> {
    Optional<Rubric> findByAssignmentId(Long assignmentId);
}
