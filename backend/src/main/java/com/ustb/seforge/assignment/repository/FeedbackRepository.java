package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.Feedback;
import com.ustb.seforge.assignment.domain.FeedbackSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findAllByGradeIdOrderByIdAsc(Long gradeId);
    List<Feedback> findAllByGradeIdAndSource(Long gradeId, FeedbackSource source);
}
