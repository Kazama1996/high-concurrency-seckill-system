package com.kazama.redis_cache_demo.infra.cache;

public record CacheResult<T> (T value , Status status) {

    public static <T> CacheResult<T> hit(T value){
        return new CacheResult<>(value , Status.HIT);
    }

    public static <T> CacheResult<T> nullHit(){
        return new CacheResult<>(null , Status.NULL_HIT);
    }

    public static <T> CacheResult<T> miss(){
        return new CacheResult<>(null , Status.MISS);
    }
}
