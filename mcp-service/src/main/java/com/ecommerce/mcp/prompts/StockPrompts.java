package com.ecommerce.mcp.prompts;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("demo-stock")
public class StockPrompts {

    private final String template;
    private final String shipmentTemplate;
    private final String coverageTemplate;

    public StockPrompts(@Value("classpath:prompts/product-stock.st") Resource template,
                        @Value("classpath:prompts/pending-shipments.st") Resource shipmentTemplate,
                        @Value("classpath:prompts/stock-coverage.st") Resource coverageTemplate) throws IOException {
        this.template = template.getContentAsString(StandardCharsets.UTF_8);
        this.shipmentTemplate = shipmentTemplate.getContentAsString(StandardCharsets.UTF_8);
        this.coverageTemplate = coverageTemplate.getContentAsString(StandardCharsets.UTF_8);
    }

    @McpPrompt(name = "product_stock", description = "Check a product's stock using the demo stock tool")
    public McpSchema.GetPromptResult productStock(
            @McpArg(name = "productName", description = "Name of the product to look up", required = true)
            String productName) {
        return render(template, "Product stock lookup (demo data)", productName);
    }

    @McpPrompt(name = "pending_shipments", description = "How many product units still need to be sent to customers? (demo data)")
    public McpSchema.GetPromptResult pendingShipments(
            @McpArg(name = "productName", description = "Product name", required = true) String productName) {
        return render(shipmentTemplate, "Pending customer shipments (demo data)", productName);
    }

    @McpPrompt(name = "stock_coverage", description = "Does product stock cover the units still to send? (demo data)")
    public McpSchema.GetPromptResult stockCoverage(
            @McpArg(name = "productName", description = "Product name", required = true) String productName) {
        return render(coverageTemplate, "Stock coverage for pending shipments (demo data)", productName);
    }

    private McpSchema.GetPromptResult render(String promptTemplate, String description, String productName) {
        if (productName == null || productName.isBlank() || productName.strip().length() > 200) {
            throw new IllegalArgumentException("Product name must contain 1 to 200 characters");
        }
        String text = promptTemplate.replace("{productName}", productName.strip());
        return new McpSchema.GetPromptResult(description,
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text))));
    }
}
