package com.ecommerce.mcp.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Profile("demo-stock")
public class SeedShipmentTools {
    private final List<ShipmentLine> lines;

    public SeedShipmentTools(ObjectMapper mapper,
            @Value("classpath:seed/product-shipments.json") Resource seed) throws IOException {
        try (var input = seed.getInputStream()) {
            List<ShipmentLine> loaded = mapper.readValue(input, new TypeReference<>() {});
            if (loaded == null) throw new IllegalArgumentException("Shipment seed must be an array");
            Set<List<String>> keys = new HashSet<>();
            Map<String, String> names = new HashMap<>();
            for (ShipmentLine line : loaded) {
                if (line == null || blank(line.orderId()) || blank(line.sku()) || blank(line.productName())
                        || line.quantity() == null || line.quantity() <= 0
                        || line.shippedQuantity() == null || line.shippedQuantity() < 0
                        || line.shippedQuantity() > line.quantity() || line.status() == null
                        || !keys.add(List.of(line.orderId(), line.sku()))) {
                    throw new IllegalArgumentException("Invalid or duplicate shipment seed line");
                }
                String previous = names.putIfAbsent(line.sku(), line.productName());
                if (previous != null && !previous.equals(line.productName())) {
                    throw new IllegalArgumentException("Shipment SKU must have a consistent product name");
                }
            }
            lines = List.copyOf(loaded);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @McpTool(name = "get_pending_shipments",
            description = "How many product units still need sending to customers? Seed/demo orders only, NOT live orders. "
                    + "Counts quantity minus shippedQuantity for IN_PREPARATION orders only. "
                    + "Excludes NEW, CANCELLED, SHIPPED and DELIVERED. Exact product names precede partial matches. "
                    + "Ask which product for MULTIPLE_MATCHES; NOT_FOUND is not zero pending.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ShipmentLookup getPendingShipments(
            @McpToolParam(description = "Product name, for example Logitech G Pro", required = true) String productName) {
        if (blank(productName) || productName.strip().length() > 200) {
            throw new IllegalArgumentException("Product name must contain 1 to 200 characters");
        }
        String query = productName.strip();
        Map<String, String> products = new LinkedHashMap<>();
        lines.forEach(line -> products.putIfAbsent(line.sku(), line.productName()));
        var matches = products.entrySet().stream().filter(p -> p.getValue().equalsIgnoreCase(query)).toList();
        if (matches.isEmpty()) {
            String normalized = query.toLowerCase(Locale.ROOT);
            matches = products.entrySet().stream()
                    .filter(p -> p.getValue().toLowerCase(Locale.ROOT).contains(normalized)).toList();
        }
        List<PendingProduct> results = matches.stream().map(product -> {
            List<PendingOrder> orders = lines.stream()
                    .filter(line -> line.sku().equals(product.getKey()) && line.status() == Status.IN_PREPARATION)
                    .filter(line -> line.quantity() > line.shippedQuantity())
                    .map(line -> new PendingOrder(line.orderId(), line.quantity() - line.shippedQuantity())).toList();
            long units = orders.stream().mapToLong(PendingOrder::remainingUnits).sum();
            return new PendingProduct(product.getKey(), product.getValue(), units, orders.size(), orders);
        }).toList();
        String status = results.isEmpty() ? "NOT_FOUND" : results.size() == 1 ? "FOUND" : "MULTIPLE_MATCHES";
        return new ShipmentLookup("seed", query, status, results);
    }

    public enum Status { NEW, IN_PREPARATION, SHIPPED, DELIVERED, CANCELLED }
    public record ShipmentLine(String orderId, String sku, String productName, Integer quantity,
                               Integer shippedQuantity, Status status) {}
    public record PendingOrder(String orderId, int remainingUnits) {}
    public record PendingProduct(String sku, String name, long pendingUnits, int pendingOrderCount,
                                 List<PendingOrder> orders) {}
    public record ShipmentLookup(String source, String query, String status, List<PendingProduct> matches) {}
}
