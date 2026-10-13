package com.example.photo.dto;

import com.example.photo.entity.Photo;
import java.time.Instant;
import java.util.List;

public record PhotoResponse(
        Long id,
        String filename,
        String contentType,
        Long userId,
        Instant timestamp,
        List<String> tags,
        String caption,
        Instant createdAt) {

    public static PhotoResponse from(Photo photo) {
        return new PhotoResponse(
                photo.getId(),
                photo.getFilename(),
                photo.getContentType(),
                photo.getUserId(),
                photo.getTimestamp(),
                List.copyOf(photo.getTags()),
                photo.getCaption(),
                photo.getCreatedAt());
    }
}