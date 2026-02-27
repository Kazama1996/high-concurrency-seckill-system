package com.kazama.redis_cache_demo.infra.ratelimit;

import com.kazama.redis_cache_demo.infra.exception.RateLimitExceedException;
import com.kazama.redis_cache_demo.infra.ratelimit.impl.SlidingWindowRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private final ExpressionParser parser = new SpelExpressionParser();

    private final Map<RateLimitType , RateLimiter> rateLimiters;

    public RateLimitAspect(List<RateLimiter> rateLimiters){
        this.rateLimiters = rateLimiters.stream()
                .collect(Collectors.toMap(RateLimiter::supportedType, r->r ,(existing,duplicate)->{
                    throw new IllegalStateException("Duplicate RateLimiter for type: " + existing.supportedType());
                }));
    }


    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

        String resolvedKey = resolveKey(joinPoint , rateLimit.key());

        String redisKey = "ratelimit:"+resolvedKey;


        RateLimiter rateLimiter = rateLimiters.get(rateLimit.type());

        if(rateLimiter==null){
            throw new IllegalArgumentException("Unsupported rate limit type: " + rateLimit.type());
        }

        boolean allowed = rateLimiter.isAllowed(redisKey, rateLimit.limit(), rateLimit.window());

        if(!allowed){
            log.warn("Rate limit exceeded for key: {}", redisKey);
            throw new RateLimitExceedException("Too many requests: " + resolvedKey);
        }




        return joinPoint.proceed();
    }


    private String resolveKey (ProceedingJoinPoint joinPoint , String keyExpression){

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        String[] paramNames= signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();

        for(int i=0 ; i< paramNames.length;i++){
            context.setVariable(paramNames[i],args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context,String.class);


    }

}
