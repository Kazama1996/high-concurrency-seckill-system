package com.kazama.redis_cache_demo.infra.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(RateLimitExceedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceedException(RateLimitExceedException e){
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BeanValidationErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BeanValidationErrorResponse.of(HttpStatus.BAD_REQUEST.value(),e));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BeanValidationErrorResponse> handleConstraintViolationException(ConstraintViolationException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BeanValidationErrorResponse.of(HttpStatus.BAD_REQUEST.value(),e));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BeanValidationErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BeanValidationErrorResponse.of(HttpStatus.BAD_REQUEST.value() , e));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }


}
