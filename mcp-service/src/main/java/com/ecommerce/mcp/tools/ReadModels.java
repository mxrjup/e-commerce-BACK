package com.ecommerce.mcp.tools;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Explicit wire contracts; never serialize persistence entities or arbitrary upstream fields. */
public final class ReadModels {
    private ReadModels() {}

    public record Variant(Long id, Long productId, String productSlug, String sku, String designation,
                          BigDecimal unitPriceExclTax, BigDecimal vatRate, int stock,
                          String status, boolean available, @Schema(nullable = true) String imageUrl) {}
    public record Variants(List<Variant> items) {}
    public enum OrderStatus { NEW, IN_PREPARATION, SHIPPED, DELIVERED, CANCELLED }
    public record OrderItem(Long id, Long variantId, String designation, BigDecimal unitPriceExclTax,
                            BigDecimal vatRate, int quantity, BigDecimal subTotalExclTax,
                            BigDecimal subTotalInclTax) {}
    public record Order(Long id, String orderNumber, Long userId, OrderStatus status,
                        @Schema(nullable = true) String deliveryMode,
                        @Schema(nullable = true) Long shippingAddressId,
                        @Schema(nullable = true) BigDecimal totalExclTax,
                        @Schema(nullable = true) BigDecimal totalInclTax,
                        Instant createdAt, List<OrderItem> items) {}
    public record Orders(List<Order> items) {}
    public record User(Long id, String email, @Schema(nullable = true) String firstName,
                       @Schema(nullable = true) String lastName) {}
    public record Users(List<User> items, int page, int size, long totalElements, int totalPages) {}
}
