# MCP service structure

```text
src/main/
├── java/com/ecommerce/mcp/
│   ├── McpServiceApplication.java
│   ├── config/       Spring configuration and bean registration
│   ├── prompts/      MCP prompt handlers and their arguments
│   ├── tools/        Operations exposed as MCP tools
│   └── resources/    Read-only context exposed as MCP resources
└── resources/
    ├── application.yml
    └── prompts/      Editable prompt templates (.st) and authoring guide
```

Start with [`src/main/resources/prompts/`](src/main/resources/prompts/README.md)
to write prompt text. `example.st` provides a template to adapt. Implement its
MCP handler in the Java `prompts` package when its behavior is defined.

Keep tools focused on named operations; use resources for read-only context and
prompts for reusable message templates. Creating a package or template does
not expose a capability. Prompt and resource capabilities remain disabled until
their handlers are implemented. The default profile has an empty tool list.

## Demo product stock

The `demo-stock` profile exposes the read-only `get_product_stock` tool and the
`product_stock` MCP prompt. Local Compose enables this profile by default;
set `MCP_PROFILES=default` to disable it. No database is read, seeded or modified.

- Data: [`src/main/resources/seed/product-stock.json`](src/main/resources/seed/product-stock.json).
- Prompt text: [`src/main/resources/prompts/product-stock.st`](src/main/resources/prompts/product-stock.st).
- Tool: `tools/SeedStockTools.java`; prompt handler: `prompts/StockPrompts.java`.

Each seed entry has a unique `sku`, a `name` and a non-negative integer `stock`.
Edit the JSON and rebuild/restart MCP to load changes. Invalid seed entries fail
startup instead of silently reporting incorrect quantities.

Example tool call after MCP initialization and `notifications/initialized`:

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_product_stock","arguments":{"productName":"Logitech G Pro"}}}
```

This returns `source: "seed"`, `status: "FOUND"` and a matching product with
`stock: 24`. Exact names take precedence over partial matches, ignoring case
and surrounding whitespace. `Logitech` returns `MULTIPLE_MATCHES`; an unknown
name returns `NOT_FOUND` with no matches. `Sony WH-1000XM5` is present with zero
stock. No quantities are invented or summed across different products.

An MCP-capable AI client can map “how much stock do I have of Logitech G Pro?”
to this tool. The MCP server itself is not a chatbot and does not run an LLM.
The reusable `product_stock` prompt instructs the client to call the tool and
label results as demo data. A real stock API is still needed for live inventory.

```bash
./mvnw -pl mcp-service spring-boot:run -Dspring-boot.run.profiles=demo-stock
docker compose --profile services up -d --build --no-deps mcp-service
```

Add tests for each implementation under `src/test/java/com/ecommerce/mcp/`,
mirroring its package. The existing `McpHttpTest` covers the transport lifecycle.

See the [server guide](../docs/mcp.md) for startup, gateway access and deployment.

## MCP Inspector with Docker

### Stock and shipment questions

The `demo-stock` profile publishes these reusable prompts in Inspector's
**Prompts** tab. Each takes a `productName` argument:

| Prompt | Example question |
| --- | --- |
| `product_stock` | How many Logitech G Pro units do we have in stock? |
| `pending_shipments` | How many Logitech G Pro units do we still have to send to customers? |
| `stock_coverage` | Do we have enough Logitech G Pro stock for pending shipments? |

Inspector retrieves prompt messages; an AI client executes the tool instructions
and writes a conversational answer. To see the quantities directly in Inspector,
use **Tools** and call `get_product_stock` or `get_pending_shipments` with
`{"productName":"Logitech G Pro"}`.

Shipment seed data lives in `src/main/resources/seed/product-shipments.json`.
Only `quantity - shippedQuantity` on `IN_PREPARATION` lines counts as pending;
NEW, SHIPPED, DELIVERED and CANCELLED orders are excluded. Results include
`pendingUnits`, `pendingOrderCount` and an order breakdown for each matching SKU.
Unknown products return NOT_FOUND, not a zero quantity. Partial names may need
clarification. All order identifiers and quantities are demonstration data.

Examples: Logitech G Pro has 24 stock and 7 pending units across two orders
(3 + 4), leaving a demo balance of 17. Sony WH-1000XM5 has 0 stock and 2 pending
units (shortage 2); Keychron K2 has 12 stock and 14 pending (shortage 2).
Razer DeathAdder V3 has no pending shipments. Coverage comparisons do not model
reservations or live fulfillment. Rebuild the container after editing seed data.

### Connecting

To test the Docker service through the authenticated gateway, start:

```powershell
docker compose --profile services up -d --build mcp-service api-gateway
npx @modelcontextprotocol/inspector
```

In Inspector, choose **Streamable HTTP**, enter
`http://localhost:8080/api/mcp`, and add the request header
`Authorization: Bearer <admin-access-token>`. Use a Keycloak access token with
audience `ecommerce-api` and realm role `admin`.
Open **Tools**, select `get_product_stock`, and call it with
`{"productName":"Logitech G Pro"}`. With `demo-stock` enabled (the Compose default),
the seed result reports stock `24`.

Port 8087 is internal to Docker and has no host mapping. Without a token the
gateway returns 401; a valid token without the admin role returns 403.

For a demonstration, sign into the back-office as `admin.demo@ecommerce.test`
with the development password `demo1234`. In browser developer tools, find a
request to the backend under Network and copy its Authorization bearer value.
Use that value in Inspector's request headers, then reconnect. Access tokens
expire after five minutes by default; use a fresh token if a request returns 401.
The Keycloak administration-console login is separate from this application user.
Inspector's own proxy/session token is also separate from the Keycloak access token.
