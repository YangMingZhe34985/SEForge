package com.ustb.seforge.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.AiGateway;
import com.ustb.seforge.ai.application.AiResponse;
import com.ustb.seforge.ai.application.PromptCatalog;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.GradeRepository;
import com.ustb.seforge.assignment.repository.RubricItemRepository;
import com.ustb.seforge.assignment.repository.RubricRepository;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.assignment.service.GradeSuggestionService;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.content.service.DocumentParserService;
import com.ustb.seforge.course.repository.CourseResourceRepository;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobSnapshot;
import com.ustb.seforge.review.api.ExternalSonarFinding;
import com.ustb.seforge.review.domain.ReviewJob;
import com.ustb.seforge.review.domain.ReviewJobStatus;
import com.ustb.seforge.review.domain.ReviewReport;
import com.ustb.seforge.review.domain.ReviewType;
import com.ustb.seforge.review.repository.ReviewJobRepository;
import com.ustb.seforge.review.repository.ReviewReportRepository;
import com.ustb.seforge.review.service.ReviewSubmissionService.CodeReviewConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewExecutionServiceCodeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReviewJobRepository reviewJobs = mock(ReviewJobRepository.class);
    private final ReviewReportRepository reports = mock(ReviewReportRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final PromptCatalog prompts = mock(PromptCatalog.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final SonarGateway sonar = mock(SonarGateway.class);
    private final AsyncJobService asyncJobs = mock(AsyncJobService.class);
    private ReviewExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ReviewExecutionService(
                reviewJobs,
                reports,
                mock(KnowledgeDocumentRepository.class),
                mock(CourseResourceRepository.class),
                mock(AssignmentRepository.class),
                mock(AssignmentQuestionRepository.class),
                mock(RubricRepository.class),
                mock(RubricItemRepository.class),
                mock(SubmissionRepository.class),
                mock(SubmissionAnswerRepository.class),
                mock(GradeRepository.class),
                mock(GradeSuggestionService.class),
                storage,
                mock(DocumentParserService.class),
                prompts,
                ai,
                new ReviewResultValidator(),
                new SecureArchiveValidator(),
                sonar,
                objectMapper,
                asyncJobs);
        when(asyncJobs.isExecutionActive(77L, "test-worker")).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(asyncJobs).runIfActive(org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq("test-worker"), any(Runnable.class));
        when(asyncJobs.completeAtomically(org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq("test-worker"), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<String>>getArgument(2).get());
    }

    @Test
    void codeReviewScansServerOwnedArchiveAndPersistsAuthoritativeFindings() throws Exception {
        byte[] archive = zip("src/App.java", "class App { void leak() {} }");
        ReviewJob review = review();
        when(reviewJobs.findByAsyncJobId(77L)).thenReturn(Optional.of(review));
        when(reports.findByReviewJobId(31L)).thenReturn(Optional.empty());
        when(storage.open(review.getObjectKey())).thenReturn(new ByteArrayInputStream(archive));
        ExternalSonarFinding finding = new ExternalSonarFinding(
                "issue-1", "java:S2095", "BUG", "MAJOR", "project:src/App.java", 1,
                "Close this resource");
        AtomicReference<Path> workspace = new AtomicReference<>();
        when(sonar.analyze(any())).thenAnswer(invocation -> {
            SonarGateway.ScanRequest request = invocation.getArgument(0);
            workspace.set(request.workspaceDirectory());
            assertThat(Files.readString(request.sourceDirectory().resolve("src/App.java")))
                    .contains("class App");
            assertThat(request.projectKey()).isEqualTo("seforge-review-4-31");
            return new SonarGateway.Analysis(request.projectKey(), "task-1", "analysis-1",
                    "ERROR", Map.of("bugs", "1", "complexity", "2"), List.of(finding));
        });
        when(prompts.load("code-review", "v2"))
                .thenReturn(new PromptCatalog.PromptTemplate("code-review", "v2", "Explain issues"));
        when(ai.complete(any())).thenReturn(new AiResponse("""
                {"summary":"One issue found","explanations":[{"findingKey":"issue-1","explanation":"A resource leaks","impact":"Resource exhaustion","remediation":"Use try-with-resources"}]}
                """, "fake", "fake-coder", 10, 20));
        when(reports.save(any())).thenAnswer(invocation -> {
            ReviewReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 91L);
            return report;
        });

        String result = service.execute(job(), ReviewType.CODE);

        assertThat(result).contains("\"reportId\":91").contains("COMPLETED");
        assertThat(review.getStatus()).isEqualTo(ReviewJobStatus.COMPLETED);
        assertThat(workspace.get()).doesNotExist();
        ArgumentCaptor<ReviewReport> saved = ArgumentCaptor.forClass(ReviewReport.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getStructuredResultJson())
                .contains("\"source\":\"SONARQUBE\"")
                .contains("\"computeTaskId\":\"task-1\"")
                .contains("\"findingKey\":\"issue-1\"");
    }

    @Test
    void sonarFailureIsRecordedAndNeverCreatesAReport() throws Exception {
        byte[] archive = zip("App.java", "class App {}");
        ReviewJob review = review();
        when(reviewJobs.findByAsyncJobId(77L)).thenReturn(Optional.of(review));
        when(reports.findByReviewJobId(31L)).thenReturn(Optional.empty());
        when(storage.open(review.getObjectKey())).thenReturn(new ByteArrayInputStream(archive));
        AtomicReference<Path> workspace = new AtomicReference<>();
        when(sonar.analyze(any())).thenAnswer(invocation -> {
            SonarGateway.ScanRequest request = invocation.getArgument(0);
            workspace.set(request.workspaceDirectory());
            throw new SonarGatewayException("SonarQube compute task ended as FAILED");
        });

        assertThatThrownBy(() -> service.execute(job(), ReviewType.CODE))
                .isInstanceOf(SonarGatewayException.class)
                .hasMessageContaining("FAILED");
        assertThat(workspace.get()).doesNotExist();
        verify(reports, never()).save(any());

        service.markFailed(77L, new SonarGatewayException("SonarQube compute task ended as FAILED"));

        assertThat(review.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(review.getErrorCode()).isEqualTo("SONAR_FAILED");
        assertThat(review.getErrorMessage()).contains("FAILED");
    }

    private ReviewJob review() throws Exception {
        String config = objectMapper.writeValueAsString(new CodeReviewConfig("SONARQUBE"));
        ReviewJob review = new ReviewJob(4L, 8L, 15L, null, null, 22L,
                ReviewType.CODE, "courses/4/submissions/15/source.zip", config);
        ReflectionTestUtils.setField(review, "id", 31L);
        review.attachAsyncJob(77L);
        when(reviewJobs.findById(31L)).thenReturn(Optional.of(review));
        return review;
    }

    private JobSnapshot job() {
        return new JobSnapshot(77L, JobKind.REVIEW_CODE, 22L, 4L, "{}", 1, "test-worker");
    }

    private byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
