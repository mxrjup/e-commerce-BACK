package com.ecommerce.users;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = UserApplication.class)
@ActiveProfiles("local-h2")
class UserApplicationTests {

    @Test
    void contextLoads() {
    }
}