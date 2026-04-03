CREATE TABLE IF NOT EXISTS seckill_activities (
                                    id BIGINT NOT NULL,
                                    product_id BIGINT NOT NULL,
                                    original_price DECIMAL(10,2) NOT NULL,
                                    seckill_price DECIMAL(10, 2) NOT NULL,
                                    total_stock INT NOT NULL,
                                    remaining_stock INT NOT NULL,
                                    max_quantity_per_order INT NOT NULL,
                                    start_time TIMESTAMPTZ NOT NULL,
                                    end_time TIMESTAMPTZ NOT NULL,
                                    status VARCHAR(20) NOT NULL,
                                    created_at TIMESTAMPTZ NOT NULL,
                                    updated_at TIMESTAMPTZ NOT NULL,
                                    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_product_status ON seckill_activities (product_id, status);
CREATE INDEX IF NOT EXISTS idx_start_time ON seckill_activities (start_time);