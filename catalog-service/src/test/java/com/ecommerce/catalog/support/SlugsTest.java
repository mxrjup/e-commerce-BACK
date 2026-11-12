package com.ecommerce.catalog.support;

import org.junit.jupiter.api.Test;
import com.ecommerce.common.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldDeriveTheSlugOfANewEntryUnlessOneIsRequested() {
        assertThat(Slugs.forCreation("mon-slug", "Autre nom")).isEqualTo("mon-slug");
        assertThat(Slugs.forCreation(null, "Autre nom")).isEqualTo("autre-nom");
        assertThat(Slugs.forCreation(" ", "Autre nom")).isEqualTo("autre-nom");
    }

    @Test
    void shouldKeepTheSlugOfAnExistingEntryUnlessOneIsRequested() {
        assertThat(Slugs.forUpdate(null, "accessoires-audio")).isEqualTo("accessoires-audio");
        assertThat(Slugs.forUpdate("", "accessoires-audio")).isEqualTo("accessoires-audio");
        assertThat(Slugs.forUpdate("audio", "accessoires-audio")).isEqualTo("audio");
    }

    @Test
    void shouldRefuseToDeriveAnEmptySlug() {
        assertThatThrownBy(() -> Slugs.forCreation(null, "日本語"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("send one explicitly");
        assertThatThrownBy(() -> Slugs.forCreation(null, "+++"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRefuseToDeriveASlugLongerThanTheColumn() {
        assertThatThrownBy(() -> Slugs.forCreation(null, "œ".repeat(100)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("longer than 120 characters");
        assertThat(Slugs.forCreation(null, "a".repeat(100))).hasSize(100);
    }
}
