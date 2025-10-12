package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long id) {
        // 1. 判断秒杀时间
        // 1.1 判断秒杀是否开始
        SeckillVoucher voucher = seckillVoucherService.getById(id);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动还未开始!");
        }
        // 1.2 判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已经结束!");
        }

        // 2. 将创建订单的整个业务进行封装
        Long userId = UserHolder.getUser().getId(); // 给userId上锁，保证一人一单
        synchronized (userId.toString().intern()) { // 使用字符串常量池中的String对象,可以保证值相同的字符串使用同一个对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(id, voucher);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long id, SeckillVoucher voucher) {
        // 2. 增加一人一单的判断
        // 根据优惠券id和用户id来查询订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("voucher_id", id).eq("user_id", userId).count();
        if (count > 0) {
            // 说明该用户已经下过单了
            return Result.fail("该用户已下过单，不能重复下单!");
        }

        // 3. 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足，无法下单");
        }

        // 4. 库存充足则扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", id).gt("stock", 0).update(); // 解决库存超卖问题
        if (!success) { // 多线程并发运行时，可能会出现不一致的情况.
            return Result.fail("库存不足，无法下单");
        }
        // 5. 创建订单信息并保存到数据库
        // 创建订单id，使用自定义的id生成器
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order", 16);
        voucherOrder.setId(orderId); // offset指的是序列号有几位，这里默认为16位
        // 创建用户id
        voucherOrder.setUserId(userId);
        // 创建订单id
        voucherOrder.setVoucherId(id);
        save(voucherOrder);
        // 返回订单Id
        return Result.ok(orderId);
    }
}
