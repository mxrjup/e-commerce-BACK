package com.ecommerce.users.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.users.dto.AddressRequestDto;
import com.ecommerce.users.dto.AddressResponseDto;
import com.ecommerce.users.entity.Address;
import com.ecommerce.users.entity.User;
import com.ecommerce.users.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AddressResponseDto> getUserAddresses(Jwt jwt) {
        User user = userService.syncUserFromJwt(jwt);
        return addressRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public AddressResponseDto createAddress(Jwt jwt, AddressRequestDto dto) {
        User user = userService.syncUserFromJwt(jwt);

        if (dto.isDefault()) {
            resetDefaultAddresses(user.getId());
        }

        Address address = Address.builder()
                .user(user)
                .label(dto.getLabel())
                .recipient(dto.getRecipient())
                .street(dto.getStreet())
                .complement(dto.getComplement())
                .zipCode(dto.getZipCode())
                .city(dto.getCity())
                .country(dto.getCountry())
                .isDefault(dto.isDefault())
                .build();

        return mapToDto(addressRepository.save(address));
    }

    @Transactional
    public AddressResponseDto updateAddress(Jwt jwt, Long addressId, AddressRequestDto dto) {
        User user = userService.syncUserFromJwt(jwt);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));

        if (dto.isDefault() && !address.isDefault()) {
            resetDefaultAddresses(user.getId());
        }

        address.setLabel(dto.getLabel());
        address.setRecipient(dto.getRecipient());
        address.setStreet(dto.getStreet());
        address.setComplement(dto.getComplement());
        address.setZipCode(dto.getZipCode());
        address.setCity(dto.getCity());
        address.setCountry(dto.getCountry());
        address.setDefault(dto.isDefault());

        return mapToDto(addressRepository.save(address));
    }

    @Transactional
    public AddressResponseDto setDefaultAddress(Jwt jwt, Long addressId) {
        User user = userService.syncUserFromJwt(jwt);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));

        resetDefaultAddresses(user.getId());
        address.setDefault(true);
        return mapToDto(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Jwt jwt, Long addressId) {
        User user = userService.syncUserFromJwt(jwt);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));

        addressRepository.delete(address);
    }

    private void resetDefaultAddresses(Long userId) {
        List<Address> addresses = addressRepository.findByUserId(userId);
        addresses.forEach(a -> a.setDefault(false));
        addressRepository.saveAll(addresses);
    }

    private AddressResponseDto mapToDto(Address address) {
        return AddressResponseDto.builder()
                .id(address.getId())
                .label(address.getLabel())
                .recipient(address.getRecipient())
                .street(address.getStreet())
                .complement(address.getComplement())
                .zipCode(address.getZipCode())
                .city(address.getCity())
                .country(address.getCountry())
                .isDefault(address.isDefault())
                .build();
    }
}