package com.ecommerce.users.dto;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Data;

@Data
@Builder
@Jacksonized
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