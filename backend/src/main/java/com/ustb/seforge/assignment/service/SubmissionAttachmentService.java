package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.api.SubmissionAttachmentView;
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
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.review.service.SecureArchiveValidator;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SubmissionAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionAttachmentService.class);
    private static final long MAX_ARCHIVE_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ZIP_MEDIA_TYPES = Set.of(
            "application/zip", "application/x-zip-compressed", "application/x-zip",
            "application/octet-stream");

    private final SubmissionService submissions;
    private final AssignmentQuestionRepository questions;
    private final SubmissionAnswerRepository answers;
    private final UserRepository users;
    private final CourseRepository courses;
    private final ObjectStorage storage;
    private final SecureArchiveValidator archiveValidator;
    private final SEForgeProperties properties;

    public SubmissionAttachmentService(SubmissionService submissions,
                                       AssignmentQuestionRepository questions,
                                       SubmissionAnswerRepository answers,
                                       UserRepository users,
                                       CourseRepository courses,
                                       ObjectStorage storage,
                                       SecureArchiveValidator archiveValidator,
                                       SEForgeProperties properties) {
        this.submissions = submissions;
        this.questions = questions;
        this.answers = answers;
        this.users = users;
        this.courses = courses;
        this.storage = storage;
        this.archiveValidator = archiveValidator;
        this.properties = properties;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubmissionAttachmentView upload(Long assignmentId, Long questionId, Long userId,
                                           MultipartFile file) {
        ValidatedArchive archive = validate(file);
        Submission submission = submissions.requireCurrentDraftForAttachment(assignmentId, userId);
        submission.requireDraft();
        AssignmentQuestion question = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> notFound("Question not found"));
        if (question.getQuestionType() != QuestionType.CODE) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Source archives can only be attached to CODE questions");
        }

        SubmissionAnswer answer = answers.findBySubmissionIdAndQuestionId(submission.getId(), questionId)
                .orElseGet(() -> new SubmissionAnswer(submission.getId(), questionId, null, null, null));
        String previousObjectKey = answer.getAttachmentObjectKey();
        long previousSize = previousObjectKey == null ? 0 : answer.getAttachmentSizeBytes();

        inspectArchive(file, archive.fileName());
        // Keep a stable lock order. READ_COMMITTED makes the aggregate queries below observe the
        // upload committed by the previous lock holder instead of an older repeatable-read snapshot.
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> notFound("User not found"));
        courses.findForUpdate(submission.getCourseId())
                .orElseThrow(() -> notFound("Course not found"));
        requireWithinQuota(submission.getCourseId(), userId, file.getSize(), previousSize);

        String objectKey = "courses/" + submission.getCourseId()
                + "/submissions/" + submission.getId()
                + "/questions/" + questionId
                + "/" + UUID.randomUUID() + "/" + archive.fileName();

        try (InputStream input = file.getInputStream()) {
            storage.put(objectKey, input, file.getSize(), "application/zip");
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,
                    "Could not store submission attachment");
        }

        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            registerStorageCleanup(objectKey, previousObjectKey);
        }
        try {
            answer.update(answer.getAnswerText(), answer.getAnswerDataJson(), objectKey, file.getSize());
            answers.save(answer);
        } catch (RuntimeException failure) {
            if (!synchronizedTransaction) deleteBestEffort(objectKey);
            throw failure;
        }
        if (!synchronizedTransaction && previousObjectKey != null
                && !previousObjectKey.equals(objectKey)) {
            deleteBestEffort(previousObjectKey);
        }
        return new SubmissionAttachmentView(submission.getId(), questionId, objectKey,
                archive.fileName(), file.getSize(), "application/zip");
    }

    private void inspectArchive(MultipartFile file, String fileName) {
        try (InputStream input = file.getInputStream()) {
            archiveValidator.validate(fileName, file.getContentType(), file.getSize(), input);
        } catch (IOException exception) {
            throw malformed("Could not inspect ZIP attachment");
        }
    }

    private void requireWithinQuota(Long courseId, Long userId, long incomingBytes,
                                    long replacedBytes) {
        long userBytes = answers.sumAttachmentBytesByUserId(userId);
        if (wouldExceed(properties.getStorage().getAttachmentUserQuotaBytes(), userBytes,
                replacedBytes, incomingBytes)) {
            throw malformed("User attachment storage quota would be exceeded");
        }
        long courseBytes = answers.sumAttachmentBytesByCourseId(courseId);
        if (wouldExceed(properties.getStorage().getAttachmentCourseQuotaBytes(), courseBytes,
                replacedBytes, incomingBytes)) {
            throw malformed("Course attachment storage quota would be exceeded");
        }
    }

    private boolean wouldExceed(long quotaBytes, long usedBytes, long replacedBytes,
                                long incomingBytes) {
        if (quotaBytes < 1) return true;
        long replacementCredit = Math.min(Math.max(replacedBytes, 0), Math.max(usedBytes, 0));
        long retainedBytes = Math.max(usedBytes, 0) - replacementCredit;
        return incomingBytes > quotaBytes - Math.min(retainedBytes, quotaBytes);
    }

    private ValidatedArchive validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw malformed("ZIP attachment is empty");
        }
        if (file.getSize() > MAX_ARCHIVE_BYTES) {
            throw malformed("ZIP attachment exceeds the 50 MB limit");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw malformed("Only ZIP attachments are supported");
        }
        String mediaType = file.getContentType();
        if (mediaType == null || !ZIP_MEDIA_TYPES.contains(
                mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))) {
            throw malformed("Attachment MIME type must be ZIP");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(4);
            if (!hasZipSignature(signature)) {
                throw malformed("Attachment content is not a ZIP archive");
            }
        } catch (IOException exception) {
            throw malformed("Could not inspect ZIP attachment");
        }
        return new ValidatedArchive(fileName);
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) throw malformed("ZIP file name is required");
        String normalized = original.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        String safe = baseName.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (safe.length() > 180) safe = safe.substring(safe.length() - 180);
        if (safe.isBlank() || ".zip".equalsIgnoreCase(safe)) throw malformed("Invalid ZIP file name");
        return safe;
    }

    private boolean hasZipSignature(byte[] value) {
        return value.length == 4 && value[0] == 0x50 && value[1] == 0x4b
                && ((value[2] == 0x03 && value[3] == 0x04)
                || (value[2] == 0x05 && value[3] == 0x06)
                || (value[2] == 0x07 && value[3] == 0x08));
    }

    private void registerStorageCleanup(String newObjectKey, String previousObjectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteBestEffort(newObjectKey);
                } else if (previousObjectKey != null && !previousObjectKey.equals(newObjectKey)) {
                    deleteBestEffort(previousObjectKey);
                }
            }
        });
    }

    private void deleteBestEffort(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not remove replaced submission attachment: {}", objectKey, exception);
        }
    }

    private AppException malformed(String message) {
        return new AppException(ErrorCode.VALIDATION_FAILED, message);
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private record ValidatedArchive(String fileName) {
    }
}
