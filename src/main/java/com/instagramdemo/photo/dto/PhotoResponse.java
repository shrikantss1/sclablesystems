package com.instagramdemo.photo.dto;

import java.time.Instant;
import java.util.List;

import com.instagramdemo.photo.entity.Photo;

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