package com.ecommerce.cart;

import com.ecommerce.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared error handling comes from common-lib, outside the component scan
 * of this service: it is wired by auto-configuration only. This test fails if
 * that wiring is ever lost, which would silently turn every handled error into
 * a raw stack trace.
 */
@SpringBootTest
class SharedErrorHandlingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterSharedExceptionHandler() {
        assertThat(applicationContext.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
    }
}
