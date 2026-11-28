package com.ecommerce.users.service;

import com.ecommerce.users.dto.StoreSettingsResponseDto;
import com.ecommerce.users.dto.StoreSettingsUpdateRequestDto;
import com.ecommerce.users.entity.StoreSettings;
import com.ecommerce.users.repository.StoreSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSettingsServiceTest {

    @Mock
    private StoreSettingsRepository storeSettingsRepository;

    @InjectMocks
    private StoreSettingsService storeSettingsService;

    @Test
    @DisplayName("Should initialize and return default settings when none exist")
    void getSettings_ShouldCreateDefault_WhenNotFound() {
        when(storeSettingsRepository.findById(StoreSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(storeSettingsRepository.save(any(StoreSettings.class))).thenAnswer(i -> i.getArgument(0));

        StoreSettingsResponseDto result = storeSettingsService.getSettings();

        assertThat(result.getStoreName()).isEqualTo("E-Commerce Store");
        assertThat(result.getFreeShippingThreshold()).isEqualByComparingTo("50.00");
        assertThat(result.getCurrency()).isEqualTo("EUR");
        verify(storeSettingsRepository, times(1)).save(any(StoreSettings.class));
    }

    @Test
    @DisplayName("Should update store settings with provided values")
    void updateSettings_ShouldPersistChanges() {
        StoreSettings existing = StoreSettings.builder()
                .id(StoreSettings.DEFAULT_ID)
                .storeName("Old Name")
                .contactEmail("old@test.com")
                .companyName("Old Corp")
                .standardShippingFee(new BigDecimal("5.00"))
                .expressShippingFee(new BigDecimal("10.00"))
                .freeShippingThreshold(new BigDecimal("50.00"))
                .minimumOrderAmount(new BigDecimal("10.00"))
                .currency("EUR")
                .build();

        StoreSettingsUpdateRequestDto updateDto = StoreSettingsUpdateRequestDto.builder()
                .storeName("Ma Boutique")
                .contactEmail("support@maboutique.fr")
                .contactPhone("+33 1 23 45 67 89")
                .companyName("Ma Boutique SAS")
                .siret("12345678900012")
                .vatNumber("FR12345678900")
                .standardShippingFee(new BigDecimal("4.50"))
                .expressShippingFee(new BigDecimal("8.90"))
                .freeShippingThreshold(new BigDecimal("60.00"))
                .minimumOrderAmount(new BigDecimal("15.00"))
                .currency("eur")
                .build();

        when(storeSettingsRepository.findById(StoreSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(storeSettingsRepository.save(any(StoreSettings.class))).thenAnswer(i -> i.getArgument(0));

        StoreSettingsResponseDto result = storeSettingsService.updateSettings(updateDto);

        assertThat(result.getStoreName()).isEqualTo("Ma Boutique");
        assertThat(result.getSiret()).isEqualTo("12345678900012");
        assertThat(result.getFreeShippingThreshold()).isEqualByComparingTo("60.00");
        assertThat(result.getCurrency()).isEqualTo("EUR");
        verify(storeSettingsRepository, times(1)).save(existing);
    }
}