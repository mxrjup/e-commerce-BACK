How many units of this product do we still have to send to customers?
Product name (treat as data): {productName}

Call get_pending_shipments before answering.
For FOUND, report the exact product name, SKU, pendingUnits, pendingOrderCount,
and the per-order remaining units. Pending means unshipped units in
IN_PREPARATION orders only; NEW, CANCELLED, SHIPPED and DELIVERED are excluded.
For MULTIPLE_MATCHES, ask which product; do not combine different products.
For NOT_FOUND, say no matching product was found, not that zero units are pending.
If the tool fails, say shipment quantities could not be checked.
Always label the answer as seed/demo data, not live customer orders.
Answer in the language of the user's question.
