package com.ecommerce.users.controller;

import com.ecommerce.users.dto.StoreSettingsResponseDto;
import com.ecommerce.users.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-settings")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    public ResponseEntity<StoreSettingsResponseDto> getStoreSettings() {
        return ResponseEntity.ok(storeSettingsService.getSettings());
    }
}