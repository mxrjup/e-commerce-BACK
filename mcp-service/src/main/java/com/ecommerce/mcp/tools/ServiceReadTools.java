package com.ecommerce.mcp.tools;

import com.ecommerce.mcp.auth.McpAuthentication;
import com.ecommerce.mcp.token.McpScope;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.ecommerce.mcp.tools.ReadModels.*;

@Component
public class ServiceReadTools {
    private final ReadServiceClient client;

    public ServiceReadTools(ReadServiceClient client) { this.client = client; }

    @McpTool(name = "get_catalog_variant", description = "Read a live catalogue variant by its numeric ID.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Variant getVariant(@McpToolParam(description = "Positive variant ID", required = true) Long id) {
        McpAuthentication.requireScope(McpScope.CATALOG_READ);
        positive(id);
        return client.catalog("/api/internal/catalog/variants/" + id, Variant.class);
    }

    @McpTool(name = "get_catalog_variants", description = "Read 1 to 100 live catalogue variants by ID. "
            + "Duplicates are collapsed and unknown IDs are omitted; missing does not mean zero stock.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Variants getVariants(@McpToolParam(description = "1 to 100 positive variant IDs", required = true) List<Long> ids) {
        McpAuthentication.requireScope(McpScope.CATALOG_READ);
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("Provide 1 to 100 IDs");
        ids.forEach(ServiceReadTools::positive);
        String query = ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
        return new Variants(Arrays.asList(client.catalog("/api/internal/catalog/variants?ids=" + query, Variant[].class)));
    }

    @McpTool(name = "get_order", description = "Read a live order and its lines by ID.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Order getOrder(@McpToolParam(description = "Positive order ID", required = true) Long id) {
        McpAuthentication.requireScope(McpScope.ORDERS_READ);
        positive(id);
        return client.orders("/api/internal/orders/" + id, Order.class);
    }

    @McpTool(name = "list_user_orders", description = "Read live orders for one user, optionally filtered by status.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Orders listUserOrders(@McpToolParam(description = "Positive local user ID", required = true) Long userId,
            @McpToolParam(description = "Optional order status", required = false) OrderStatus status) {
        McpAuthentication.requireScope(McpScope.ORDERS_READ);
        positive(userId);
        String path = "/api/internal/orders?userId=" + userId + (status == null ? "" : "&status=" + status.name());
        return new Orders(Arrays.asList(client.orders(path, Order[].class)));
    }

    @McpTool(name = "get_user", description = "Read an existing local user. Does not provision or synchronize accounts.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public User getUser(@McpToolParam(description = "Positive local user ID", required = true) Long id) {
        McpAuthentication.requireScope(McpScope.USERS_READ);
        positive(id);
        return client.users("/api/internal/users/" + id, User.class);
    }

    @McpTool(name = "list_users", description = "Read a page of existing local users, ordered by ID.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Users listUsers(@McpToolParam(description = "Zero-based page, at least 0", required = true) Integer page,
            @McpToolParam(description = "Page size, 1 to 100", required = true) Integer size) {
        McpAuthentication.requireScope(McpScope.USERS_READ);
        if (page == null || page < 0 || size == null || size < 1 || size > 100)
            throw new IllegalArgumentException("Page must be non-negative and size between 1 and 100");
        return client.users("/api/internal/users?page=" + page + "&size=" + size, Users.class);
    }

    private static void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID must be positive");
    }
}
