package com.duriancare.auth.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "community_post_media")
public class CommunityMedia {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private CommunityMediaType mediaType;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CommunityMedia() {
    }

    public CommunityMedia(UUID id, CommunityMediaType mediaType, String url, String objectKey, String contentType, int sortOrder) {
        this.id = id;
        this.mediaType = mediaType;
        this.url = url;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public void setPost(CommunityPost post) {
        this.post = post;
    }

    public CommunityMediaType getMediaType() {
        return mediaType;
    }

    public String getUrl() {
        return url;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
