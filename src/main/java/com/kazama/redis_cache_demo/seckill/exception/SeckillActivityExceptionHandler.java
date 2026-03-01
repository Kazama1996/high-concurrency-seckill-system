package com.kazama.redis_cache_demo.seckill.exception;

import com.kazama.redis_cache_demo.infra.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SeckillActivityExceptionHandler {

    @ExceptionHandler(SeckillActivityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSeckillActivityNotFoundException(SeckillActivityNotFoundException e){

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value() , e.getMessage()));
    }

    @ExceptionHandler(SeckillActivityOverlappingException.class)
    public ResponseEntity<ErrorResponse> handleSeckillActivityOverlappingException(SeckillActivityOverlappingException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value() , e.getMessage()));
    }
}
