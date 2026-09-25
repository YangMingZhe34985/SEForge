package com.ustb.seforge.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.course.domain.CourseResource;
import com.ustb.seforge.course.domain.ResourceStatus;
import com.ustb.seforge.course.repository.CourseResourceRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobStatus;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.review.api.CreateAssignmentReviewRequest;
import com.ustb.seforge.review.api.CreateCodeReviewRequest;
import com.ustb.seforge.review.api.CreateDocumentReviewRequest;
import com.ustb.seforge.review.api.ReviewJobView;
import com.ustb.seforge.review.api.ReviewReportView;
import com.ustb.seforge.review.domain.ReviewJob;
import com.ustb.seforge.review.domain.ReviewJobStatus;
import com.ustb.seforge.review.domain.ReviewReport;
import com.ustb.seforge.review.domain.ReviewType;
import com.ustb.seforge.review.repository.ReviewJobRepository;
import com.ustb.seforge.review.repository.ReviewReportRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewSubmissionService {
    private static final Set<String> DOCUMENT_KINDS = Set.of(
            "SRS", "DESIGN", "TEST_REPORT", "README", "API", "GENERAL");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "ppt", "pptx", "docx", "md", "txt");
    private final ReviewJobRepository reviewJobs;
    private final ReviewReportRepository reports;
    private final KnowledgeDocumentRepository documents;
    private final CourseResourceRepository resources;
    private final SubmissionRepository submissions;
    private final SubmissionAnswerRepository answers;
    private final CourseAccessService courseAccess;
    private final AsyncJobService asyncJobs;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public ReviewSubmissionService(
            ReviewJobRepository reviewJobs,
            ReviewReportRepository reports,
            KnowledgeDocumentRepository documents,
            CourseResourceRepository resources,
            SubmissionRepository submissions,
            SubmissionAnswerRepository answers,
            CourseAccessService courseAccess,
            AsyncJobService asyncJobs,
            ObjectMapper objectMapper,
            AuditService audit) {
        this.reviewJobs = reviewJobs;
        this.reports = reports;
        this.documents = documents;
        this.resources = resources;
        this.submissions = submissions;
        this.answers = answers;
        this.courseAccess = courseAccess;
        this.asyncJobs = asyncJobs;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public ReviewJobView submitDocument(Long courseId, Long userId,
                                        CreateDocumentReviewRequest request) {
        courseAccess.requireTeachingStaff(courseId, userId);
        boolean documentTarget = request.documentId() != null;
        boolean resourceTarget = request.resourceId() != null;
        if (documentTarget == resourceTarget) {
            throw malformed("Exactly one of documentId or resourceId is required");
        }
        Long documentId = null;
        Long resourceId = null;
        String objectKey;
        String fileName;
        String mediaType;
        long size;
        if (documentTarget) {
            KnowledgeDocument document = documents.findByIdAndCourseId(request.documentId(), courseId)
                    .orElseThrow(this::notFound);
            documentId = document.getId();
            objectKey = document.getObjectKey();
            fileName = document.getOriginalName();
            mediaType = document.getMediaType();
            size = document.getSizeBytes();
        } else {
            CourseResource resource = resources.findByIdAndCourseIdAndStatus(
                            request.resourceId(), courseId, ResourceStatus.ACTIVE)
                    .orElseThrow(this::notFound);
            resourceId = resource.getId();
            objectKey = resource.getObjectKey();
            fileName = resource.getName();
            mediaType = resource.getContentType();
            size = resource.getSizeBytes() == null ? 0 : resource.getSizeBytes();
        }
        if (size > 25L * 1024 * 1024) throw malformed("Document review is limited to 25 MB");
        if (!DOCUMENT_EXTENSIONS.contains(extension(fileName))) {
            throw malformed("Document review supports PDF, PPT/PPTX, DOCX, Markdown and TXT files");
        }
        ReviewJob review = new ReviewJob(courseId, null, null, documentId, resourceId, userId,
                ReviewType.DOCUMENT, objectKey, json(Map.of(
                        "documentKind", documentKind(request.documentKind()),
                        "fileName", fileName,
                        "mediaType", safe(mediaType, "application/octet-stream"))));
        ReviewJobView view = enqueue(review, request.idempotencyKey());
        audit.record(userId, courseId, "REVIEW_DOCUMENT_REQUEST", "REVIEW_JOB", view.id(),
                AuditService.SUCCEEDED);
        return view;
    }

    @Transactional
    public ReviewJobView submitAssignment(Long courseId, Long userId,
                                          CreateAssignmentReviewRequest request) {
        courseAccess.requireTeachingStaff(courseId, userId);
        Submission submission = requireSubmitted(request.submissionId(), courseId);
        ReviewJob review = new ReviewJob(courseId, submission.getAssignmentId(), submission.getId(),
                null, null, userId, ReviewType.ASSIGNMENT, null, "{}");
        ReviewJobView view = enqueue(review, request.idempotencyKey());
        audit.record(userId, courseId, "REVIEW_ASSIGNMENT_REQUEST", "SUBMISSION", submission.getId(),
                AuditService.SUCCEEDED);
        return view;
    }

    @Transactional
    public ReviewJobView submitCode(Long courseId, Long userId, CreateCodeReviewRequest request) {
        courseAccess.requireTeachingStaff(courseId, userId);
        Submission submission = requireSubmitted(request.submissionId(), courseId);
        SubmissionAnswer attachment = answers.findAllBySubmissionIdOrderByIdAsc(submission.getId()).stream()
                .filter(answer -> request.attachmentObjectKey().equals(answer.getAttachmentObjectKey()))
                .findFirst().orElseThrow(() -> malformed("Attachment does not belong to the submission"));
        CodeReviewConfig config = new CodeReviewConfig("SONARQUBE");
        ReviewJob review = new ReviewJob(courseId, submission.getAssignmentId(), submission.getId(),
                null, null, userId, ReviewType.CODE, attachment.getAttachmentObjectKey(), json(config));
        ReviewJobView view = enqueue(review, request.idempotencyKey());
        audit.record(userId, courseId, "REVIEW_CODE_REQUEST", "SUBMISSION", submission.getId(),
                AuditService.SUCCEEDED);
        return view;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewJobView> list(
            Long courseId, Long userId, ReviewType reviewType, int page, int size) {
        courseAccess.requireTeachingStaff(courseId, userId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<ReviewJobView> result = (reviewType == null
                ? reviewJobs.findAllByCourseIdOrderByCreatedAtDesc(courseId, pageable)
                : reviewJobs.findAllByCourseIdAndReviewTypeOrderByCreatedAtDesc(
                        courseId, reviewType, pageable))
                .map(this::view);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ReviewJobView get(Long courseId, Long reviewJobId, Long userId) {
        courseAccess.requireTeachingStaff(courseId, userId);
        return view(requireReview(courseId, reviewJobId));
    }

    @Transactional
    public ReviewJobView retry(Long courseId, Long reviewJobId, Long userId) {
        courseAccess.requireTeachingStaff(courseId, userId);
        ReviewJob review = reviewJobs.findForRetry(reviewJobId, courseId).orElseThrow(this::notFound);
        AsyncJobView previous = asyncJobs.findForCourse(review.getAsyncJobId(), courseId)
                .orElseThrow(this::notFound);
        if (previous.status() != JobStatus.DEAD_LETTER
                && previous.status() != JobStatus.CANCELLED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Review is still retrying automatically; manual retry is not available yet");
        }
        AsyncJobView async = asyncJobs.submit(review.getReviewType().jobKind(), userId, courseId,
                Map.of("reviewJobId", review.getId()),
                "review-retry:" + review.getId() + ":" + UUID.randomUUID());
        review.retryAs(userId, async.id());
        audit.record(userId, courseId, "REVIEW_RETRY", "REVIEW_JOB", review.getId(),
                AuditService.SUCCEEDED);
        return ReviewJobView.from(review);
    }

    @Transactional(readOnly = true)
    public ReviewReportView reportForJob(Long courseId, Long reviewJobId, Long userId) {
        courseAccess.requireTeachingStaff(courseId, userId);
        requireReview(courseId, reviewJobId);
        ReviewReport report = reports.findByReviewJobId(reviewJobId).orElseThrow(this::notFound);
        return ReviewReportView.from(report, objectMapper);
    }

    @Transactional(readOnly = true)
    public ExportedReport export(Long courseId, Long reportId, Long userId) {
        courseAccess.requireTeachingStaff(courseId, userId);
        ReviewReport report = reports.findByIdAndCourseId(reportId, courseId).orElseThrow(this::notFound);
        com.fasterxml.jackson.databind.node.ObjectNode exported = objectMapper.createObjectNode();
        exported.put("reportId", report.getId());
        exported.put("reviewJobId", report.getReviewJobId());
        exported.put("courseId", report.getCourseId());
        exported.put("status", report.getStatus().name());
        exported.put("summary", report.getSummary());
        exported.put("model", report.getModelName());
        exported.put("promptVersion", report.getPromptVersion());
        exported.putPOJO("aiTraceId", report.getAiTraceId());
        exported.put("generatedAt", report.getGeneratedAt().toString());
        try {
            exported.set("result", objectMapper.readTree(report.getStructuredResultJson()));
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Stored review report is invalid");
        }
        return new ExportedReport("seforge-review-" + reportId + ".json",
                json(exported).getBytes(StandardCharsets.UTF_8));
    }

    private ReviewJobView enqueue(ReviewJob review, String idempotencyKey) {
        reviewJobs.save(review);
        AsyncJobView async = asyncJobs.submit(review.getReviewType().jobKind(), review.getRequestedBy(),
                review.getCourseId(), Map.of("reviewJobId", review.getId()),
                idempotencyKey == null ? null : "course:" + review.getCourseId() + ":" + idempotencyKey);
        ReviewJob existing = reviewJobs.findByAsyncJobId(async.id()).orElse(null);
        if (existing != null && !existing.getId().equals(review.getId())) {
            if (!java.util.Objects.equals(existing.getSubmissionId(), review.getSubmissionId())
                    || !java.util.Objects.equals(existing.getDocumentId(), review.getDocumentId())
                    || !java.util.Objects.equals(existing.getResourceId(), review.getResourceId())
                    || !java.util.Objects.equals(existing.getObjectKey(), review.getObjectKey())
                    || !sameConfig(existing.getConfigJson(), review.getConfigJson())) {
                throw new AppException(ErrorCode.CONFLICT, "Idempotency key belongs to another review target");
            }
            reviewJobs.delete(review);
            return ReviewJobView.from(existing);
        }
        review.attachAsyncJob(async.id());
        return ReviewJobView.from(review);
    }

    private Submission requireSubmitted(Long submissionId, Long courseId) {
        Submission submission = submissions.findById(submissionId).orElseThrow(this::notFound);
        if (!courseId.equals(submission.getCourseId())) throw notFound();
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Draft submissions cannot be reviewed");
        }
        return submission;
    }

    private ReviewJob requireReview(Long courseId, Long reviewJobId) {
        return reviewJobs.findByIdAndCourseId(reviewJobId, courseId).orElseThrow(this::notFound);
    }

    private ReviewJobView view(ReviewJob review) {
        return ReviewJobView.from(review, asyncJobs.findForCourse(review.getAsyncJobId(), review.getCourseId()).orElse(null));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw malformed("Review configuration is invalid");
        }
    }

    private boolean sameConfig(String first, String second) {
        try { return objectMapper.readTree(first).equals(objectMapper.readTree(second)); }
        catch (JsonProcessingException invalid) { throw malformed("Review configuration is invalid"); }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String documentKind(String value) {
        String normalized = safe(value, "GENERAL").toUpperCase(Locale.ROOT);
        if (!DOCUMENT_KINDS.contains(normalized)) {
            throw malformed("Unsupported documentKind");
        }
        return normalized;
    }

    private String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private AppException malformed(String message) {
        return new AppException(ErrorCode.MALFORMED_REQUEST, message);
    }

    private AppException notFound() {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Review target not found");
    }

    public record CodeReviewConfig(String source) {
    }

    public record ExportedReport(String fileName, byte[] content) {
    }
}
