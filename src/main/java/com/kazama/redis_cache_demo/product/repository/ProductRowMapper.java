package com.kazama.redis_cache_demo.product.repository;

import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.product.enums.ProductCategory;
import org.springframework.jdbc.core.RowMapper;

import javax.swing.tree.TreePath;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductRowMapper implements RowMapper<ProductDTO> {


    @Override
    public ProductDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductDTO(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("image_url"),
                ProductCategory.valueOf(rs.getString("category")),
                rs.getBoolean("is_seckill")
        );
    }
}
