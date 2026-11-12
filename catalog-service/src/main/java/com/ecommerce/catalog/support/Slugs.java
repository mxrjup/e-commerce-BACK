package com.ecommerce.catalog.support;

import com.ecommerce.common.exception.BadRequestException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The URL identifier of a catalogue entry: {@code "Écrans & Moniteurs"}
 * becomes {@code "ecrans-moniteurs"}.
 *
 * <p>A slug appears in public URLs, so it never changes behind the
 * back-office's back: derived from the name at creation when none is sent,
 * then kept as is until a request sends a new one explicitly.
 */
public final class Slugs {

    /** What a slug looks like, whether generated or sent by the back-office. */
    public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** Same limit as the {@code slug} columns. */
    public static final int MAX_LENGTH = 120;

    private Slugs() {
    }

    public static String from(String name) {
        // Les ligatures ne se decomposent pas en NFD : « Cœur » donnerait « c-ur ».
        String unligatured = name.replace("œ", "oe").replace("Œ", "OE")
                .replace("æ", "ae").replace("Æ", "AE");
        String withoutAccents = Normalizer.normalize(unligatured, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    /** For a new entry: the slug the client asked for, or one derived from the name. */
    public static String forCreation(String requested, String name) {
        return isGiven(requested) ? requested : derive(name);
    }

    /** For an existing entry: the slug the client asked for, or the current one. */
    public static String forUpdate(String requested, String current) {
        return isGiven(requested) ? requested : current;
    }

    /**
     * A name with no Latin letter or digit gives an empty slug, and a long name
     * full of ligatures one longer than the column: both would make an entry
     * with no usable URL, so the client has to choose the slug itself.
     */
    private static String derive(String name) {
        String slug = from(name);
        if (slug.isEmpty()) {
            throw new BadRequestException(
                    "No slug can be derived from the name '" + name + "': send one explicitly");
        }
        if (slug.length() > MAX_LENGTH) {
            throw new BadRequestException("The slug derived from the name is longer than "
                    + MAX_LENGTH + " characters: send one explicitly");
        }
        return slug;
    }

    private static boolean isGiven(String slug) {
        return slug != null && !slug.isBlank();
    }
}
