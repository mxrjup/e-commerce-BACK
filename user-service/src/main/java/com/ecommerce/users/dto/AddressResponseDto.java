package com.ecommerce.users.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResponseDto {
    private Long id;
    private String label;
    private String recipient;
    private String street;
    private String complement;
    private String zipCode;
    private String city;
    private String country;
    private boolean isDefault;
}