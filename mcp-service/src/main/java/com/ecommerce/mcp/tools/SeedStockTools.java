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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Component
@Profile("demo-stock")
public class SeedStockTools {

    private final List<ProductStock> products;

    public SeedStockTools(ObjectMapper mapper,
                          @Value("classpath:seed/product-stock.json") Resource seed) throws IOException {
        try (var input = seed.getInputStream()) {
            List<ProductStock> loaded = mapper.readValue(input, new TypeReference<>() {});
            if (loaded == null) {
                throw new IllegalArgumentException("Stock seed must be a JSON array");
            }
            var skus = new HashSet<String>();
            for (ProductStock product : loaded) {
                if (product == null || product.sku() == null || product.sku().isBlank()
                        || product.name() == null || product.name().isBlank()
                        || product.stock() == null || product.stock() < 0
                        || !skus.add(product.sku())) {
                    throw new IllegalArgumentException("Invalid stock seed: require unique SKUs, names and non-negative stock");
                }
            }
            products = List.copyOf(loaded);
        }
    }

    @McpTool(name = "get_product_stock",
            description = "Find stock by product name in seed/demo data, NOT live inventory. "
                    + "Use for questions like 'how much stock do I have of X?'. "
                    + "Case-insensitive exact names take priority over partial names. "
                    + "For MULTIPLE_MATCHES ask which product; NOT_FOUND does not mean zero stock.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public StockLookup getProductStock(
            @McpToolParam(description = "Product name only, for example Logitech G Pro", required = true)
            String productName) {
        if (productName == null || productName.isBlank() || productName.strip().length() > 200) {
            throw new IllegalArgumentException("Product name must contain 1 to 200 characters");
        }
        String query = productName.strip();
        List<ProductStock> matches = products.stream()
                .filter(product -> product.name().equalsIgnoreCase(query)).toList();
        if (matches.isEmpty()) {
            String normalized = query.toLowerCase(Locale.ROOT);
            matches = products.stream()
                    .filter(product -> product.name().toLowerCase(Locale.ROOT).contains(normalized)).toList();
        }
        String status = matches.isEmpty() ? "NOT_FOUND" : matches.size() == 1 ? "FOUND" : "MULTIPLE_MATCHES";
        return new StockLookup("seed", query, status, matches);
    }

    public record ProductStock(String sku, String name, Integer stock) {}

    public record StockLookup(String source, String query, String status, List<ProductStock> matches) {}
}
