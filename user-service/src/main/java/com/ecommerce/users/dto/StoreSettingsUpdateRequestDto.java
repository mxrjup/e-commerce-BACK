package com.ecommerce.users.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

@Data
@Builder
@Jacksonized
public class StoreSettingsUpdateRequestDto {

    @NotBlank(message = "Store name is required")
    private String storeName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email must be valid")
    private String contactEmail;

    private String contactPhone;
    private String supportHours;
    private String postalAddress;

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String siret;
    private String vatNumber;
    private String legalNotice;
    private String termsUrl;
    private String privacyPolicyUrl;

    @NotNull(message = "Standard shipping fee is required")
    @DecimalMin(value = "0.00", message = "Standard shipping fee must be positive or zero")
    private BigDecimal standardShippingFee;

    @NotNull(message = "Express shipping fee is required")
    @DecimalMin(value = "0.00", message = "Express shipping fee must be positive or zero")
    private BigDecimal expressShippingFee;

    @NotNull(message = "Free shipping threshold is required")
    @DecimalMin(value = "0.00", message = "Free shipping threshold must be positive or zero")
    private BigDecimal freeShippingThreshold;

    @NotNull(message = "Minimum order amount is required")
    @DecimalMin(value = "0.00", message = "Minimum order amount must be positive or zero")
    private BigDecimal minimumOrderAmount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;
}