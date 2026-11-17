package com.ecommerce.notifications.repository;

import com.ecommerce.notifications.entity.VariantAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VariantAlertRepository extends JpaRepository<VariantAlert, Long> {

    List<VariantAlert> findByResolvedFalseOrderByCreatedAtDesc();

    List<VariantAlert> findByVariantIdAndResolvedFalse(Long variantId);
}