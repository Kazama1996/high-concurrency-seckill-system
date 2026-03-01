package com.kazama.redis_cache_demo.infra.validation.validator;

import com.kazama.redis_cache_demo.infra.validation.annotation.ValidateDateRange;
import com.kazama.redis_cache_demo.infra.vo.date.DateRangeValidatable;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidateDateRange, DateRangeValidatable> {

    @Override
    public boolean isValid(DateRangeValidatable target, ConstraintValidatorContext constraintValidatorContext) {
        if (target.startTime() == null || target.endTime() == null) return true;
        return target.startTime().isBefore(target.endTime());
    }
}
