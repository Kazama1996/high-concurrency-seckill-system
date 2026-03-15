CREATE TABLE orders (
                        id                  BIGINT          NOT NULL,
                        user_id             BIGINT          NOT NULL,
                        product_id          BIGINT          NOT NULL,
                        seckill_activity_id BIGINT          NOT NULL,
                        quantity            INTEGER         NOT NULL,
                        original_price      DECIMAL(10, 2)  NOT NULL,
                        seckill_price       DECIMAL(10, 2)  NOT NULL,
                        order_status        VARCHAR(50)     NOT NULL,
                        created_at          TIMESTAMPTZ     NOT NULL,
                        updated_at          TIMESTAMPTZ     NOT NULL,
                        CONSTRAINT pk_orders PRIMARY KEY (id)
);
CREATE INDEX idx_orders_user_id             ON orders (user_id);
CREATE INDEX idx_orders_seckill_activity_id ON orders (seckill_activity_id);