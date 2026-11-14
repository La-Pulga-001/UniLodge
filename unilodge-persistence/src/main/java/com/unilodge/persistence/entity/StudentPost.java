package com.unilodge.persistence.entity;

import com.unilodge.common.enums.ModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A student's roommate or housing request thread. */
@Entity
@Table(name = "student_posts")
public class StudentPost extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "preferred_location", length = 160)
    private String preferredLocation;

    @Column(name = "budget_max", precision = 12, scale = 2)
    private BigDecimal budgetMax;

    @Column(name = "roommates_needed")
    private Integer roommatesNeeded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationStatus status;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public University getUniversity() {
        return university;
    }

    public void setUniversity(University university) {
        this.university = university;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getPreferredLocation() {
        return preferredLocation;
    }

    public void setPreferredLocation(String preferredLocation) {
        this.preferredLocation = preferredLocation;
    }

    public BigDecimal getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(BigDecimal budgetMax) {
        this.budgetMax = budgetMax;
    }

    public Integer getRoommatesNeeded() {
        return roommatesNeeded;
    }

    public void setRoommatesNeeded(Integer roommatesNeeded) {
        this.roommatesNeeded = roommatesNeeded;
    }

    public ModerationStatus getStatus() {
        return status;
    }

    public void setStatus(ModerationStatus status) {
        this.status = status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }
}
