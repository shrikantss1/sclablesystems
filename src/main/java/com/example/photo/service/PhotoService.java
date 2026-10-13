package com.example.photo.service;

import com.example.photo.entity.Photo;
import com.example.photo.repository.PhotoRepository;
import com.example.photo.storage.S3PhotoStorageService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final S3PhotoStorageService s3PhotoStorageService;

    public PhotoService(PhotoRepository photoRepository, S3PhotoStorageService s3PhotoStorageService) {
        this.photoRepository = photoRepository;
        this.s3PhotoStorageService = s3PhotoStorageService;
    }

    public Photo upload(MultipartFile file, Long userId, Instant timestamp, List<String> tags, String caption) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo file must not be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file must be an image");
        }

        byte[] imageData;
        try {
            imageData = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded photo", exception);
        }

        String s3Key = s3PhotoStorageService.newPhotoKey(userId);
        s3PhotoStorageService.store(s3Key, contentType, imageData);
        try {
            return photoRepository.save(new Photo(
                    file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename(),
                    contentType,
                    userId,
                    timestamp,
                    tags == null ? List.of() : new ArrayList<>(tags),
                    caption,
                    s3Key));
        } catch (RuntimeException exception) {
            try {
                s3PhotoStorageService.delete(s3Key);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public List<Photo> findAllByUserId(Long userId) {
        return photoRepository.findAllByUserIdOrderByTimestampDesc(userId);
    }

    public Photo findByIdAndUserId(Long id, Long userId) {
        return photoRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
    }

    public byte[] getImage(Photo photo) {
        if (photo.getS3Key() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo image is not stored in S3");
        }
        return s3PhotoStorageService.read(photo.getS3Key());
    }

    public String getImageUrl(Photo photo) {
        if (photo.getS3Key() == null) {
            return null;
        }
        return s3PhotoStorageService.createDownloadUrl(photo.getS3Key());
    }
}