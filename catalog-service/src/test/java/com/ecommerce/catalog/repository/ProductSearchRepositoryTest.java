package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductSearchRepositoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void skipsProductsDeletedBetweenIdSelectionAndEntityLoading() {
        EntityManager em = mock(EntityManager.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TypedQuery<Product> query = mock(TypedQuery.class);
        Product remaining = mock(Product.class);
        when(remaining.getId()).thenReturn(2L);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of(1L, 2L));
        when(em.createQuery(anyString(), eq(Product.class))).thenReturn(query);
        when(query.setParameter("ids", List.of(1L, 2L))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(remaining));

        assertThat(new ProductSearchRepository(em, jdbc).page(
                new ProductSearchRepository.Criteria(null, null, null),
                ProductSearchRepository.Sort.NEWEST, 0, 20)).containsExactly(remaining);
    }
}
