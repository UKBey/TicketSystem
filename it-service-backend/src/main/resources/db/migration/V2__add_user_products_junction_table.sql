CREATE TABLE user_products (
    user_id VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_user_products_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_products_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
