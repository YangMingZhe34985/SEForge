package com.ustb.seforge.content.repository;

import com.ustb.seforge.content.domain.KnowledgeChunk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    void deleteAllByDocumentId(Long documentId);
    void deleteAllByDocumentIdAndEmbeddingVersion(Long documentId, String embeddingVersion);
    List<KnowledgeChunk> findAllByDocumentIdOrderByChunkIndexAsc(Long documentId);
    List<KnowledgeChunk> findAllByDocumentIdAndEmbeddingVersionOrderByChunkIndexAsc(
            Long documentId, String embeddingVersion);
    List<KnowledgeChunk> findAllByVectorIdIn(List<String> vectorIds);
    Optional<KnowledgeChunk> findByVectorId(String vectorId);
    Optional<KnowledgeChunk> findByVectorIdAndCourseIdAndEmbeddingVersion(
            String vectorId, Long courseId, String embeddingVersion);
    long countByDocumentIdAndEmbeddingVersion(Long documentId, String embeddingVersion);

    @Query("select c.vectorId from KnowledgeChunk c "
            + "where c.courseId = :courseId and c.embeddingVersion = :embeddingVersion")
    List<String> findVectorIdsByCourseIdAndEmbeddingVersion(
            @Param("courseId") Long courseId,
            @Param("embeddingVersion") String embeddingVersion);

    @Query("select distinct c.embeddingVersion from KnowledgeChunk c where c.documentId = :documentId")
    List<String> findEmbeddingVersionsByDocumentId(@Param("documentId") Long documentId);
}
