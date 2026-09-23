package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.RubricItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricItemRepository extends JpaRepository<RubricItem, Long> {
    List<RubricItem> findAllByRubricIdOrderBySortOrderAscIdAsc(Long rubricId);
    Optional<RubricItem> findByIdAndRubricId(Long id, Long rubricId);
}
