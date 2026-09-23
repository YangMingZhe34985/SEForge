package com.ustb.seforge.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ustb.seforge.common.exception.AppException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SecureArchiveValidatorTest {
    private final SecureArchiveValidator validator = new SecureArchiveValidator();

    @Test
    void acceptsSmallSourceArchiveWithoutExtractingIt() throws Exception {
        byte[] archive = zip(Map.of(
                "src/main/App.java", "class App {}".getBytes(StandardCharsets.UTF_8),
                "README.md", "hello".getBytes(StandardCharsets.UTF_8)));

        SecureArchiveValidator.ArchiveSummary summary = validator.validate(
                "submission.zip", "application/zip", archive.length,
                new ByteArrayInputStream(archive));

        assertThat(summary.entryCount()).isEqualTo(2);
        assertThat(summary.uncompressedBytes()).isPositive();
    }

    @Test
    void extractsIntoIsolatedWorkspaceAndDeletesItOnClose() throws Exception {
        byte[] archive = zip(Map.of("src/main/App.java",
                "class App {}".getBytes(StandardCharsets.UTF_8)));
        Path root;

        try (SecureArchiveValidator.ArchiveWorkspace workspace = validator.extract(
                "submission.zip", "application/zip", archive.length,
                new ByteArrayInputStream(archive))) {
            root = workspace.root();
            assertThat(Files.readString(workspace.sourceDirectory()
                    .resolve("src/main/App.java"))).isEqualTo("class App {}");
            assertThat(workspace.summary().entryCount()).isEqualTo(1);
        }

        assertThat(root).doesNotExist();
    }

    @Test
    void rejectsZipSlipPaths() throws Exception {
        byte[] archive = zip(Map.of("../../outside.txt", "bad".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> validator.validate(
                "submission.zip", "application/zip", archive.length,
                new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("unsafe path");
    }

    @Test
    void rejectsNestedArchives() throws Exception {
        byte[] archive = zip(Map.of("nested/source.zip", "not-a-zip".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> validator.validate(
                "submission.zip", null, archive.length, new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Nested archives");
    }

    @Test
    void rejectsStudentSuppliedScannerConfiguration() throws Exception {
        byte[] archive = zip(Map.of(
                "sonar-project.properties", "sonar.host.url=http://attacker".getBytes(
                        StandardCharsets.UTF_8),
                "App.java", "class App {}".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> validator.validate(
                "submission.zip", "application/zip", archive.length,
                new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("reserved path");
    }

    @Test
    void rejectsObjectThatChangedAfterReviewWasQueued() throws Exception {
        byte[] archive = zip(Map.of("App.java", "class App {}".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> validator.extract(
                "submission.zip", "application/zip", archive.length + 1L,
                new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("size changed");
    }

    @Test
    void rejectsNonZipMimeType() throws Exception {
        byte[] archive = zip(Map.of("App.java", "class App {}".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> validator.validate(
                "submission.zip", "text/plain", archive.length,
                new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("media type");
    }

    @Test
    void rejectsSuspiciousCompressionRatio() throws Exception {
        byte[] repeated = new byte[1_000_000];
        java.util.Arrays.fill(repeated, (byte) 'A');
        byte[] archive = zip(Map.of("large.txt", repeated));

        assertThatThrownBy(() -> validator.validate(
                "submission.zip", null, archive.length, new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("compression ratio");
    }

    @Test
    void rejectsTooManyFiles() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index <= SecureArchiveValidator.MAX_ENTRIES; index++) {
                zip.putNextEntry(new ZipEntry("src/File" + index + ".java"));
                zip.write('x');
                zip.closeEntry();
            }
        }
        byte[] archive = output.toByteArray();

        assertThatThrownBy(() -> validator.validate(
                "submission.zip", "application/zip", archive.length,
                new ByteArrayInputStream(archive)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("too many entries");
    }

    private byte[] zip(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
