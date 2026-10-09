package com.ecommerce.order;

import com.ecommerce.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SharedErrorHandlingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterSharedExceptionHandler() {
        assertThat(applicationContext.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
    }
}
