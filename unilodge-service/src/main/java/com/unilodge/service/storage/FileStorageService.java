package com.unilodge.service.storage;

import com.unilodge.common.exception.BadRequestException;
import com.unilodge.common.exception.NotFoundException;
import com.unilodge.service.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded photos on disk with generated, unguessable file names.
 * The database only keeps metadata; files are served through a controller that
 * resolves names safely inside the configured upload directory.
 */
@Service
public class FileStorageService {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    public record StoredFile(String fileName, String originalName, String contentType, long size) {
    }

    private final AppProperties properties;
    private Path root;
    private Path propertyImages;
    private Path avatars;

    public FileStorageService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialise() {
        try {
            root = Paths.get(properties.getUpload().getDir()).toAbsolutePath().normalize();
            propertyImages = Files.createDirectories(root.resolve("property-images"));
            avatars = Files.createDirectories(root.resolve("avatars"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create upload directories", exception);
        }
    }

    public StoredFile storePropertyImage(MultipartFile file) {
        return store(file, propertyImages, properties.getUpload().getMaxImageBytes());
    }

    public StoredFile storeAvatar(MultipartFile file) {
        return store(file, avatars, properties.getUpload().getMaxAvatarBytes());
    }

    /** Used by the demo data seeder to create placeholder listing photos. */
    public String storeSeedImage(byte[] pngBytes) {
        String fileName = UUID.randomUUID() + ".png";
        try {
            Files.write(propertyImages.resolve(fileName), pngBytes);
            return fileName;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write seed image", exception);
        }
    }

    public Resource loadPropertyImage(String fileName) {
        return load(propertyImages, fileName);
    }

    public Resource loadAvatar(String fileName) {
        return load(avatars, fileName);
    }

    public void deletePropertyImage(String fileName) {
        delete(propertyImages, fileName);
    }

    public void deleteAvatar(String fileName) {
        delete(avatars, fileName);
    }

    private StoredFile store(MultipartFile file, Path directory, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a file to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("That file is too large. The limit is "
                    + (maxBytes / (1024 * 1024)) + " MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Only JPEG, PNG or WebP images are allowed.");
        }
        String extension = EXTENSIONS.get(contentType);
        String fileName = UUID.randomUUID() + "." + extension;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store uploaded file", exception);
        }
        return new StoredFile(fileName,
                file.getOriginalFilename() == null ? fileName : file.getOriginalFilename(),
                contentType, file.getSize());
    }

    private Resource load(Path directory, String fileName) {
        Path resolved = directory.resolve(fileName).normalize();
        if (!resolved.startsWith(directory) || !Files.isRegularFile(resolved)) {
            throw new NotFoundException("That image could not be found.");
        }
        return new FileSystemResource(resolved);
    }

    private void delete(Path directory, String fileName) {
        try {
            Files.deleteIfExists(directory.resolve(fileName).normalize());
        } catch (IOException ignored) {
            // A leftover file is harmless; the database row is removed regardless.
        }
    }
}
