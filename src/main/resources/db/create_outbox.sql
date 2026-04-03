CREATE TABLE IF NOT EXISTS outbox(
    id BIGINT PRIMARY KEY ,
    topic_name VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL
);