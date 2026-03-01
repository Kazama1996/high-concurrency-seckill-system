package com.kazama.redis_cache_demo.seckill.service;

import com.kazama.redis_cache_demo.infra.bloomfilter.impl.ProductBloomFilterService;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.product.exception.InsufficientStockException;
import com.kazama.redis_cache_demo.product.exception.ProductNotFoundException;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import com.kazama.redis_cache_demo.product.service.ProductService;
import com.kazama.redis_cache_demo.seckill.dto.CreateSeckillActivityRequest;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.entity.SeckillActivity;
import com.kazama.redis_cache_demo.seckill.enums.Status;
import com.kazama.redis_cache_demo.seckill.event.SeckillActivityCreatedEvent;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityOverlappingException;
import com.kazama.redis_cache_demo.seckill.repository.SeckillActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.ServiceUnavailableException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class SeckillActivityService {

    private final ProductBloomFilterService productBloomFilterService;

    private final SeckillActivityRepository seckillActivityRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final ProductService productService;

    private final ProductRepository productRepository;




    private List<SeckillActivity> buildEntityList(List<CreateSeckillActivityRequest> requests){
        return requests.stream().map(request-> {
            return  SeckillActivity.builder()
                    .productId(request.productId())
                    .seckillPrice(request.seckillPrice())
                    .totalStock(request.totalStock())
                    .remainingStock(request.totalStock())  // 跟 totalStock 相同
                    .startTime(request.dateRange().startTime())
                    .endTime(request.dateRange().endTime())
                    .status(Status.PENDING)
                    .build();
        }).toList();
    }


    private void validateBloomFilter(List<CreateSeckillActivityRequest> requests){
        for(CreateSeckillActivityRequest request : requests){
            if(!productBloomFilterService.mightContain(request.productId())){
                log.info("product does not exists in product bloomfilter");
                throw new ProductNotFoundException(request.productId());
            }
        }
    }

    private void validateStock(List<CreateSeckillActivityRequest> requests , List<Long> productIds ){
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (CreateSeckillActivityRequest request : requests) {
            Product product = productMap.get(request.productId());
            if (product == null) {
                throw new ProductNotFoundException(request.productId());
            }
            if (product.getStock() < request.totalStock()) {
                throw new InsufficientStockException(
                        String.format("Product %s stock %d is less than requested %d",
                                request.productId(), product.getStock(), request.totalStock())
                );
            }
        }
    }

    private void validateInternalOverlap(List<CreateSeckillActivityRequest> requests ){
        Map<Long, List<CreateSeckillActivityRequest>> groupByProduct = requests.stream()
                .collect(Collectors.groupingBy(CreateSeckillActivityRequest::productId));

        groupByProduct.forEach((productId, activities) -> {
            activities.stream()
                    .sorted(Comparator.comparing(r -> r.dateRange().startTime()))
                    .reduce((prev, curr) -> {
                        if (prev.dateRange().endTime().isAfter(curr.dateRange().startTime())) {
                            throw new SeckillActivityOverlappingException(
                                    String.format("Product %s has overlapping activities in request", productId)
                            );
                        }
                        return curr;
                    });
        });
    }

    private void validateExternalOverlap(List<CreateSeckillActivityRequest> requests , List<Long> productIds){
        ZonedDateTime minStart = requests.stream()
                .map(r -> r.dateRange().startTime())
                .min(Comparator.naturalOrder())
                .orElseThrow();

        ZonedDateTime maxEnd = requests.stream()
                .map(r -> r.dateRange().endTime())
                .max(Comparator.naturalOrder())
                .orElseThrow();

        List<SeckillActivity> overlappingActivity = seckillActivityRepository.findOverlappingActivities(productIds, minStart, maxEnd);

        Map<Long, List<SeckillActivity>> existingByProduct = overlappingActivity.stream()
                .collect(Collectors.groupingBy(SeckillActivity::getProductId));

        for (CreateSeckillActivityRequest request : requests) {
            List<SeckillActivity> existing = existingByProduct
                    .getOrDefault(request.productId(), List.of());

            boolean hasOverlap = existing.stream().anyMatch(e ->
                    e.getStartTime().isBefore(request.dateRange().endTime()) &&
                            e.getEndTime().isAfter(request.dateRange().startTime())
            );

            if (hasOverlap) {
                throw new SeckillActivityOverlappingException(
                        String.format("Product %s has overlapping activity", request.productId())
                );
            }
        }
    }



    @Transactional
    public List<SeckillActivityDTO> createActivities(List<CreateSeckillActivityRequest> requests){

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Request list cannot be null or empty");
        }

        log.info("CREATE seckill activities for {} products", requests.size());

        List<Long> productIds = requests.stream()
                .map(CreateSeckillActivityRequest::productId)
                .distinct()
                .toList();

        // Step 1 Bloom filter
        validateBloomFilter(requests);

        // Step 2 validate overlap condition in request itself
        validateInternalOverlap(requests);

        // Step 3  check Product is not out of stock
        validateStock(requests,productIds);

        // Step 4. validate overlap condition in DB
        validateExternalOverlap(requests, productIds);

        // Step 5. buildEntityList
        List<SeckillActivity> seckillActivities = buildEntityList(requests);

        // Step 6 saveAll
        List<SeckillActivity> saved = seckillActivityRepository.saveAll(seckillActivities);

        // Step 7 mark these product as is_Seckill = true
        productService.markAsSeckill(productIds);

        // step 8 publish SeckillActivityCreatedEvent
        saved.forEach(activity ->
                eventPublisher.publishEvent(new SeckillActivityCreatedEvent(
                        activity.getId(),
                        activity.getProductId(),
                        activity.getStartTime(),
                        activity.getEndTime()
                ))
        );

        log.info("CREATE seckill activities success, count: {}", saved.size());

        // Step 9 return
        return saved.stream().map(this::toDTO).toList();

    }




    SeckillActivityDTO toDTO(SeckillActivity activity){

        return new SeckillActivityDTO(
                activity.getId(),
                activity.getProductId(),
                activity.getSeckillPrice(),
                activity.getTotalStock(),
                activity.getRemainingStock(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getStatus());
    }
}
