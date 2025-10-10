package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        // 1. 获取商铺id
        String key = CACHE_SHOP_KEY + id;
        // 2. 从Redis中查找商铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopStr)) {
            // 3.1 Redis命中，将String类型的Json对象反序列化成Shop对象，并返回
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return Result.ok(shop);
        }
        // 3.2 Redis未命中，则从数据库中查出该商铺信息。
        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("商户信息不存在");
        }

        // 3.3 将商铺信息加入Redis中，便于下次查找。
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        // 4. 返回商铺信息。
        return Result.ok(shop);




    }
}
