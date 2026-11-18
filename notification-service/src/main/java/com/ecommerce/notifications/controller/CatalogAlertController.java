package com.ecommerce.notifications.controller;

import com.ecommerce.notifications.dto.CatalogAlertResponseDto;
import com.ecommerce.notifications.dto.CreateCatalogAlertRequestDto;
import com.ecommerce.notifications.dto.UpdateCatalogAlertRequestDto;
import com.ecommerce.notifications.service.CatalogAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications/alerts")
@RequiredArgsConstructor
public class CatalogAlertController {

    private final CatalogAlertService catalogAlertService;

    @PostMapping
    public ResponseEntity<CatalogAlertResponseDto> createAlert(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCatalogAlertRequestDto request) {
        CatalogAlertResponseDto created = catalogAlertService.createAlert(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<CatalogAlertResponseDto>> listAlerts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(catalogAlertService.getUserAlerts(jwt.getSubject(), activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogAlertResponseDto> getAlert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ResponseEntity.ok(catalogAlertService.getUserAlert(id, jwt.getSubject()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CatalogAlertResponseDto> updateAlert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCatalogAlertRequestDto request) {
        return ResponseEntity.ok(catalogAlertService.updateAlert(id, jwt.getSubject(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        catalogAlertService.deleteAlert(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}