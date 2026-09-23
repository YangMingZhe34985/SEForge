package com.ustb.seforge.content.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.content.api.DocumentUploadView;
import com.ustb.seforge.content.api.KnowledgeDocumentView;
import com.ustb.seforge.content.domain.DocumentStatus;
import com.ustb.seforge.content.domain.IngestionJob;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import com.ustb.seforge.content.repository.IngestionJobRepository;
import com.ustb.seforge.content.repository.KnowledgeChunkRepository;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.course.domain.CourseChapter;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.repository.CourseChapterRepository;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.AsyncJobService;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDocumentService {
    public static final String PARSER_VERSION = "seforge-parser-v1";
    public static final String CHUNKING_VERSION = "recursive-token-700-100-v1";
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "ppt", "pptx", "docx", "md", "txt");

    private final KnowledgeDocumentRepository documents;
    private final CourseRepository courses;
    private final CourseChapterRepository chapters;
    private final KnowledgeChunkRepository chunks;
    private final IngestionJobRepository ingestions;
    private final CourseAccessService access;
    private final ObjectStorage storage;
    private final VectorIndex vectors;
    private final VectorIndexVersionPolicy versions;
    private final AsyncJobService jobs;
    private final SEForgeProperties properties;
    private final AuditService audit;

    public KnowledgeDocumentService(KnowledgeDocumentRepository documents, CourseRepository courses,
                                    CourseChapterRepository chapters,
                                    KnowledgeChunkRepository chunks,
                                    IngestionJobRepository ingestions, CourseAccessService access,
                                    ObjectStorage storage, VectorIndex vectors,
                                    VectorIndexVersionPolicy versions, AsyncJobService jobs,
                                    SEForgeProperties properties, AuditService audit) {
        this.documents = documents;
        this.courses = courses;
        this.chapters = chapters;
        this.chunks = chunks;
        this.ingestions = ingestions;
        this.access = access;
        this.storage = storage;
        this.vectors = vectors;
        this.versions = versions;
        this.jobs = jobs;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public DocumentUploadView upload(Long courseId, Long chapterId, Long actorId, MultipartFile file) {
        access.requireTeachingStaff(courseId, actorId);
        if (chapterId != null) requireChapter(courseId, chapterId);
        ValidatedFile validated = validate(file);
        String checksum = sha256(file);
        KnowledgeDocument existing = documents.findByCourseIdAndChecksum(courseId, checksum).orElse(null);
        if (existing != null && existing.getStatus() != DocumentStatus.DELETED) {
            AsyncJobView currentJob = currentJob(existing);
            return new DocumentUploadView(KnowledgeDocumentView.from(existing, currentJob), currentJob, true);
        }
        courses.findForUpdate(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found"));
        long usedBytes = documents.sumStoredBytesByCourseId(courseId);
        long quotaBytes = properties.getStorage().getCourseQuotaBytes();
        if (quotaBytes < 1 || file.getSize() > quotaBytes - Math.min(usedBytes, quotaBytes)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Course document storage quota would be exceeded");
        }

        String objectKey = "courses/" + courseId + "/knowledge/" + UUID.randomUUID() + "." + validated.extension();
        try (InputStream input = file.getInputStream()) {
            storage.put(objectKey, input, file.getSize(), validated.mediaType());
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Could not store document");
        }

        KnowledgeDocument document;
        String writeVersion = versions.writeVersion();
        if (existing == null) {
            document = new KnowledgeDocument(courseId, chapterId, actorId, validated.fileName(), objectKey,
                    validated.mediaType(), file.getSize(), checksum, PARSER_VERSION,
                    writeVersion, CHUNKING_VERSION);
        } else {
            existing.replaceUpload(chapterId, actorId, validated.fileName(), objectKey, validated.mediaType(),
                    file.getSize(), PARSER_VERSION, writeVersion, CHUNKING_VERSION);
            document = existing;
        }
        document = documents.save(document);
        AsyncJobView job = enqueue(document, actorId, "upload", writeVersion);
        document.queued();
        audit.record(actorId, courseId, "COURSE_RESOURCE_UPLOAD",
                "KNOWLEDGE_DOCUMENT", document.getId(), AuditService.SUCCEEDED);
        return new DocumentUploadView(KnowledgeDocumentView.from(document, job), job, false);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentView> list(Long courseId, Long actorId) {
        access.requireMember(courseId, actorId);
        boolean includeJobs = access.isAdmin(actorId) || access.roleFor(courseId, actorId)
                .filter(role -> role == CourseMemberRole.TEACHER || role == CourseMemberRole.TA)
                .isPresent();
        return documents.findAllByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(document -> document.getStatus() != DocumentStatus.DELETED)
                .map(document -> KnowledgeDocumentView.from(
                        document, includeJobs ? currentJob(document) : null)).toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument requireReadable(Long courseId, Long documentId, Long actorId) {
        access.requireMember(courseId, actorId);
        KnowledgeDocument document = require(courseId, documentId);
        if (document.getStatus() == DocumentStatus.DELETED) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found");
        }
        return document;
    }

    @Transactional
    public AsyncJobView reindex(Long courseId, Long documentId, Long actorId) {
        access.requireTeachingStaff(courseId, actorId);
        KnowledgeDocument document = require(courseId, documentId);
        if (document.getStatus() == DocumentStatus.DELETED) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found");
        }
        String writeVersion = versions.writeVersion();
        document.reindex(writeVersion);
        AsyncJobView job = enqueue(document, actorId,
                "reindex:" + document.getVersion(), writeVersion);
        if (document.getIngestedAt() == null) document.queued();
        return job;
    }

    @Transactional
    public void delete(Long courseId, Long documentId, Long actorId) {
        access.requireTeachingStaff(courseId, actorId);
        KnowledgeDocument document = require(courseId, documentId);
        if (document.getStatus() == DocumentStatus.DELETED) return;
        LinkedHashSet<String> indexedVersions = new LinkedHashSet<>(
                chunks.findEmbeddingVersionsByDocumentId(documentId));
        indexedVersions.add(document.getEmbeddingVersion());
        indexedVersions.addAll(versions.managedVersions());
        document.deleting();
        try {
            for (String embeddingVersion : indexedVersions) {
                vectors.deleteDocument(embeddingVersion, courseId, documentId);
            }
            chunks.deleteAllByDocumentId(documentId);
            storage.delete(document.getObjectKey());
            document.deleted();
            audit.record(actorId, courseId, "COURSE_RESOURCE_DELETE",
                    "KNOWLEDGE_DOCUMENT", documentId, AuditService.SUCCEEDED);
        } catch (IOException | RuntimeException exception) {
            document.failed(exception);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Document deletion could not be completed safely");
        }
    }

    public InputStream open(KnowledgeDocument document) {
        try {
            return storage.open(document.getObjectKey());
        } catch (IOException exception) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document object is unavailable");
        }
    }

    private AsyncJobView enqueue(KnowledgeDocument document, Long actorId, String reason,
                                 String embeddingVersion) {
        AsyncJobView job = jobs.submit(JobKind.INGEST_DOCUMENT, actorId, document.getCourseId(),
                Map.of("documentId", document.getId(), "courseId", document.getCourseId(),
                        "embeddingVersion", embeddingVersion),
                "document:" + document.getId() + ":" + embeddingVersion + ":" + reason);
        if (ingestions.findByAsyncJobId(job.id()).isEmpty()) {
            ingestions.save(new IngestionJob(document.getCourseId(), document.getId(), job.id(), actorId,
                    document.getParserVersion(), embeddingVersion));
        }
        return job;
    }

    private KnowledgeDocument require(Long courseId, Long documentId) {
        return documents.findByIdAndCourseId(documentId, courseId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found"));
    }

    private CourseChapter requireChapter(Long courseId, Long chapterId) {
        CourseChapter chapter = chapters.findById(chapterId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course chapter not found"));
        if (!chapter.getCourseId().equals(courseId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course chapter not found");
        }
        return chapter;
    }

    private AsyncJobView currentJob(KnowledgeDocument document) {
        return ingestions.findFirstByDocumentIdOrderByIdDesc(document.getId())
                .flatMap(ingestion -> jobs.findForCourse(ingestion.getAsyncJobId(), document.getCourseId()))
                .orElse(null);
    }

    private ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Document is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Document exceeds the 100 MB limit");
        }
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safeName = original.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1).trim();
        if (safeName.isBlank() || safeName.length() > 255 || safeName.contains("..")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Document name is invalid");
        }
        int dot = safeName.lastIndexOf('.');
        String extension = dot < 0 ? "" : safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Supported formats: PDF, PPT/PPTX, DOCX, Markdown and TXT");
        }
        String mediaType = mediaType(extension);
        verifySignature(file, extension);
        return new ValidatedFile(safeName, extension, mediaType);
    }

    private void verifySignature(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            boolean valid = switch (extension) {
                case "pdf" -> header.length >= 4 && header[0] == '%' && header[1] == 'P'
                        && header[2] == 'D' && header[3] == 'F';
                case "docx", "pptx" -> header.length >= 2 && header[0] == 'P' && header[1] == 'K';
                case "ppt" -> header.length >= 4 && (header[0] & 0xff) == 0xd0 && (header[1] & 0xff) == 0xcf
                        && (header[2] & 0xff) == 0x11 && (header[3] & 0xff) == 0xe0;
                default -> java.util.Arrays.stream(toUnsigned(header)).noneMatch(value -> value == 0);
            };
            if (!valid) throw new AppException(ErrorCode.VALIDATION_FAILED, "File content does not match its extension");
        } catch (IOException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Document could not be read");
        }
    }

    private int[] toUnsigned(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int index = 0; index < bytes.length; index++) values[index] = bytes[index] & 0xff;
        return values;
    }

    private String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Document checksum could not be calculated");
        }
    }

    private String mediaType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "md" -> "text/markdown";
            default -> "text/plain";
        };
    }

    private record ValidatedFile(String fileName, String extension, String mediaType) {}
}
