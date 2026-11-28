package com.ecommerce.users.controller;

import com.ecommerce.users.dto.UserProfileResponseDto;
import com.ecommerce.users.dto.UserProfileUpdateRequestDto;
import com.ecommerce.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getUserProfile(jwt));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponseDto> updateMyProfile(@AuthenticationPrincipal Jwt jwt,
                                                                  @Valid @RequestBody UserProfileUpdateRequestDto dto) {
        return ResponseEntity.ok(userService.updateUserProfile(jwt, dto));
    }
}