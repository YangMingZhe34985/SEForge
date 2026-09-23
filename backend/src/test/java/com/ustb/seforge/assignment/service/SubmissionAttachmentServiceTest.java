package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.QuestionType;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionAnswer;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.SubmissionAnswerRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.course.domain.Course;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.identity.domain.User;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.review.service.SecureArchiveValidator;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class SubmissionAttachmentServiceTest {
    private SubmissionService submissions;
    private AssignmentQuestionRepository questions;
    private SubmissionAnswerRepository answers;
    private UserRepository users;
    private CourseRepository courses;
    private ObjectStorage storage;
    private SEForgeProperties properties;
    private SubmissionAttachmentService service;

    @BeforeEach
    void setUp() {
        submissions = mock(SubmissionService.class);
        questions = mock(AssignmentQuestionRepository.class);
        answers = mock(SubmissionAnswerRepository.class);
        users = mock(UserRepository.class);
        courses = mock(CourseRepository.class);
        storage = mock(ObjectStorage.class);
        properties = new SEForgeProperties();
        service = new SubmissionAttachmentService(submissions, questions, answers, users, courses, storage,
                new SecureArchiveValidator(), properties);
    }

    @Test
    void rejectsUnauthorizedStudentBeforeLookingUpQuestionOrWritingStorage() {
        MultipartFile file = zip("source.zip", "application/zip");
        when(submissions.requireCurrentDraftForAttachment(1L, 7L))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED, "denied"));

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessage("denied");

        verifyNoInteractions(questions, answers, storage);
    }

    @Test
    void rejectsNonCodeQuestion() throws Exception {
        MultipartFile file = zip("source.zip", "application/zip");
        Submission submission = submission();
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        when(question.getQuestionType()).thenReturn(QuestionType.ANALYSIS);
        when(submissions.requireCurrentDraftForAttachment(1L, 7L)).thenReturn(submission);
        when(questions.findByIdAndAssignmentId(2L, 1L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("CODE");

        verify(storage, never()).put(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void rejectsFileWhoseMetadataClaimsZipButContentIsNotZip() {
        MultipartFile file = new MockMultipartFile(
                "file", "source.zip", "application/zip", new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not a ZIP");

        verifyNoInteractions(submissions, questions, answers, storage);
    }

    @Test
    void rejectsNonZipExtensionEvenWithZipMimeAndSignature() {
        MultipartFile file = zip("source.jar", "application/zip");

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Only ZIP");

        verifyNoInteractions(submissions, questions, answers, storage);
    }

    @Test
    void rejectsNonZipMimeEvenWithZipExtensionAndSignature() {
        MultipartFile file = zip("source.zip", "text/plain");

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("MIME");

        verifyNoInteractions(submissions, questions, answers, storage);
    }

    @Test
    void rejectsArchiveLargerThanFiftyMegabytesWithoutReadingIt() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(50L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("50 MB");

        verify(file, never()).getOriginalFilename();
        verifyNoInteractions(submissions, questions, answers, storage);
    }

    @Test
    void generatesObjectKeyPersistsItAndRemovesReplacedObject() throws Exception {
        MultipartFile file = zip("student source.zip", "application/x-zip-compressed");
        Submission submission = submission();
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        when(question.getQuestionType()).thenReturn(QuestionType.CODE);
        when(submissions.requireCurrentDraftForAttachment(1L, 7L)).thenReturn(submission);
        when(questions.findByIdAndAssignmentId(2L, 1L)).thenReturn(Optional.of(question));
        SubmissionAnswer answer = new SubmissionAnswer(30L, 2L, "draft", null, "old/key.zip");
        when(answers.findBySubmissionIdAndQuestionId(30L, 2L)).thenReturn(Optional.of(answer));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(mock(User.class)));
        when(courses.findForUpdate(10L)).thenReturn(Optional.of(mock(Course.class)));

        var result = service.upload(1L, 2L, 7L, file);

        assertThat(result.objectKey()).startsWith("courses/10/submissions/30/questions/2/")
                .endsWith("/student_source.zip");
        assertThat(result.fileName()).isEqualTo("student_source.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(answer.getAttachmentObjectKey()).isEqualTo(result.objectKey());
        assertThat(answer.getAttachmentSizeBytes()).isEqualTo(file.getSize());
        verify(storage).put(anyString(), any(InputStream.class), anyLong(),
                org.mockito.ArgumentMatchers.eq("application/zip"));
        verify(storage).delete("old/key.zip");
        verify(answers).save(answer);
    }

    @Test
    void rejectsZipSlipBeforeWritingObjectStorage() throws Exception {
        MultipartFile file = zip("source.zip", "application/zip", "../outside.java");
        stubAuthorizedCodeUpload();

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("unsafe path");

        verify(storage, never()).put(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void rejectsUploadThatWouldExceedUserCumulativeQuota() throws Exception {
        MultipartFile file = zip("source.zip", "application/zip");
        stubAuthorizedCodeUpload();
        when(courses.findForUpdate(10L)).thenReturn(Optional.of(mock(Course.class)));
        properties.getStorage().setAttachmentUserQuotaBytes(file.getSize() + 5);
        when(answers.sumAttachmentBytesByUserId(7L)).thenReturn(6L);

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("User attachment storage quota");

        verify(storage, never()).put(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void rejectsUploadThatWouldExceedCourseCumulativeQuota() throws Exception {
        MultipartFile file = zip("source.zip", "application/zip");
        stubAuthorizedCodeUpload();
        when(courses.findForUpdate(10L)).thenReturn(Optional.of(mock(Course.class)));
        properties.getStorage().setAttachmentCourseQuotaBytes(file.getSize() + 5);
        when(answers.sumAttachmentBytesByCourseId(10L)).thenReturn(6L);

        assertThatThrownBy(() -> service.upload(1L, 2L, 7L, file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Course attachment storage quota");

        verify(storage, never()).put(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    private void stubAuthorizedCodeUpload() {
        AssignmentQuestion question = mock(AssignmentQuestion.class);
        Submission submission = submission();
        when(question.getQuestionType()).thenReturn(QuestionType.CODE);
        when(submissions.requireCurrentDraftForAttachment(1L, 7L)).thenReturn(submission);
        when(questions.findByIdAndAssignmentId(2L, 1L)).thenReturn(Optional.of(question));
        when(answers.findBySubmissionIdAndQuestionId(30L, 2L)).thenReturn(Optional.empty());
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(mock(User.class)));
    }

    private Submission submission() {
        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(30L);
        when(submission.getCourseId()).thenReturn(10L);
        return submission;
    }

    private MultipartFile zip(String name, String mediaType) {
        return zip(name, mediaType, "src/App.java");
    }

    private MultipartFile zip(String name, String mediaType, String entryName) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream archive = new ZipOutputStream(output)) {
                archive.putNextEntry(new ZipEntry(entryName));
                archive.write("class App {}".getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
            return new MockMultipartFile("file", name, mediaType, output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
