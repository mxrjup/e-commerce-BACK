# Prompt templates

Keep editable prompt text here, separate from Java handlers.

`example.st` is a starting template with three placeholders:

| Placeholder | Meaning |
| --- | --- |
| `{task}` | The task the assistant should perform |
| `{context}` | Context supplied for that task |
| `{responseFormat}` | Expected response format |

Copy it to a descriptive file such as `catalog-summary.st`, then specify the
role, task, inputs, constraints and expected output. Do not put credentials or
personal customer data in templates.

Files are available on the application classpath as `prompts/<filename>`.
Adding a file does not register an MCP prompt or execute it. A Spring bean in
`com.ecommerce.mcp.prompts` must load the template, supply its arguments and
return MCP messages. Define the public prompt name and argument descriptions
in that handler; they are what an MCP client discovers.

The default profile has `spring.ai.mcp.server.capabilities.prompt: false`.
The `demo-stock` profile enables prompts and registers `product_stock`, whose
text is in `product-stock.st` and whose Java handler is `StockPrompts`.
Use `prompts/list` to discover it and `prompts/get` with the `productName`
argument to retrieve its messages. The generic `example.st` remains unregistered.

The same profile also registers `pending_shipments` (`pending-shipments.st`)
and `stock_coverage` (`stock-coverage.st`). These use `get_pending_shipments`
for remaining demo order quantities; coverage also calls `get_product_stock`.
Each prompt requires `productName`. Retrieve them in Inspector's Prompts tab;
Inspector displays their instructions, while an AI client can execute them.
