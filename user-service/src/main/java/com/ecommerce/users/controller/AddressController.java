package com.ecommerce.users.controller;

import com.ecommerce.users.dto.AddressRequestDto;
import com.ecommerce.users.dto.AddressResponseDto;
import com.ecommerce.users.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<List<AddressResponseDto>> getMyAddresses(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(addressService.getUserAddresses(jwt));
    }

    @PostMapping
    public ResponseEntity<AddressResponseDto> createAddress(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody AddressRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.createAddress(jwt, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressResponseDto> updateAddress(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody AddressRequestDto dto) {
        return ResponseEntity.ok(addressService.updateAddress(jwt, id, dto));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<AddressResponseDto> setDefaultAddress(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable Long id) {
        return ResponseEntity.ok(addressService.setDefaultAddress(jwt, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long id) {
        addressService.deleteAddress(jwt, id);
        return ResponseEntity.noContent().build();
    }
}