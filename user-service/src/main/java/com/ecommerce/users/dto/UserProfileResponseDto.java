package com.ecommerce.users.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponseDto {
    private Long id;
    private String authProviderId;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
}