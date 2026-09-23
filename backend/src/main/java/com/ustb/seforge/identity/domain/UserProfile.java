package com.ustb.seforge.identity.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "student_no", unique = true, length = 64)
    private String studentNo;

    @Column(name = "teacher_no", unique = true, length = 64)
    private String teacherNo;

    @Column(length = 128)
    private String department;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    protected UserProfile() {
    }

    public UserProfile(Long userId, String displayName, AccountType accountType) {
        this.userId = userId;
        this.displayName = displayName;
        this.accountType = accountType;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getTeacherNo() {
        return teacherNo;
    }

    public String getDepartment() {
        return department;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public void setTeacherNo(String teacherNo) {
        this.teacherNo = teacherNo;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
