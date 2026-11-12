package com.ecommerce.users.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.extern.jackson.Jacksonized;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Jacksonized
public class AddressRequestDto {
    private String label;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @NotBlank(message = "Street is required")
    private String street;

    private String complement;

    @NotBlank(message = "Zip code is required")
    private String zipCode;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Country is required")
    private String country;

    private boolean isDefault;
}