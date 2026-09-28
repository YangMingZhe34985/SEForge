package com.ustb.seforge.review.repository;
import com.ustb.seforge.review.domain.ReviewArtifact;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface ReviewArtifactRepository extends JpaRepository<ReviewArtifact,Long> {
    Optional<ReviewArtifact> findByIdAndCourseId(Long id,Long courseId);
    @Query("select coalesce(sum(a.sizeBytes),0) from ReviewArtifact a where a.courseId=:courseId") long bytesByCourse(Long courseId);
    @Query("select coalesce(sum(a.sizeBytes),0) from ReviewArtifact a where a.ownerId=:ownerId") long bytesByOwner(Long ownerId);
}
