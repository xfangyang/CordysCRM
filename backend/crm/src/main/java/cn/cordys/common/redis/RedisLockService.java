package cn.cordys.common.redis;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁服务
 *
 * @author system
 * @date 2025-12-25
 */
@Service
public class RedisLockService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 默认锁过期时间（秒）
     */
    private static final long DEFAULT_EXPIRE_TIME = 30;

    /**
     * 尝试获取锁
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的value（通常是请求唯一标识）
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, String lockValue) {
        return tryLock(lockKey, lockValue, DEFAULT_EXPIRE_TIME);
    }

    /**
     * 尝试获取锁
     *
     * @param lockKey     锁的key
     * @param lockValue   锁的value（通常是请求唯一标识）
     * @param expireTime  锁过期时间（秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, String lockValue, long expireTime) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放锁
     * 使用 Lua 脚本确保只有锁的持有者才能释放锁
     *
     * @param lockKey   锁的key
     * @param lockValue 锁的value
     */
    public void unlock(String lockKey, String lockValue) {
        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else " +
                        "return 0 " +
                        "end";
        redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                        luaScript, Long.class
                ),
                java.util.Collections.singletonList(lockKey),
                lockValue
        );
    }

    /**
     * 带超时等待的获取锁
     *
     * @param lockKey    锁的key
     * @param lockValue  锁的value
     * @param waitTime   等待获取锁的时间（毫秒）
     * @return 是否获取成功
     */
    public boolean tryLockWithWait(String lockKey, String lockValue, long waitTime) {
        return tryLockWithWait(lockKey, lockValue, DEFAULT_EXPIRE_TIME, waitTime);
    }

    /**
     * 带超时等待的获取锁
     *
     * @param lockKey     锁的key
     * @param lockValue   锁的value
     * @param expireTime  锁过期时间（秒）
     * @param waitTime    等待获取锁的时间（毫秒）
     * @return 是否获取成功
     */
    public boolean tryLockWithWait(String lockKey, String lockValue, long expireTime, long waitTime) {
        long startTime = System.currentTimeMillis();
        long timeout = startTime + waitTime;

        while (System.currentTimeMillis() < timeout) {
            if (tryLock(lockKey, lockValue, expireTime)) {
                return true;
            }
            try {
                Thread.sleep(50); // 每50ms重试一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
