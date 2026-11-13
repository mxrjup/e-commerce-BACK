package com.ecommerce.users.service;

import com.ecommerce.users.dto.StoreSettingsResponseDto;
import com.ecommerce.users.dto.StoreSettingsUpdateRequestDto;
import com.ecommerce.users.entity.StoreSettings;
import com.ecommerce.users.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final StoreSettingsRepository storeSettingsRepository;

    @Transactional
    public StoreSettingsResponseDto getSettings() {
        StoreSettings settings = storeSettingsRepository.findById(StoreSettings.DEFAULT_ID)
                .orElseGet(() -> storeSettingsRepository.save(createDefaultSettings()));
        return mapToDto(settings);
    }

    @Transactional
    public StoreSettingsResponseDto updateSettings(StoreSettingsUpdateRequestDto dto) {
        StoreSettings settings = storeSettingsRepository.findById(StoreSettings.DEFAULT_ID)
                .orElseGet(this::createDefaultSettings);

        settings.setStoreName(dto.getStoreName());
        settings.setContactEmail(dto.getContactEmail());
        settings.setContactPhone(dto.getContactPhone());
        settings.setSupportHours(dto.getSupportHours());
        settings.setPostalAddress(dto.getPostalAddress());

        settings.setCompanyName(dto.getCompanyName());
        settings.setSiret(dto.getSiret());
        settings.setVatNumber(dto.getVatNumber());
        settings.setLegalNotice(dto.getLegalNotice());
        settings.setTermsUrl(dto.getTermsUrl());
        settings.setPrivacyPolicyUrl(dto.getPrivacyPolicyUrl());

        settings.setStandardShippingFee(dto.getStandardShippingFee());
        settings.setExpressShippingFee(dto.getExpressShippingFee());
        settings.setFreeShippingThreshold(dto.getFreeShippingThreshold());
        settings.setMinimumOrderAmount(dto.getMinimumOrderAmount());
        settings.setCurrency(dto.getCurrency().toUpperCase());

        return mapToDto(storeSettingsRepository.save(settings));
    }

    private StoreSettings createDefaultSettings() {
        return StoreSettings.builder()
                .id(StoreSettings.DEFAULT_ID)
                .storeName("E-Commerce Store")
                .contactEmail("contact@ecommerce.test")
                .companyName("E-Commerce SAS")
                .standardShippingFee(new BigDecimal("4.99"))
                .expressShippingFee(new BigDecimal("9.99"))
                .freeShippingThreshold(new BigDecimal("50.00"))
                .minimumOrderAmount(new BigDecimal("10.00"))
                .currency("EUR")
                .build();
    }

    private StoreSettingsResponseDto mapToDto(StoreSettings settings) {
        return StoreSettingsResponseDto.builder()
                .storeName(settings.getStoreName())
                .contactEmail(settings.getContactEmail())
                .contactPhone(settings.getContactPhone())
                .supportHours(settings.getSupportHours())
                .postalAddress(settings.getPostalAddress())
                .companyName(settings.getCompanyName())
                .siret(settings.getSiret())
                .vatNumber(settings.getVatNumber())
                .legalNotice(settings.getLegalNotice())
                .termsUrl(settings.getTermsUrl())
                .privacyPolicyUrl(settings.getPrivacyPolicyUrl())
                .standardShippingFee(settings.getStandardShippingFee())
                .expressShippingFee(settings.getExpressShippingFee())
                .freeShippingThreshold(settings.getFreeShippingThreshold())
                .minimumOrderAmount(settings.getMinimumOrderAmount())
                .currency(settings.getCurrency())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}