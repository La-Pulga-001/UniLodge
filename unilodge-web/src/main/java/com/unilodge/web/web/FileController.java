package com.unilodge.web.web;

import com.unilodge.common.exception.NotFoundException;
import com.unilodge.persistence.entity.PropertyImage;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.PropertyImageRepository;
import com.unilodge.persistence.repository.UserRepository;
import com.unilodge.service.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/** Serves uploaded property photos and account avatars. */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final PropertyImageRepository imageRepository;
    private final UserRepository userRepository;
    private final FileStorageService storageService;

    public FileController(PropertyImageRepository imageRepository,
                          UserRepository userRepository,
                          FileStorageService storageService) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @GetMapping("/property/{imageId}")
    public ResponseEntity<Resource> propertyImage(@PathVariable UUID imageId) {
        PropertyImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("That photo could not be found."));
        Resource resource = storageService.loadPropertyImage(image.getFileName());
        MediaType mediaType = parse(image.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(resource);
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> avatar(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("That account could not be found."));
        if (user.getAvatarFile() == null) {
            throw new NotFoundException("That account has no photo.");
        }
        Resource resource = storageService.loadAvatar(user.getAvatarFile());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(resource);
    }

    private MediaType parse(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
