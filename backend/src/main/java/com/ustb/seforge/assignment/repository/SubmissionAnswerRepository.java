package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {
    List<SubmissionAnswer> findAllBySubmissionIdOrderByIdAsc(Long submissionId);
    Optional<SubmissionAnswer> findBySubmissionIdAndQuestionId(Long submissionId, Long questionId);

    @Query(value = """
            select coalesce(sum(answer.attachment_size_bytes), 0)
            from submission_answer answer
            join submission submission on submission.id = answer.submission_id
            where submission.course_id = :courseId
              and answer.attachment_object_key is not null
            """, nativeQuery = true)
    long sumAttachmentBytesByCourseId(@Param("courseId") Long courseId);

    @Query(value = """
            select coalesce(sum(answer.attachment_size_bytes), 0)
            from submission_answer answer
            join submission submission on submission.id = answer.submission_id
            where submission.user_id = :userId
              and answer.attachment_object_key is not null
            """, nativeQuery = true)
    long sumAttachmentBytesByUserId(@Param("userId") Long userId);
}
