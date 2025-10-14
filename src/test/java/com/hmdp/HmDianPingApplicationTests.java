package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    private final static Long TOTAL_SHOP_NUMBER = 15L;

    @Test
    public void savaShopToRedis() throws InterruptedException {
        for (Long i = 0L; i < TOTAL_SHOP_NUMBER; i++) shopService.saveHotShopToRedis(i, 10L); // 进行缓存预热
    }
}
