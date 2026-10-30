package com.library.controller;

import com.library.model.StoredImage;
import com.library.repository.ImageRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Sends a stored picture to the browser. The id is a long random value, so it is only known to those who see the page. */
@RestController
public class ImageController {
    private final ImageRepository images;

    public ImageController(ImageRepository images) {
        this.images = images;
    }

    @GetMapping("/api/images/{id}")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        StoredImage img = images.findById(id).orElse(null);
        if (img == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(img.data);
    }
}
