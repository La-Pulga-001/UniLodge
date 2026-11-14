package com.unilodge.persistence.entity;

import com.unilodge.common.enums.UserRole;
import com.unilodge.common.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A student, landlord or administrator account. */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 80)
    private String fullName;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;

    @Column(name = "business_name", length = 120)
    private String businessName;

    @Column(name = "avatar_file", length = 200)
    private String avatarFile;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "admin_verified_at")
    private Instant adminVerifiedAt;

    @Column(name = "original_admin", nullable = false)
    private boolean originalAdmin;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public University getUniversity() {
        return university;
    }

    public void setUniversity(University university) {
        this.university = university;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getAvatarFile() {
        return avatarFile;
    }

    public void setAvatarFile(String avatarFile) {
        this.avatarFile = avatarFile;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getAdminVerifiedAt() {
        return adminVerifiedAt;
    }

    public void setAdminVerifiedAt(Instant adminVerifiedAt) {
        this.adminVerifiedAt = adminVerifiedAt;
    }

    public boolean isOriginalAdmin() {
        return originalAdmin;
    }

    public void setOriginalAdmin(boolean originalAdmin) {
        this.originalAdmin = originalAdmin;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    /** True when this administrator has completed email verification. */
    public boolean isAdminVerified() {
        return adminVerifiedAt != null;
    }
}
