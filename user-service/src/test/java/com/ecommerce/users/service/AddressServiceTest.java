package com.ecommerce.users.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.users.dto.AddressRequestDto;
import com.ecommerce.users.dto.AddressResponseDto;
import com.ecommerce.users.entity.Address;
import com.ecommerce.users.entity.User;
import com.ecommerce.users.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AddressService addressService;

    private User mockUser;
    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).authProviderId("sub-123").email("john@example.com").build();
        mockJwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "sub-123").build();
    }

    @Test
    @DisplayName("Should reset previous default address when creating a new default address")
    void createAddress_ShouldResetDefaults_WhenNewIsDefault() {
        Address oldDefault = Address.builder().id(10L).user(mockUser).isDefault(true).build();
        List<Address> addresses = new ArrayList<>(List.of(oldDefault));

        AddressRequestDto newAddressDto = AddressRequestDto.builder()
                .recipient("John Doe")
                .street("123 Main St")
                .zipCode("75000")
                .city("Paris")
                .country("France")
                .isDefault(true)
                .build();

        when(userService.syncUserFromJwt(mockJwt)).thenReturn(mockUser);
        when(addressRepository.findByUserId(1L)).thenReturn(addresses);
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> {
            Address a = i.getArgument(0);
            a.setId(11L);
            return a;
        });

        AddressResponseDto response = addressService.createAddress(mockJwt, newAddressDto);

        assertThat(response.isDefault()).isTrue();
        assertThat(oldDefault.isDefault()).isFalse();
        verify(addressRepository, times(1)).saveAll(addresses);
    }

    @Test
    @DisplayName("Should update own address and reset other defaults when set to default")
    void updateAddress_ShouldUpdateFields_WhenAddressBelongsToUser() {
        Address existingAddress = Address.builder()
                .id(10L)
                .user(mockUser)
                .recipient("John Doe")
                .street("Old St")
                .zipCode("75000")
                .city("Paris")
                .country("France")
                .isDefault(false)
                .build();

        AddressRequestDto updateDto = AddressRequestDto.builder()
                .label("Work")
                .recipient("John Doe")
                .street("456 New St")
                .zipCode("69000")
                .city("Lyon")
                .country("France")
                .isDefault(true)
                .build();

        when(userService.syncUserFromJwt(mockJwt)).thenReturn(mockUser);
        when(addressRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(existingAddress));
        when(addressRepository.findByUserId(1L)).thenReturn(List.of(existingAddress));
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));

        AddressResponseDto response = addressService.updateAddress(mockJwt, 10L, updateDto);

        assertThat(response.getStreet()).isEqualTo("456 New St");
        assertThat(response.getCity()).isEqualTo("Lyon");
        assertThat(response.isDefault()).isTrue();
        verify(addressRepository, times(1)).save(existingAddress);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when address to delete does not exist")
    void deleteAddress_ShouldThrowNotFound_WhenAddressDoesNotExist() {
        when(userService.syncUserFromJwt(mockJwt)).thenReturn(mockUser);
        when(addressRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(mockJwt, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when updating another user's address")
    void updateAddress_ShouldThrowNotFound_WhenAddressBelongsToAnotherUser() {
        AddressRequestDto updateDto = AddressRequestDto.builder()
                .recipient("Jane Doe")
                .street("99 Other St")
                .zipCode("75001")
                .city("Paris")
                .country("France")
                .isDefault(true)
                .build();

        when(userService.syncUserFromJwt(mockJwt)).thenReturn(mockUser);
        // Address 42 belongs to another user (e.g. userId = 2), so querying with userId = 1 returns empty
        when(addressRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(mockJwt, 42L, updateDto))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(addressRepository, never()).save(any());
        verify(addressRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when setting another user's address as default")
    void setDefaultAddress_ShouldThrowNotFound_WhenAddressBelongsToAnotherUser() {
        when(userService.syncUserFromJwt(mockJwt)).thenReturn(mockUser);
        when(addressRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.setDefaultAddress(mockJwt, 42L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(addressRepository, never()).save(any());
        verify(addressRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting another user's address")
    void deleteAddress_ShouldThrowNotFound_WhenAddressBelongsToAnotherUser() {
        when(userService.syncUserFromJwt(mockJwt)).thenReturn(mockUser);
        when(addressRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(mockJwt, 42L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(addressRepository, never()).delete(any());
    }
}