CREATE TABLE IF NOT EXISTS order_created_outbox(
                       id BIGINT PRIMARY KEY ,
                       topic_name VARCHAR(255) NOT NULL,
                       payload JSONB NOT NULL,
                       status VARCHAR(100) NOT NULL,
                       retry_count         INT             NOT NULL DEFAULT 0,
                       created_at          TIMESTAMPTZ     NOT NULL,
                       updated_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_created_outbox_status ON order_created_outbox (status);