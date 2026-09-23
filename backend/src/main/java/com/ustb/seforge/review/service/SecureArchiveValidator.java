package com.ustb.seforge.review.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Validates and, only for a worker scan, extracts a bounded source archive into a newly-created
 * temporary directory. Entry permissions are ignored, symbolic links are never created and source
 * files are never executed.
 */
@Component
public class SecureArchiveValidator {
    static final long MAX_COMPRESSED_BYTES = 50L * 1024 * 1024;
    static final long MAX_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;
    static final long MAX_ENTRY_BYTES = 25L * 1024 * 1024;
    static final int MAX_ENTRIES = 2_000;
    static final int MAX_RATIO = 100;
    private static final int MAX_PATH_LENGTH = 4_096;
    private static final int MAX_SEGMENT_LENGTH = 255;
    private static final Set<String> ZIP_MEDIA_TYPES = Set.of(
            "application/zip", "application/x-zip-compressed", "application/octet-stream");
    private static final Set<String> NESTED_ARCHIVES = Set.of(
            "zip", "7z", "rar", "gz", "bz2", "xz", "tar", "tgz");
    private static final Set<String> RESERVED_SCANNER_NAMES = Set.of(
            ".scannerwork", "sonar-project.properties", ".sonarcloud.properties");
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5",
            "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4",
            "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    public ArchiveSummary validate(String fileName, String declaredMediaType, long declaredSize,
                                   InputStream source) {
        byte[] archive = readArchive(fileName, declaredMediaType, declaredSize, source);
        try {
            return inspect(archive, null);
        } catch (IOException exception) {
            throw invalid("Archive could not be inspected");
        }
    }

    /**
     * Creates an isolated source workspace. Callers must close it; closing recursively removes all
     * extracted material even when scanning fails.
     */
    public ArchiveWorkspace extract(String fileName, String declaredMediaType, long declaredSize,
                                    InputStream source) throws IOException {
        byte[] archive = readArchive(fileName, declaredMediaType, declaredSize, source);
        Path workspaceRoot = Files.createTempDirectory("seforge-sonar-").toAbsolutePath().normalize();
        try {
            Path sourceRoot = Files.createDirectory(workspaceRoot.resolve("source"));
            ArchiveSummary summary = inspect(archive, sourceRoot);
            return new ArchiveWorkspace(workspaceRoot, sourceRoot, summary);
        } catch (IOException | RuntimeException failure) {
            try {
                deleteRecursively(workspaceRoot);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private byte[] readArchive(String fileName, String declaredMediaType, long declaredSize,
                               InputStream source) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw invalid("Code submission must be a ZIP archive");
        }
        String mediaType = declaredMediaType == null ? null
                : declaredMediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mediaType != null && !mediaType.isBlank() && !ZIP_MEDIA_TYPES.contains(mediaType)) {
            throw invalid("Unsupported archive media type");
        }
        if (declaredSize < 0 || declaredSize > MAX_COMPRESSED_BYTES) {
            throw invalid("Archive exceeds the 50 MB compressed-size limit");
        }
        if (source == null) throw invalid("Archive content is required");
        try {
            byte[] archive = source.readNBytes((int) MAX_COMPRESSED_BYTES + 1);
            if (archive.length > MAX_COMPRESSED_BYTES) {
                throw invalid("Archive exceeds the 50 MB compressed-size limit");
            }
            if (declaredSize > 0 && declaredSize != archive.length) {
                throw invalid("Archive size changed after it was submitted");
            }
            if (!hasZipSignature(archive)) throw invalid("Archive signature is not ZIP");
            return archive;
        } catch (IOException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Archive could not be inspected");
        }
    }

    private ArchiveSummary inspect(byte[] archive, Path extractionRoot) throws IOException {
        int entries = 0;
        int files = 0;
        long uncompressed = 0;
        byte[] buffer = new byte[8192];
        Path validationRoot = extractionRoot == null
                ? Path.of("archive-validation-root").toAbsolutePath().normalize() : extractionRoot;
        Set<String> normalizedEntries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++entries > MAX_ENTRIES) throw invalid("Archive contains too many entries");
                Path target = safeTarget(entry.getName(), validationRoot);
                String normalizedName = validationRoot.relativize(target).toString()
                        .replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!normalizedEntries.add(normalizedName)) {
                    throw invalid("Archive contains duplicate paths");
                }
                if (entry.isDirectory()) {
                    if (extractionRoot != null) Files.createDirectories(target);
                    continue;
                }
                files++;
                if (NESTED_ARCHIVES.contains(extension(normalizedName))) {
                    throw invalid("Nested archives are not accepted");
                }
                if (extractionRoot != null) {
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                }
                long entryBytes = 0;
                OutputStream output = extractionRoot == null ? OutputStream.nullOutputStream()
                        : newOutput(target);
                try (output) {
                    for (int read; (read = zip.read(buffer)) != -1; ) {
                        if (read == 0) continue;
                        entryBytes += read;
                        uncompressed += read;
                        if (entryBytes > MAX_ENTRY_BYTES) {
                            throw invalid("An archive entry is too large");
                        }
                        if (uncompressed > MAX_UNCOMPRESSED_BYTES) {
                            throw invalid("Archive exceeds the 200 MB expanded-size limit");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
        if (files == 0) throw invalid("Archive is empty");
        long compressed = Math.max(archive.length, 1);
        if (uncompressed > compressed * MAX_RATIO) {
            throw invalid("Archive compression ratio is unsafe");
        }
        return new ArchiveSummary(files, archive.length, uncompressed);
    }

    private OutputStream newOutput(Path target) throws IOException {
        try {
            return Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exception) {
            throw invalid("Archive contains conflicting paths");
        }
    }

    private Path safeTarget(String rawName, Path extractionRoot) {
        if (rawName == null || rawName.isBlank()) throw invalid("Archive contains an unnamed entry");
        String name = rawName.replace('\\', '/');
        while (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        if (name.isBlank() || name.length() > MAX_PATH_LENGTH || name.startsWith("/")
                || name.matches("^[A-Za-z]:.*") || name.indexOf('\0') >= 0) {
            throw invalid("Archive contains an unsafe path");
        }
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            String lower = segment.toLowerCase(Locale.ROOT);
            String deviceStem = lower.contains(".") ? lower.substring(0, lower.indexOf('.')) : lower;
            if (RESERVED_SCANNER_NAMES.contains(lower)) {
                throw invalid("Archive contains a reserved path");
            }
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || segment.length() > MAX_SEGMENT_LENGTH || segment.contains(":")
                    || segment.endsWith(".") || segment.endsWith(" ")
                    || WINDOWS_DEVICE_NAMES.contains(deviceStem)) {
                throw invalid("Archive contains an unsafe path");
            }
        }
        try {
            Path resolved = extractionRoot.resolve(name).normalize();
            if (!resolved.startsWith(extractionRoot)) throw invalid("Archive contains an unsafe path");
            return resolved;
        } catch (InvalidPathException exception) {
            throw invalid("Archive contains an unsafe path");
        }
    }

    private boolean hasZipSignature(byte[] value) {
        return value.length >= 4 && value[0] == 0x50 && value[1] == 0x4b
                && ((value[2] == 0x03 && value[3] == 0x04)
                || (value[2] == 0x05 && value[3] == 0x06)
                || (value[2] == 0x07 && value[3] == 0x08));
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private AppException invalid(String message) {
        return new AppException(ErrorCode.MALFORMED_REQUEST, message);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record ArchiveSummary(int entryCount, long compressedBytes, long uncompressedBytes) {
    }

    public static final class ArchiveWorkspace implements AutoCloseable {
        private final Path root;
        private final Path sourceDirectory;
        private final ArchiveSummary summary;

        private ArchiveWorkspace(Path root, Path sourceDirectory, ArchiveSummary summary) {
            this.root = root;
            this.sourceDirectory = sourceDirectory;
            this.summary = summary;
        }

        public Path root() { return root; }
        public Path sourceDirectory() { return sourceDirectory; }
        public ArchiveSummary summary() { return summary; }

        @Override
        public void close() throws IOException {
            deleteRecursively(root);
        }
    }
}
