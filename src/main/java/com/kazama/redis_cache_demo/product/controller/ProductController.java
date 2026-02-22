package com.kazama.redis_cache_demo.product.controller;

import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.dto.UpdateProductRequest;
import com.kazama.redis_cache_demo.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.naming.ServiceUnavailableException;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;



    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) throws ServiceUnavailableException {
        log.info("API request : getProduct :{}" , id);

        ProductDTO product = productService.getProductById(id);

        if(product==null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }


    @PatchMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(@PathVariable Long id , @RequestBody UpdateProductRequest request){
        log.info("API request : updateProduct [ id :{} ,  request:{} ]" , id , request);

        ProductDTO updatedProduct = productService.updateProduct(id, request);

        return ResponseEntity.ok(updatedProduct);


    }

}
