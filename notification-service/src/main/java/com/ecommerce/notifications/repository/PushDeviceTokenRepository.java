package com.ecommerce.notifications.repository;

import com.ecommerce.notifications.entity.PushDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    Optional<PushDeviceToken> findByExpoToken(String expoToken);

    List<PushDeviceToken> findByRecipientIdAndActiveTrue(String recipientId);

    void deleteByRecipientIdAndExpoToken(String recipientId, String expoToken);
}