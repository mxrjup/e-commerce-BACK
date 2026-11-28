package com.ecommerce.users.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class StoreSettingsResponseDto {
    private String storeName;
    private String contactEmail;
    private String contactPhone;
    private String supportHours;
    private String postalAddress;

    private String companyName;
    private String siret;
    private String vatNumber;
    private String legalNotice;
    private String termsUrl;
    private String privacyPolicyUrl;

    private BigDecimal standardShippingFee;
    private BigDecimal expressShippingFee;
    private BigDecimal freeShippingThreshold;
    private BigDecimal minimumOrderAmount;
    private String currency;
    private Instant updatedAt;
}