package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 创建包含10个线程的线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryShopById(Long id) {

//        Shop shop = queryWithPassThrough(id);
//        Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("商户信息不存在");
        }
        // 4. 返回商铺信息。
        return Result.ok(shop);

    }



    // 该方法实现了缓存穿透，使用的是缓存空值的办法
    public Shop queryWithPassThrough(Long id) {
        // 1. 获取商铺id
        String key = CACHE_SHOP_KEY + id;
        // 2. 从Redis中查找商铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopStr)) {
            // 3.1 Redis命中，将String类型的Json对象反序列化成Shop对象，并返回
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return shop;
        }

        // shopStr == "" 时， 说明该id在数据库中不存在，且这个id之前已经被用户访问过缓存了空对象。
        if (shopStr != null) {
            return null;
        }
        // shopStr == null 时，说明该id在缓存中时不存在且不为空对象，此时重建缓存。
        // 3.2 Redis未命中，则从数据库中查出该商铺信息。
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TIME, TimeUnit.MINUTES); // 当请求的商铺id不存在时，缓存空对象
            return null;
        }

        // 3.3 将商铺信息加入Redis中，便于下次查找。
        // 缓存一致性：增加超时剔除机制
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TIME, TimeUnit.MINUTES);
        return shop;
    }

    // 基于互斥锁的方式解决热点key问题。
    public Shop queryWithMutex(Long id) {
        // 1. 获取商铺id
        String key = CACHE_SHOP_KEY + id;
        // 2. 从Redis中查找商铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopStr)) {
            // 3.1 Redis命中，将String类型的Json对象反序列化成Shop对象，并返回
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return shop;
        }

        // shopStr == "" 时， 说明该id在数据库中不存在，且这个id之前已经被用户访问过缓存了空对象。
        if (shopStr != null) {
            return null;
        }


        // shopStr == null 时，说明该id在缓存中时不存在且不为空对象，此时重建缓存。
        // 基于互斥锁的方法解决热点key问题
        // 3.2 Redis未命中，则从数据库中查出该商铺信息。
        Shop shop = null;
        try {
            // 4.1 尝试获取互斥锁
            boolean lock = tryLock(id);
            // 4.2 判断是否获取锁成功
            if (!lock) {
                // 4.3 获取锁失败，进行休眠等待，并重新再请求锁
                Thread.sleep(20); // 休眠等待
                return queryWithMutex(id); // 休眠后尝试再次获取锁
            }
            // 4.4 获取锁成功，则进行缓存重建
            shop = getById(id);
            Thread.sleep(200); // 因为数据库在本地，故休眠200ms 表示在进行缓存重建
            // 4.5 将商铺信息加入Redis中，便于下次查找。
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TIME, TimeUnit.MINUTES); // 当请求的商铺id不存在时，缓存空对象
                return null;
            }

            // 缓存一致性：增加超时剔除机制
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TIME, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e); // 运行时出现问题就简单抛个异常。
        } finally {
            // 释放锁, 不管 try 的运行是否成功，都要释放锁。
            unlock(id);
        }
        return shop;
    }

    // 基于逻辑过期的方式解决热点key问题。
    public Shop queryWithLogicExpire(Long id) {
        // 1. 获取商铺id
        String key = CACHE_SHOP_KEY + id;
        // 2. 从Redis中查找商铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopStr)) {
            // 3.1 Redis未命中，返回空
            return null;
        }

        // 3. 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
        JSONObject shopData = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopData, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.1 缓存未过期，此时直接返回的商铺信息，无需进行缓存重建
            return shop;
        }

        // 4. 缓存过期，此时需要进行缓存重建
        // 4.1 尝试获取互斥锁
        boolean lock = tryLock(id);
        if (lock) {
            // 4.2 获取互斥锁成功, 开启一个新线程执行缓存重建任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveHotShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(id);
                }
            });
        }
        // 返回商铺信息，缓存重建成功前返回的时旧的商铺信息，缓冲重建成功后返回的是新的商铺信息。
        return shop;
    }

    public boolean tryLock(Long id) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(CACHE_LOCK_KEY + id, "1", CACHE_LOCK_TIME, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(lock); // 只有当返回值为 true 时才返回true
    }

    public void unlock(Long id) {
        stringRedisTemplate.delete(CACHE_LOCK_KEY + id);
    }

    // 对热点key进行缓存预热, expireTime由调用者确定
    public void saveHotShopToRedis(Long id, Long expireTime) throws InterruptedException {
        // 1. 查询商铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData shopWithExpireTime = new RedisData();
        shopWithExpireTime.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        shopWithExpireTime.setData(shop);
        // 3. 将数据存入Redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopWithExpireTime));
    }

    @Override
    public Result update(Shop shop) {
        // 采用主动更新策略实现缓存同步
        // 采用先更新数据库，再删除缓存的步骤
        Long id = shop.getId();

        // 避免管理员在操作的时候可能会删除一个不存在的商户id。
        if (id == null) {
            return  Result.fail("商户id不存在！");
        }
        updateById(shop); // 更新数据库

        // 在更新数据库的时候，该商户的缓存已经被超时剔除了，此时无需删除缓存
//        if (shop == null) {
//            return Result.ok();
//        }

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
