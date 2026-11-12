package com.ecommerce.users.controller;

import com.ecommerce.users.dto.StoreSettingsResponseDto;
import com.ecommerce.users.dto.StoreSettingsUpdateRequestDto;
import com.ecommerce.users.service.StoreSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/store-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class StoreSettingsAdminController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    public ResponseEntity<StoreSettingsResponseDto> getStoreSettings() {
        return ResponseEntity.ok(storeSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<StoreSettingsResponseDto> updateStoreSettings(
            @Valid @RequestBody StoreSettingsUpdateRequestDto dto) {
        return ResponseEntity.ok(storeSettingsService.updateSettings(dto));
    }
}