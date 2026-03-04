local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local expireTime = now - window * 1000
redis.call('ZREMRANGEBYSCORE',key,0,expireTime)
local count = redis.call('ZCARD',key)
if count >= limit then
    return 0
end
redis.call('ZADD',key,now,now)
redis.call('EXPIRE',key,window)
return 1