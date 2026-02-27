package com.kazama.redis_cache_demo.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(RateLimitExceedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceedException(RateLimitExceedException e){
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),e.getMessage()));
    }


}
