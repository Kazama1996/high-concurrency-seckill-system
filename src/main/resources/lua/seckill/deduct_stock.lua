local stock = tonumber(redis.call('GET',KEYS[1]))

if stock == nil then
    return 0-1
end

if stock <= 0 then
    return 0
end

local successOrder = redis.call('SADD' , KEYS[2] , ARGV[1])

if successOrder ~=1 then
    return 0-2
end

local remaining = redis.call('DECR', KEYS[1])

return tonumber(remaining)
