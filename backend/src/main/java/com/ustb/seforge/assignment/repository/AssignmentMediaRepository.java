package com.ustb.seforge.assignment.repository;
import com.ustb.seforge.assignment.domain.AssignmentMedia;
import org.springframework.data.jpa.repository.*;
public interface AssignmentMediaRepository extends JpaRepository<AssignmentMedia,Long> {
    @Query("select coalesce(sum(m.sizeBytes),0) from AssignmentMedia m where m.ownerId = :owner")
    long bytesByOwner(Long owner);
    @Query("select coalesce(sum(m.sizeBytes),0) from AssignmentMedia m where m.courseId = :course")
    long bytesByCourse(Long course);
}
