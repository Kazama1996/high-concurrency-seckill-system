package com.kazama.redis_cache_demo.infra.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public record BeanValidationErrorResponse(
        int status,
        String timeStamp,
        List<FieldDetail> details

) {
    public record  FieldDetail(String fieldName , String message){}

    public static BeanValidationErrorResponse of(int status , MethodArgumentNotValidException e){

        List<FieldDetail> detailList = e.getFieldErrors().stream().map(error -> {
            return new FieldDetail(error.getField() , error.getDefaultMessage());
        }).toList();

        return new BeanValidationErrorResponse(status  , LocalDateTime.now().toString(), detailList);
    }

    public static BeanValidationErrorResponse of(int status , ConstraintViolationException e){

        List<FieldDetail> detailList = e.getConstraintViolations().stream().map(violation -> {
            String path = violation.getPropertyPath().toString();
            return new FieldDetail(path.substring(path.lastIndexOf('.')+1) ,violation.getMessage());
        }).toList();

        return new BeanValidationErrorResponse(status  , LocalDateTime.now().toString(), detailList);
    }


    public static BeanValidationErrorResponse of(int status , HandlerMethodValidationException e ){
        List<BeanValidationErrorResponse.FieldDetail> details = Stream.concat(e.getBeanResults().stream(), e.getValueResults().stream())
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> {
                            String fieldName = result.getMethodParameter().getParameterName();
                            if (error instanceof FieldError fe) {
                                fieldName = fe.getField();
                            }
                            return new BeanValidationErrorResponse.FieldDetail(fieldName, error.getDefaultMessage());
                        })
                )
                .toList();

        return new BeanValidationErrorResponse(HttpStatus.BAD_REQUEST.value(), LocalDateTime.now().toString(), details);


    }
}
