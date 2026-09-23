package com.ustb.seforge.content.repository;

import com.ustb.seforge.content.domain.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findByIdAndCourseId(Long id, Long courseId);
    Optional<KnowledgeDocument> findByCourseIdAndChecksum(Long courseId, String checksum);
    List<KnowledgeDocument> findAllByCourseIdOrderByCreatedAtDesc(Long courseId);

    @Query("select coalesce(sum(d.sizeBytes),0) from KnowledgeDocument d "
            + "where d.courseId=:courseId and d.status <> com.ustb.seforge.content.domain.DocumentStatus.DELETED")
    long sumStoredBytesByCourseId(@Param("courseId") Long courseId);
}
