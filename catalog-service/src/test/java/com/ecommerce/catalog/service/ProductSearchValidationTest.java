package com.ecommerce.catalog.service;

import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.ProductSearchRepository;
import com.ecommerce.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductSearchValidationTest {
    private final ProductSearchRepository search = mock(ProductSearchRepository.class);
    private final ProductCatalogService service = new ProductCatalogService(search,
            mock(ProductRepository.class), mock(CategoryRepository.class));

    @Test void emptyAutocompleteDoesNotAccessPostgres() {
        assertThat(service.autocomplete(null, 8)).isEmpty();
        assertThat(service.autocomplete("  ", 8)).isEmpty();
        verifyNoInteractions(search);
    }

    @Test void rejectsExcessiveFiltersBeforeQuerying() {
        assertThatThrownBy(() -> service.facets(null, null, null, null, null, null,
                Collections.nCopies(21, "Couleur:Noir"), List.of()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(search);
    }

    @Test void boundsAutocompleteAndNormalizesQuery() {
        when(search.autocomplete("logitec", 3)).thenReturn(List.of());
        assertThat(service.autocomplete(" logitec ", 3)).isEmpty();
        verify(search).autocomplete("logitec", 3);
        assertThatThrownBy(() -> service.autocomplete("logitec", 21))
                .isInstanceOf(BadRequestException.class);
        verifyNoMoreInteractions(search);
    }
}
