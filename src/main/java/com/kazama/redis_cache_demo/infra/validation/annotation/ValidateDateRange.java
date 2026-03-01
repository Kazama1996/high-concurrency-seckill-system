package com.kazama.redis_cache_demo.infra.validation.annotation;

import com.kazama.redis_cache_demo.infra.validation.validator.DateRangeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidateDateRange {

    String message() default "startTime must be before endTime";
    Class<?>[] groups() default {};
    Class<?extends Payload>[] payload() default {};
}
