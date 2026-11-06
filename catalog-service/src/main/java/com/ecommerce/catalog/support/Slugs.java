package com.ecommerce.catalog.support;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds the URL identifier of a catalogue entry from its name:
 * {@code "Écrans & Moniteurs"} becomes {@code "ecrans-moniteurs"}.
 */
public final class Slugs {

    /** What a slug looks like, whether generated or sent by the back-office. */
    public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

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

    /** The slug the client asked for, or one derived from the name. */
    public static String resolve(String requested, String name) {
        return requested != null && !requested.isBlank() ? requested : from(name);
    }
}
