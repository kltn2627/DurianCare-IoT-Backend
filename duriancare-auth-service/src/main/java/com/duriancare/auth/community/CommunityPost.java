package com.duriancare.auth.community;

import com.duriancare.auth.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "community_posts")
public class CommunityPost {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityPostVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityPostStatus status;

    @Column(name = "reaction_count", nullable = false)
    private long reactionCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "share_count", nullable = false)
    private long shareCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommunityMedia> media = new ArrayList<>();

    protected CommunityPost() {
    }

    public CommunityPost(User author, String topic, String content, CommunityPostVisibility visibility) {
        this.author = author;
        this.topic = topic;
        this.content = content;
        this.visibility = visibility;
        this.status = CommunityPostStatus.PUBLISHED;
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

    public User getAuthor() {
        return author;
    }

    public String getTopic() {
        return topic;
    }

    public String getContent() {
        return content;
    }

    public CommunityPostVisibility getVisibility() {
        return visibility;
    }

    public CommunityPostStatus getStatus() {
        return status;
    }

    public void setStatus(CommunityPostStatus status) {
        this.status = status;
    }

    public long getReactionCount() {
        return reactionCount;
    }

    public void setReactionCount(long reactionCount) {
        this.reactionCount = reactionCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getShareCount() {
        return shareCount;
    }

    public void setShareCount(long shareCount) {
        this.shareCount = shareCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<CommunityMedia> getMedia() {
        return media;
    }

    public void addMedia(CommunityMedia item) {
        media.add(item);
        item.setPost(this);
    }
}
