package com.kazama.redis_cache_demo.infra.schedule.job;

import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.enums.Status;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.repository.SeckillActivityRepository;
import com.kazama.redis_cache_demo.seckill.service.SeckillActivityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@RequiredArgsConstructor
public class SeckillCacheWarmingJob implements Job {


    private final SeckillActivityRepository seckillActivityRepository;

    private final SeckillActivityCacheService seckillActivityCacheService;



    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        Long activityId = null;

        try {
            activityId =  jobExecutionContext.getJobDetail().getJobDataMap().getLong("activityId");
            final Long finalActivityId = activityId;

            log.info("Cache warming start for activityId: {}", activityId);

            SeckillActivityDTO seckillActivityDTO = seckillActivityRepository
                    .findSeckillActivityById(activityId)
                    .orElseThrow(() -> new SeckillActivityNotFoundException("seckill activity is not found " + finalActivityId));

            if (seckillActivityDTO.status() == Status.CANCELLED) {
                log.warn("Activity {} is cancelled, skip cache warming", activityId);
                return;
            }

            long ttl = ChronoUnit.SECONDS.between(ZonedDateTime.now(), seckillActivityDTO.endTime());

            if (ttl <= 0) {
                log.warn("Activity {} already ended, skip cache warming", activityId);
                return;
            }



            // activity cache service
            seckillActivityCacheService.setActivity(seckillActivityDTO.id(), seckillActivityDTO, ttl);
            seckillActivityCacheService.setStock(seckillActivityDTO.id(), seckillActivityDTO.totalStock(), ttl);
            log.info("Cache warming success for activityId: {}, ttl: {}s", activityId, ttl);
        }catch(Exception e){
            log.error("Cache warming failed for activityId: {}", activityId, e);
            throw new JobExecutionException(e);
        }
    }


}
