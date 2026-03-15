local stock = tonumber(redis.call('GET',KEYS[1]))

if stock == nil then
    return -1
end

local quantity = tonumber(ARGV[2])

if stock <  quantity then
    return 0
end

local successOrder = redis.call('SADD' , KEYS[2] , ARGV[1])

if successOrder ~=1 then
    return -2
end

local remaining = redis.call('DECRBY', KEYS[1] , quantity)

return tonumber(remaining)
