package com.ecommerce.users.service;

import com.ecommerce.users.entity.User;
import com.ecommerce.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ecommerce.users.dto.UserProfileResponseDto;
import com.ecommerce.users.dto.UserProfileUpdateRequestDto;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User syncUserFromJwt(Jwt jwt) {
        String sub = jwt.getSubject();

        return userRepository.findByAuthProviderId(sub)
                .orElseGet(() -> createUserFromJwt(jwt, sub));
    }

    private User createUserFromJwt(Jwt jwt, String sub) {
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        boolean isAdmin = checkAdminRole(jwt);

        User newUser = User.builder()
                .authProviderId(sub)
                .email(email != null ? email : "")
                .firstName(firstName)
                .lastName(lastName)
                .isAdmin(isAdmin)
                .build();

        return userRepository.save(newUser);
    }

    @SuppressWarnings("unchecked")
    private boolean checkAdminRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.contains("admin");
        }
        return false;
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDto getUserProfile(Jwt jwt) {
        User user = syncUserFromJwt(jwt);
        return mapToProfileDto(user);
    }

    @Transactional
    public UserProfileResponseDto updateUserProfile(Jwt jwt, UserProfileUpdateRequestDto dto) {
        User user = syncUserFromJwt(jwt);
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        return mapToProfileDto(userRepository.save(user));
    }

    private UserProfileResponseDto mapToProfileDto(User user) {
        return UserProfileResponseDto.builder()
                .id(user.getId())
                .authProviderId(user.getAuthProviderId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponseDto> getUsers(String search, Pageable pageable) {
        return userRepository.searchUsers(search, pageable)
                .map(this::mapToAdminDto);
    }

    private UserAdminResponseDto mapToAdminDto(User user) {
        return UserAdminResponseDto.builder()
                .id(user.getId())
                .authProviderId(user.getAuthProviderId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .build();
    }
}