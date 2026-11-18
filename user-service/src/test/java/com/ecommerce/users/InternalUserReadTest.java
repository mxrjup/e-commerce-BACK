package com.ecommerce.users;

import com.ecommerce.users.entity.User;
import com.ecommerce.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "mcp.internal-read-key=test-read-key")
@AutoConfigureMockMvc
class InternalUserReadTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository repository;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void readsExistingUsersWithoutProvisioningOrExposingInternalFields() throws Exception {
        User user = repository.save(User.builder().authProviderId("mcp-read-test")
                .email("read@test").firstName("Read").lastName("Only").build());
        long count = repository.count();
        try {
            mvc.perform(get("/api/internal/users/" + user.getId()).header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isOk()).andExpect(jsonPath("email").value("read@test"))
                    .andExpect(jsonPath("authProviderId").doesNotExist())
                    .andExpect(jsonPath("addresses").doesNotExist());
            mvc.perform(get("/api/internal/users?page=0&size=10").header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items").isArray())
                    .andExpect(jsonPath("size").value(10));
            mvc.perform(get("/api/internal/users/9223372036854775807").header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/internal/users?page=-1&size=101").header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isBadRequest());
            assertThat(repository.count()).isEqualTo(count);
        } finally { repository.deleteById(user.getId()); }
    }

    @Test
    void rejectsMissingOrIncorrectCredentialsAndAllWrites() throws Exception {
        mvc.perform(get("/api/internal/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/internal/users").header("X-MCP-Service-Key", "wrong"))
                .andExpect(status().isForbidden());
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/internal/users/1")
                            .header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void readsUserSubjectWithValidServiceKey() throws Exception {
        User user = repository.save(User.builder()
                .authProviderId("keycloak-sub-999")
                .email("subject@test")
                .firstName("Sub")
                .lastName("Test")
                .build());
        try {
            mvc.perform(get("/api/internal/users/" + user.getId() + "/subject")
                            .header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId()))
                    .andExpect(jsonPath("$.subject").value("keycloak-sub-999"));

            mvc.perform(get("/api/internal/users/999999999/subject")
                            .header("X-MCP-Service-Key", "test-read-key"))
                    .andExpect(status().isNotFound());
        } finally {
            repository.deleteById(user.getId());
        }
    }
}
