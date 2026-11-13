package com.ecommerce.users.service;

import com.ecommerce.users.entity.User;
import com.ecommerce.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import com.ecommerce.users.dto.UserAdminResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("mock.jwt.token")
                .header("alg", "none")
                .claim("sub", "keycloak-user-uuid-123")
                .claim("email", "john.doe@example.com")
                .claim("given_name", "John")
                .claim("family_name", "Doe")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
                .build();
    }

    @Test
    @DisplayName("Should create new user when logging in for the first time")
    void syncUserFromJwt_ShouldCreateUser_WhenUserDoesNotExist() {
        when(userRepository.findByAuthProviderId("keycloak-user-uuid-123"))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User u = invocation.getArgument(0);
                    u.setId(1L);
                    return u;
                });

        User result = userService.syncUserFromJwt(mockJwt);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAuthProviderId()).isEqualTo("keycloak-user-uuid-123");
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.isAdmin()).isTrue();

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should return existing user on subsequent logins without saving again")
    void syncUserFromJwt_ShouldReturnExistingUser_WhenUserAlreadyExists() {
        User existingUser = User.builder()
                .id(1L)
                .authProviderId("keycloak-user-uuid-123")
                .email("john.doe@example.com")
                .build();

        when(userRepository.findByAuthProviderId("keycloak-user-uuid-123"))
                .thenReturn(Optional.of(existingUser));

        User result = userService.syncUserFromJwt(mockJwt);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should return paginated users matching search term")
    void getUsers_ShouldReturnPaginatedResult() {
        User u1 = User.builder().id(1L).email("alice@test.com").firstName("Alice").build();
        Page<User> page = new PageImpl<>(List.of(u1));
        Pageable pageable = PageRequest.of(0, 20);

        when(userRepository.searchUsers("alice", pageable)).thenReturn(page);

        Page<UserAdminResponseDto> result = userService.getUsers("alice", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("alice@test.com");
        verify(userRepository, times(1)).searchUsers("alice", pageable);
    }
}