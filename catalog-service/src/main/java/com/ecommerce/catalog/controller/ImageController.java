package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.support.ImageFormat;
import com.ecommerce.catalog.support.ImageStorage;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Serves the uploaded photos (C06) to the storefront, the mobile app and the
 * back-office. Public like the rest of {@code GET /api/catalog/**}.
 *
 * <p>A stored file never changes (a new photo gets a new name), so browsers
 * may keep it for a year without asking again.
 */
@RestController
@RequestMapping("/api/catalog/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorage storage;

    @GetMapping("/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        Path file = storage.find(name).orElseThrow(() -> new ResourceNotFoundException("Image " + name + " not found"));
        String extension = name.substring(name.lastIndexOf('.') + 1);
        MediaType type = ImageFormat.ofExtension(extension).map(format -> MediaType.parseMediaType(format.mediaType()))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
