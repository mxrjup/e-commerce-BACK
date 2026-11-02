package com.ecommerce.cart.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the transaction mode of the service.
 *
 * <p>A functional test cannot catch a read-only transaction here: the service
 * is tested on H2, which quietly ignores the flag, while PostgreSQL rejects the
 * INSERT and the endpoint answers 500. Reading the declared attribute catches it
 * whatever the database.
 *
 * <p>Every method of this service goes through {@code getOrCreateCart}, which
 * inserts a cart when the visitor does not have one, so none of them may be
 * read-only. A method that one day only reads would make this test fail on
 * purpose: that is the moment to narrow it rather than to relax it.
 */
class CartServiceTransactionTest {

    private final AnnotationTransactionAttributeSource attributeSource = new AnnotationTransactionAttributeSource();

    @Test
    void shouldNotDeclareAnyReadOnlyTransaction() {
        for (Method method : CartService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            TransactionAttribute attribute = attributeSource.getTransactionAttribute(method, CartService.class);
            if (attribute == null) {
                continue;
            }
            assertThat(attribute.isReadOnly())
                    .as("%s() reaches getOrCreateCart, which inserts a cart when the visitor has none: "
                            + "a read-only transaction makes PostgreSQL reject that insert", method.getName())
                    .isFalse();
        }
    }
}
