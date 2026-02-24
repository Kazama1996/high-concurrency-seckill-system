package com.kazama.redis_cache_demo.infra.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        int status,
        String message,
        String timeStamp
) {

    public static ErrorResponse of(int status , String message){
        return new ErrorResponse(status , message , LocalDateTime.now().toString());
    }
}
