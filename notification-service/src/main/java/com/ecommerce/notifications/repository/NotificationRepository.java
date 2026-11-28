package com.ecommerce.notifications.repository;

import com.ecommerce.notifications.entity.Notification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "variantAlert")
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    @EntityGraph(attributePaths = "variantAlert")
    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(String recipientId);

    @EntityGraph(attributePaths = "variantAlert")
    Optional<Notification> findByIdAndRecipientId(Long id, String recipientId);

    long countByRecipientIdAndReadFalse(String recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.recipientId = :recipientId AND n.read = false")
    int markAllAsReadByRecipientId(@Param("recipientId") String recipientId, @Param("now") Instant now);
}