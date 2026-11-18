package com.ecommerce.notifications.repository;

import com.ecommerce.notifications.entity.CatalogAlertSubscription;
import com.ecommerce.notifications.entity.CatalogAlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogAlertSubscriptionRepository extends JpaRepository<CatalogAlertSubscription, Long> {

    List<CatalogAlertSubscription> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<CatalogAlertSubscription> findByRecipientIdAndActiveTrueOrderByCreatedAtDesc(String recipientId);

    Optional<CatalogAlertSubscription> findByIdAndRecipientId(Long id, String recipientId);

    Optional<CatalogAlertSubscription> findByRecipientIdAndVariantIdAndAlertType(
            String recipientId, Long variantId, CatalogAlertType alertType);

    List<CatalogAlertSubscription> findByVariantIdAndAlertTypeAndActiveTrue(
            Long variantId, CatalogAlertType alertType);
}