package com.example.photo.dto;

import com.example.photo.entity.Photo;
import java.time.Instant;
import java.util.List;

public record PhotoResponse(
        Long id,
        String filename,
        String contentType,
        String s3Url,
        Long userId,
        Instant timestamp,
        List<String> tags,
        String caption,
        Instant createdAt) {

    public static PhotoResponse from(Photo photo, String s3Url) {
        return new PhotoResponse(
                photo.getId(),
                photo.getFilename(),
                photo.getContentType(),
                s3Url,
                photo.getUserId(),
                photo.getTimestamp(),
                List.copyOf(photo.getTags()),
                photo.getCaption(),
                photo.getCreatedAt());
    }
}