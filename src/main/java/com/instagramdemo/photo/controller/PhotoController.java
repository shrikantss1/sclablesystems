package com.instagramdemo.photo.controller;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.instagramdemo.photo.dto.PhotoResponse;
import com.instagramdemo.photo.entity.Photo;
import com.instagramdemo.photo.service.PhotoService;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponse> post(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam("timestamp") java.time.Instant timestamp,
            @RequestParam(value = "tags[]", required = false) List<String> tags,
            @RequestParam(value = "caption", required = false) String caption) {
        Photo photo = photoService.upload(file, userId, timestamp, tags, caption);
        PhotoResponse response = PhotoResponse.from(photo, photoService.getImageUrl(photo));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .queryParam("userId", userId)
            .queryParam("photoId", photo.getId())
            .build()
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

        @GetMapping(params = "userId")
    public List<PhotoResponse> list(@RequestParam("userId") Long userId) {
        return photoService.findAllByUserId(userId).stream()
            .map(photo -> PhotoResponse.from(photo, photoService.getImageUrl(photo)))
                .toList();
    }

        @GetMapping(params = {"userId", "photoId"})
    public ResponseEntity<byte[]> view(
            @RequestParam("photoId") Long photoId,
            @RequestParam("userId") Long userId) {
        Photo photo = photoService.findByIdAndUserId(photoId, userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.getFilename() + "\"")
                .body(photoService.getImage(photo));
    }
}