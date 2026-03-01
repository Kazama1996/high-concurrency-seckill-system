package com.kazama.redis_cache_demo.infra.init.service;

import com.github.javafaker.Faker;
import com.kazama.redis_cache_demo.infra.bloomfilter.impl.ProductBloomFilterService;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.product.enums.ProductCategory;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitService {

    private final ProductRepository productRepository;
    private final ProductBloomFilterService productBloomFilterService;

    private final Faker faker = new Faker(Locale.TRADITIONAL_CHINESE);
    private final Random random = new Random();


    @Transactional
    public void initProducts(int count , int seckillCount){
        log.info("Start to initlize, counts:{} , seckillCount:{}" , count , seckillCount);

        long existingCount= productRepository.count();
        if(existingCount>0){
            log.warn("There are {} products in DB , will be prune" , existingCount);
            productRepository.deleteAll();
        }

        List<Product> products = new ArrayList<>();

        for(int i =0 ; i< count - seckillCount; i++){
            products.add(createRandomProduct(false));
        }

        for(int i=0 ; i< seckillCount;i++){
            products.add(createRandomProduct(true));
        }

        List<Product> savedProducts = productRepository.saveAll(products);
        log.info("Save {} products successfully" , savedProducts.size());

        productBloomFilterService.rebuild();
        log.info("Bloom filter rebuild success");
    }

    private Product createRandomProduct(boolean isSeckill){

        Product product = new Product();

        // 隨機分類
        ProductCategory category = randomCategory();
        product.setCategory(category);

        // 根據分類生成商品名稱
        product.setName(generateProductName(category));

        // 商品描述
        product.setDescription(faker.lorem().sentence(10));

        // 價格
        if (isSeckill) {
            // 秒殺商品價格較低
            product.setPrice(randomPrice(1, 99));
        } else {
            product.setPrice(randomPrice(10, 9999));
        }

        //庫存
        product.setStock(randomStock(10,10000000));


        // 圖片 URL（使用 placeholder）
        product.setImageUrl("https://via.placeholder.com/400x400?text=" +
                java.net.URLEncoder.encode(product.getName(), java.nio.charset.StandardCharsets.UTF_8));

        // 是否秒殺
        product.setIsSeckill(isSeckill);

        // 時間戳（會自動設定，但這裡手動設定以示範）
        Instant now = Instant.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        return product;

    }

    private String generateProductName(ProductCategory category){
        return switch (category) {
            case ELECTRONICS -> faker.options().option(
                    "iPhone 15 Pro", "MacBook Air M2", "iPad Pro",
                    "AirPods Pro", "Apple Watch", "Sony 耳機",
                    "Samsung Galaxy S24", "小米 14 Pro", "華碩 ROG 電競筆電"
            );
            case CLOTHING -> faker.options().option(
                    "Nike 運動外套", "Adidas 運動褲", "Uniqlo 襯衫",
                    "Levi's 牛仔褲", "H&M 連帽上衣", "Zara 休閒外套"
            );
            case FOOD -> faker.options().option(
                    "日本進口零食禮盒", "星巴克咖啡豆", "義美泡芙",
                    "巧克力禮盒", "台灣茶葉禮盒", "有機堅果綜合包"
            );
            case BOOKS -> faker.book().title();
            case SPORTS -> faker.options().option(
                    "Wilson 網球拍", "Nike 籃球", "瑜珈墊",
                    "啞鈴組", "跑步機", "飛輪健身車"
            );
            case HOME -> faker.options().option(
                    "IKEA 收納櫃", "無印良品香氛機", "Dyson 吸塵器",
                    "掃地機器人", "空氣清淨機", "電風扇"
            );
            case BEAUTY -> faker.options().option(
                    "SK-II 神仙水", "雅詩蘭黛精華液", "蘭蔻粉底液",
                    "資生堂防曬乳", "巴黎萊雅面膜", "Olay 抗皺霜"
            );
            case TOYS -> faker.options().option(
                    "樂高積木", "芭比娃娃", "遙控車",
                    "拼圖遊戲", "桌遊", "模型飛機"
            );
            case AUTOMOTIVE -> faker.options().option(
                    "車用吸塵器", "行車記錄器", "汽車香氛",
                    "雨刷", "機油", "車用手機架"
            );
            case DIGITAL -> faker.options().option(
                    "Netflix 訂閱", "Spotify Premium", "Adobe CC",
                    "Office 365", "遊戲點數卡", "Steam 遊戲"
            );
        };
    }

    private ProductCategory randomCategory() {
        ProductCategory[] categories = ProductCategory.values();
        return categories[random.nextInt(categories.length)];
    }

    private BigDecimal randomPrice(int min, int max) {
        double price = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer randomStock(int min , int max){
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    public void initDefaultData(){
        initProducts(1000,10);
    }


}
