package com.ecommerce.users;

import com.ecommerce.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SharedErrorHandlingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldRegisterSharedExceptionHandler() {
        assertThat(applicationContext.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
    }
}
