package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.VatRate;

import java.math.BigDecimal;

public record VatRateResponse(Long id, String label, BigDecimal rate, boolean defaultRate) {

    public static VatRateResponse from(VatRate vatRate) {
        return new VatRateResponse(vatRate.getId(), vatRate.getLabel(), vatRate.getRate(),
                vatRate.isDefaultRate());
    }
}
