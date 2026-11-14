package com.unilodge.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A bookmarked student post. */
@Entity
@Table(name = "saved_student_posts")
public class SavedStudentPost extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private StudentPost post;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public StudentPost getPost() {
        return post;
    }

    public void setPost(StudentPost post) {
        this.post = post;
    }
}
