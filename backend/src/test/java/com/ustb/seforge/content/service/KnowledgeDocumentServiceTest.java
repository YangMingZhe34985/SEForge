package com.ustb.seforge.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.api.DocumentUploadView;
import com.ustb.seforge.content.domain.DocumentStatus;
import com.ustb.seforge.content.domain.KnowledgeDocument;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.content.infrastructure.VectorIndex;
import com.ustb.seforge.content.repository.IngestionJobRepository;
import com.ustb.seforge.content.repository.KnowledgeChunkRepository;
import com.ustb.seforge.content.repository.KnowledgeDocumentRepository;
import com.ustb.seforge.course.domain.CourseChapter;
import com.ustb.seforge.course.domain.Course;
import com.ustb.seforge.course.repository.CourseChapterRepository;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class KnowledgeDocumentServiceTest {
    private KnowledgeDocumentRepository documents;
    private CourseRepository courses;
    private CourseChapterRepository chapters;
    private KnowledgeChunkRepository chunks;
    private IngestionJobRepository ingestions;
    private CourseAccessService access;
    private ObjectStorage storage;
    private VectorIndex vectors;
    private VectorIndexVersionPolicy versions;
    private AsyncJobService jobs;
    private AuditService audit;
    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp() {
        documents = mock(KnowledgeDocumentRepository.class);
        courses = mock(CourseRepository.class);
        chapters = mock(CourseChapterRepository.class);
        chunks = mock(KnowledgeChunkRepository.class);
        ingestions = mock(IngestionJobRepository.class);
        access = mock(CourseAccessService.class);
        storage = mock(ObjectStorage.class);
        vectors = mock(VectorIndex.class);
        versions = mock(VectorIndexVersionPolicy.class);
        jobs = mock(AsyncJobService.class);
        audit = mock(AuditService.class);
        service = new KnowledgeDocumentService(documents, courses, chapters, chunks, ingestions, access,
                storage, vectors, versions, jobs, new SEForgeProperties(), audit);
    }

    @Test
    void rejectsChapterFromAnotherCourseBeforeDuplicateShortCircuit() {
        CourseChapter foreignChapter = new CourseChapter(20L, null, "Foreign", null, 0);
        when(chapters.findById(42L)).thenReturn(Optional.of(foreignChapter));
        KnowledgeDocument duplicate = new KnowledgeDocument(10L, null, 7L, "notes.md", "old/key",
                "text/markdown", 5L, "checksum", "parser", "embedding", "chunking");
        when(documents.findByCourseIdAndChecksum(eq(10L), anyString())).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> service.upload(10L, 42L, 7L, markdown()))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(exception).hasMessage("Course chapter not found");
                });

        verify(access).requireTeachingStaff(10L, 7L);
        verify(chapters).findById(42L);
        verifyNoInteractions(documents, courses, storage, vectors, versions, jobs, chunks, ingestions);
    }

    @Test
    void rejectsUnknownChapterWithoutWritingDocumentOrObject() {
        when(chapters.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(10L, 99L, 7L, markdown()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(documents, courses, storage, vectors, versions, jobs, chunks, ingestions);
    }

    @Test
    void permitsDuplicateWhenChapterBelongsToCourse() {
        CourseChapter chapter = new CourseChapter(10L, null, "Local", null, 0);
        when(chapters.findById(42L)).thenReturn(Optional.of(chapter));
        KnowledgeDocument duplicate = new KnowledgeDocument(10L, 42L, 7L, "notes.md", "old/key",
                "text/markdown", 5L, "checksum", "parser", "embedding", "chunking");
        when(documents.findByCourseIdAndChecksum(eq(10L), anyString())).thenReturn(Optional.of(duplicate));

        var result = service.upload(10L, 42L, 7L, markdown());

        assertThat(result.duplicate()).isTrue();
        assertThat(result.document().courseId()).isEqualTo(10L);
        assertThat(result.document().chapterId()).isEqualTo(42L);
        verifyNoInteractions(courses, storage, vectors, versions, jobs, chunks);
    }

    @Test
    void recordsSuccessfulCourseResourceUpload() {
        when(courses.findForUpdate(10L)).thenReturn(Optional.of(
                new Course("SE101", "Software Engineering", null, 3L, 7L)));
        when(versions.writeVersion()).thenReturn("embedding-v1");
        when(documents.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", 71L);
            return document;
        });
        AsyncJobView job = new AsyncJobView(81L, JobKind.INGEST_DOCUMENT, JobStatus.QUEUED,
                10L, 0, 3, false, null, null, Instant.now(), Instant.now());
        when(jobs.submit(eq(JobKind.INGEST_DOCUMENT), eq(7L), eq(10L), any(), anyString()))
                .thenReturn(job);
        when(ingestions.findByAsyncJobId(81L)).thenReturn(Optional.empty());

        DocumentUploadView result = service.upload(10L, null, 7L, markdown());

        assertThat(result.duplicate()).isFalse();
        assertThat(result.document().status()).isEqualTo(DocumentStatus.QUEUED);
        verify(audit).record(7L, 10L, "COURSE_RESOURCE_UPLOAD",
                "KNOWLEDGE_DOCUMENT", 71L, AuditService.SUCCEEDED);
    }

    @Test
    void recordsSuccessfulCourseResourceDeletion() throws Exception {
        KnowledgeDocument document = new KnowledgeDocument(10L, null, 7L, "notes.md",
                "courses/10/knowledge/notes.md", "text/markdown", 5L, "checksum",
                "parser", "embedding-v1", "chunking");
        ReflectionTestUtils.setField(document, "id", 71L);
        when(documents.findByIdAndCourseId(71L, 10L)).thenReturn(Optional.of(document));
        when(chunks.findEmbeddingVersionsByDocumentId(71L)).thenReturn(List.of());
        when(versions.managedVersions()).thenReturn(Set.of());

        service.delete(10L, 71L, 7L);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.DELETED);
        verify(storage).delete("courses/10/knowledge/notes.md");
        verify(audit).record(7L, 10L, "COURSE_RESOURCE_DELETE",
                "KNOWLEDGE_DOCUMENT", 71L, AuditService.SUCCEEDED);
    }

    private MockMultipartFile markdown() {
        return new MockMultipartFile("file", "notes.md", "text/markdown", "notes".getBytes());
    }
}
