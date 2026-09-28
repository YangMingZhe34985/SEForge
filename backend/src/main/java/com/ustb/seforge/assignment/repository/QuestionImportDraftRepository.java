package com.ustb.seforge.assignment.repository;
import com.ustb.seforge.assignment.domain.QuestionImportDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface QuestionImportDraftRepository extends JpaRepository<QuestionImportDraft,Long> {
    Optional<QuestionImportDraft> findBySourceMediaId(Long sourceMediaId);
}
