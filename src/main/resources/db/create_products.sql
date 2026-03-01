CREATE TABLE products (
                          id BIGINT NOT NULL,
                          name VARCHAR(200) NOT NULL,
                          description TEXT,
                          category VARCHAR(20) NOT NULL,
                          price DECIMAL(10, 2) NOT NULL,
                          stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
                          image_url VARCHAR(500),
                          is_seckill BOOLEAN DEFAULT FALSE NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL,
                          PRIMARY KEY (id)
);

CREATE INDEX idx_category_seckill ON products (category, is_seckill);
