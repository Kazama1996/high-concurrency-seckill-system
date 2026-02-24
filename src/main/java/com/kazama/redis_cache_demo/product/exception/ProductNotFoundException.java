package com.kazama.redis_cache_demo.product.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }


    public ProductNotFoundException(Long id){
        super("Product not found: "+id);
    }
}
