package com.example.photo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "photos", schema = "sb")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant timestamp;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "photo_tags", schema = "sb", joinColumns = @JoinColumn(name = "photo_id"))
    @Column(name = "tag", nullable = false)
    @OrderColumn(name = "tag_order")
    private List<String> tags = new ArrayList<>();

    private String caption;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Photo() {
    }

    public Photo(
            String filename,
            String contentType,
            Long userId,
            Instant timestamp,
            List<String> tags,
            String caption,
            String s3Key) {
        this.filename = filename;
        this.contentType = contentType;
        this.userId = userId;
        this.timestamp = timestamp;
        this.tags = new ArrayList<>(tags);
        this.caption = caption;
        this.s3Key = s3Key;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getCaption() {
        return caption;
    }

    public String getS3Key() {
        return s3Key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}