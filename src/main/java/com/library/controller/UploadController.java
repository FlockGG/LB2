package com.library.controller;

import com.library.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

/** Saves uploaded images (book covers, profile photos) into the uploads folder. */
@RestController
public class UploadController {
    private final AuthService auth;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public UploadController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/upload")
    public Map<String, String> upload(@RequestHeader(value = "X-Token", required = false) String token,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        auth.require(token);
        String type = file.getContentType() == null ? "" : file.getContentType();
        String ext = switch (type) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> null;
        };
        if (ext == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a PNG, JPG, GIF or WebP image.");
        Path dir = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(name));
        }
        return Map.of("url", "/uploads/" + name);
    }
}
