package com.ecommerce.notifications.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "push_device_tokens",
        indexes = {
                @Index(name = "idx_push_tokens_recipient_active", columnList = "recipient_id, active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_tokens_expo_token", columnNames = "expo_token")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 128)
    private String recipientId;

    @Column(name = "expo_token", nullable = false, length = 255)
    private String expoToken;

    @Column(name = "platform", length = 32)
    private String platform; // ios, android

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.lastUsedAt == null) {
            this.lastUsedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}