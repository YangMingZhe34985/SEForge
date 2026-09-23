package com.ustb.seforge.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.course.repository.CourseResourceRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.review.domain.ReviewType;
import com.ustb.seforge.review.repository.ReviewJobRepository;
import com.ustb.seforge.review.repository.ReviewReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ReviewSubmissionServiceTest {
    @Mock ReviewJobRepository reviewJobs;
    @Mock ReviewReportRepository reports;
    @Mock KnowledgeDocumentRepository documents;
    @Mock CourseResourceRepository resources;
    @Mock SubmissionRepository submissions;
    @Mock SubmissionAnswerRepository answers;
    @Mock CourseAccessService courseAccess;
    @Mock AsyncJobService asyncJobs;
    @Mock ObjectMapper objectMapper;
    @Mock AuditService audit;
    @InjectMocks ReviewSubmissionService service;

    @Test
    void filtersAndPagesReviewHistoryAtTheDatabase() {
        PageRequest pageable = PageRequest.of(2, 20);
        when(reviewJobs.findAllByCourseIdAndReviewTypeOrderByCreatedAtDesc(
                42L, ReviewType.CODE, pageable)).thenReturn(Page.empty(pageable));

        var result = service.list(42L, 7L, ReviewType.CODE, 2, 20);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.total()).isZero();
        verify(courseAccess).requireTeachingStaff(42L, 7L);
        verifyNoInteractions(reports, documents, resources, submissions, answers, asyncJobs, audit);
    }
}
