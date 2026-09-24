package com.ustb.seforge.identity.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.api.UserImportPreviewView;
import com.ustb.seforge.identity.api.UserImportResultView;
import com.ustb.seforge.identity.api.UserImportRowView;
import com.ustb.seforge.identity.api.UserView;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Bounded, two-step import. Preview is read-only; confirm validates the same bytes again. */
@Service
public class UserImportService {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final List<String> HEADER = List.of("accountType", "studentNo", "username", "email", "displayName");
    private final IdentityService identity;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final SecureRandom random = new SecureRandom();

    public UserImportService(IdentityService identity, UserRepository users, UserProfileRepository profiles) {
        this.identity = identity;
        this.users = users;
        this.profiles = profiles;
    }

    public UserImportPreviewView preview(byte[] csv) {
        List<UserImportRowView> rows = inspect(csv);
        int valid = (int) rows.stream().filter(row -> row.status().equals("VALID")).count();
        return new UserImportPreviewView(digest(csv), rows.size(), valid, rows.size() - valid, rows);
    }

    public UserImportResultView confirm(byte[] csv, String expectedDigest) {
        if (expectedDigest == null || !MessageDigest.isEqual(
                digest(csv).getBytes(StandardCharsets.US_ASCII), expectedDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw new AppException(ErrorCode.CONFLICT, "CSV has changed since preview");
        }
        List<UserImportRowView> checked = inspect(csv);
        List<UserImportRowView> results = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (UserImportRowView row : checked) {
            if (!row.status().equals("VALID")) {
                results.add(new UserImportRowView(row.line(), row.accountType(), row.studentNo(), row.username(),
                        row.email(), row.displayName(), "SKIPPED", row.message(), null, null));
                skipped++;
                continue;
            }
            byte[] secret = new byte[24];
            random.nextBytes(secret);
            String initialPassword = "S" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            try {
                UserView user = identity.createUser(new CreateUserRequest(row.email(), row.username(), initialPassword,
                        row.displayName(), AccountType.valueOf(row.accountType()), Set.of(GlobalRole.USER), row.studentNo()));
                results.add(new UserImportRowView(row.line(), row.accountType(), row.studentNo(), row.username(),
                        row.email(), row.displayName(), "CREATED", "Created", user.id(), initialPassword));
                created++;
            } catch (AppException | DataIntegrityViolationException exception) {
                results.add(new UserImportRowView(row.line(), row.accountType(), row.studentNo(), row.username(),
                        row.email(), row.displayName(), "FAILED", "Account conflicts with an existing record", null, null));
                failed++;
            }
        }
        return new UserImportResultView(results.size(), created, skipped, failed, results);
    }

    private List<UserImportRowView> inspect(byte[] csv) {
        if (csv == null || csv.length == 0 || csv.length > MAX_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "CSV must be between 1 byte and 1 MB");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(csv)).toString();
        } catch (CharacterCodingException exception) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "CSV must use UTF-8");
        }
        List<List<String>> records = parseCsv(content.replaceFirst("^\uFEFF", ""));
        if (records.isEmpty() || !records.getFirst().equals(HEADER)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "CSV header must be accountType,studentNo,username,email,displayName");
        }
        if (records.size() < 2 || records.size() - 1 > MAX_ROWS) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "CSV must contain 1 to 1000 accounts");
        }
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenStudentNos = new HashSet<>();
        List<UserImportRowView> result = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> fields = records.get(i);
            String type = field(fields, 0).toUpperCase(Locale.ROOT);
            String studentNo = field(fields, 1).toUpperCase(Locale.ROOT);
            String username = field(fields, 2).toLowerCase(Locale.ROOT);
            String email = field(fields, 3).toLowerCase(Locale.ROOT);
            String displayName = field(fields, 4);
            String error = validate(fields, type, studentNo, username, email, displayName,
                    seenUsernames, seenEmails, seenStudentNos);
            result.add(new UserImportRowView(i + 1, type, studentNo, username, email, displayName,
                    error == null ? "VALID" : "REJECTED", error == null ? "Ready" : error, null, null));
        }
        return result;
    }

    private String validate(List<String> fields, String type, String studentNo, String username, String email,
                            String displayName, Set<String> usernames, Set<String> emails, Set<String> studentNos) {
        if (fields.size() != HEADER.size()) return "Expected five columns";
        if (!type.equals("TEACHER") && !type.equals("STUDENT")) return "Account type must be TEACHER or STUDENT";
        if (!username.matches("[a-z0-9_.-]{3,64}")) return "Invalid username";
        if (email.length() > 190 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) return "Invalid email";
        if (displayName.isBlank() || displayName.length() > 100) return "Invalid display name";
        if (type.equals("STUDENT") && !studentNo.matches("[A-Z0-9_-]{3,64}")) return "Student number required";
        if (type.equals("TEACHER") && !studentNo.isEmpty()) return "Teacher must not have a student number";
        if (!usernames.add(username)) return "Duplicate username in CSV";
        if (!emails.add(email)) return "Duplicate email in CSV";
        if (!studentNo.isEmpty() && !studentNos.add(studentNo)) return "Duplicate student number in CSV";
        if (users.existsByUsernameIgnoreCase(username)) return "Username already exists";
        if (users.existsByEmailIgnoreCase(email)) return "Email already exists";
        if (!studentNo.isEmpty() && profiles.existsByStudentNoIgnoreCase(studentNo)) return "Student number already exists";
        return null;
    }

    private String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index).trim() : "";
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                    closedQuote = true;
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && field.isEmpty() && !closedQuote) {
                quoted = true;
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                row.add(field.toString());
                field.setLength(0);
                closedQuote = false;
                if (ch != ',') {
                    if (!row.stream().allMatch(String::isBlank)) rows.add(row);
                    row = new ArrayList<>();
                    if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                }
            } else if (closedQuote || ch == '"') {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Malformed CSV quoting");
            } else {
                field.append(ch);
            }
        }
        if (quoted) throw new AppException(ErrorCode.VALIDATION_FAILED, "Unclosed CSV quote");
        if (!row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString());
            if (!row.stream().allMatch(String::isBlank)) rows.add(row);
        }
        return rows;
    }

    private String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
