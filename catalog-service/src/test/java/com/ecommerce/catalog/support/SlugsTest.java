package com.ecommerce.catalog.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void shouldDropAccentsPunctuationAndCase() {
        assertThat(Slugs.from("Écrans & Moniteurs")).isEqualTo("ecrans-moniteurs");
        assertThat(Slugs.from("  Souris -- gaming!  ")).isEqualTo("souris-gaming");
        assertThat(Slugs.from("Cœur 4K")).isEqualTo("coeur-4k");
    }

    @Test
    void shouldProduceSlugsMatchingTheValidationPattern() {
        assertThat(Slugs.from("Télétravail & Bureau")).matches(Slugs.PATTERN);
    }

    @Test
    void shouldPreferTheRequestedSlug() {
        assertThat(Slugs.resolve("mon-slug", "Autre nom")).isEqualTo("mon-slug");
        assertThat(Slugs.resolve(null, "Autre nom")).isEqualTo("autre-nom");
        assertThat(Slugs.resolve(" ", "Autre nom")).isEqualTo("autre-nom");
    }
}
