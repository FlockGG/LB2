package com.library.controller;

import com.library.model.StoredImage;
import com.library.repository.ImageRepository;
import com.library.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Saves uploaded images (book covers, profile photos) in the database, so they survive restarts and redeploys. */
@RestController
public class UploadController {
    private final AuthService auth;
    private final ImageRepository images;

    public UploadController(AuthService auth, ImageRepository images) {
        this.auth = auth;
        this.images = images;
    }

    @PostMapping("/api/upload")
    public Map<String, String> upload(@RequestHeader(value = "X-Token", required = false) String token,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        auth.require(token);
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!type.equals("image/png") && !type.equals("image/jpeg") && !type.equals("image/gif") && !type.equals("image/webp"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a PNG, JPG, GIF or WebP image.");
        StoredImage img = new StoredImage();
        img.id = UUID.randomUUID().toString();
        img.contentType = type;
        img.data = file.getBytes();
        img.createdAt = System.currentTimeMillis();
        images.save(img);
        return Map.of("url", "/api/images/" + img.id);
    }
}
