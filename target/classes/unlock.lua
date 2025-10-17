-- 执行释放锁的动作时，由于java代码中执行不能保证原子性，因而使用lua脚本来保证原子性

--1. 获取当前线程标识
local threadId = ARGV[1]
--2. 获取redis中存储的锁标识
local key = KEYS[1]
local cacheId = redis.call('get', key)
--3. 比较锁标识与当前线程标识，如果一致，则释放锁
if cacheId == threadId then
    return redis.call('del', key)
end
--4. 如果锁标识与当前线程标识不一致，则不释放锁
return 0