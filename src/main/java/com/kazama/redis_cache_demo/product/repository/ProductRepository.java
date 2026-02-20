package com.kazama.redis_cache_demo.product.repository;

import com.kazama.redis_cache_demo.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product , Long>  , ProductRepositoryCustom{

    @Query("SELECT p.id FROM Product p")
    List<Long> findAllIds();
}
