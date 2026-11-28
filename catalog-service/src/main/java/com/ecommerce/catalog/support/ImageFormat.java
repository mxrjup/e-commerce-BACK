package com.ecommerce.catalog.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats the catalogue accepts (C06), recognised from the first
 * bytes of the file: neither the file name nor the type the browser
 * announces can be trusted, an executable renamed {@code .jpg} would pass.
 */
public enum ImageFormat {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MARK = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final String extension;
    private final String mediaType;

    ImageFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_START)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_START)) {
            return Optional.of(PNG);
        }
        // RIFF, la taille sur 4 octets, puis WEBP.
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARK)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /** The format of a stored file, from the extension the catalogue gave it. */
    public static Optional<ImageFormat> ofExtension(String extension) {
        return Arrays.stream(values()).filter(format -> format.extension.equals(extension)).findFirst();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
