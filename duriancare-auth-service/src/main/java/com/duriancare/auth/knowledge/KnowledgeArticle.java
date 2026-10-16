package com.duriancare.auth.knowledge;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "knowledge_articles")
public class KnowledgeArticle {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(nullable = false, unique = true, length = 260)
    private String slug;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", length = 20)
    private UserRole authorRole;

    @Column(nullable = false, length = 600)
    private String excerpt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeArticleStatus status;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(name = "cover_image_content_type", length = 120)
    private String coverImageContentType;

    @Column(name = "reading_time", nullable = false, length = 40)
    private String readingTime;

    @Column(length = 500)
    private String tags;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 600)
    private String rejectionReason;

    @Column(nullable = false)
    private long views;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeArticle() {
    }

    public KnowledgeArticle(String title, String slug, String category, String authorName, String excerpt, String content) {
        this.title = title;
        this.slug = slug;
        this.category = category;
        this.authorName = authorName;
        this.excerpt = excerpt;
        this.content = content;
        this.status = KnowledgeArticleStatus.REVIEW;
        this.readingTime = "1 phút đọc";
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public UserRole getAuthorRole() {
        return authorRole;
    }

    public void setAuthorRole(UserRole authorRole) {
        this.authorRole = authorRole;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public KnowledgeArticleStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeArticleStatus status) {
        this.status = status;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getCoverImageKey() {
        return coverImageKey;
    }

    public String getCoverImageContentType() {
        return coverImageContentType;
    }

    public void setCoverImage(String url, String key, String contentType) {
        this.coverImageUrl = url;
        this.coverImageKey = key;
        this.coverImageContentType = contentType;
    }

    public String getReadingTime() {
        return readingTime;
    }

    public void setReadingTime(String readingTime) {
        this.readingTime = readingTime;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public long getViews() {
        return views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

