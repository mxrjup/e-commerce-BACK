package com.ecommerce.catalog.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The image files of the variants (C06), in one directory: a Docker volume
 * locally, a PVC in Kubernetes. The database keeps their public URL in
 * {@code variante_image}.
 *
 * <p>A file name is always made here, from a random UUID and the detected
 * format, never taken from the upload: a name like {@code ../../etc/passwd}
 * cannot reach the disk, and reading only accepts names of that shape.
 *
 * <p>Files follow the transaction of their row: removed once a deletion is
 * committed, never before, and removed again if the row that pointed to a
 * new file is rolled back.
 */
@Slf4j
@Component
public class ImageStorage {

    private static final Pattern NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png|webp)$");

    private final Path directory;
    private final String publicUrl;

    public ImageStorage(@Value("${catalog.images.directory}") String directory,
                        @Value("${catalog.images.public-url}") String publicUrl) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        try {
            Files.createDirectories(this.directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create the image directory " + this.directory, ex);
        }
    }

    /** Writes the file under a new name, through a temporary file so a reader never sees half an image. */
    public String store(byte[] content, ImageFormat format) {
        String name = UUID.randomUUID().toString().replace("-", "") + "." + format.extension();
        Path target = directory.resolve(name);
        try {
            Path temporary = Files.createTempFile(directory, "upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot store image " + name, ex);
        }
        deleteIfRolledBack(name);
        return name;
    }

    /** The URL the database keeps and the clients load, through the gateway. */
    public String urlOf(String name) {
        return publicUrl + "/" + name;
    }

    /** The file behind a URL, if it is one of ours: an image hosted elsewhere is left alone. */
    public Optional<String> nameOf(String url) {
        String prefix = publicUrl + "/";
        if (url == null || !url.startsWith(prefix)) {
            return Optional.empty();
        }
        String name = url.substring(prefix.length());
        return NAME.matcher(name).matches() ? Optional.of(name) : Optional.empty();
    }

    /** A stored file, or nothing for a name of any other shape or a file that does not exist. */
    public Optional<Path> find(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path file = directory.resolve(name);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Removes the files behind these URLs once the current transaction is committed. */
    public void deleteAfterCommit(Collection<String> urls) {
        List<String> names = urls.stream().map(this::nameOf).flatMap(Optional::stream).toList();
        if (names.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            names.forEach(this::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                names.forEach(ImageStorage.this::delete);
            }
        });
    }

    private void deleteIfRolledBack(String name) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    delete(name);
                }
            }
        });
    }

    /** A file that cannot be removed is only logged: the database is already right, a stray file costs disk space only. */
    private void delete(String name) {
        try {
            Files.deleteIfExists(directory.resolve(name));
        } catch (IOException ex) {
            log.warn("Could not delete image file {}: {}", name, ex.getMessage());
        }
    }
}
