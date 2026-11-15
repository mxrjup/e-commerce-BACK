CREATE TABLE IF NOT EXISTS customer_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    stripe_customer_id VARCHAR(255),
    stripe_email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS saved_payment_method (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_payment_method_id VARCHAR(255) NOT NULL UNIQUE,
    brand VARCHAR(255),
    last4 VARCHAR(20),
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);
