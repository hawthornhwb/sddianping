package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 创建类初始化的任务
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new voucherOrderHandler());
    }

    // 基于阻塞队列的异步秒杀
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
//    // 创建线程任务
//    private class voucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1. // 从阻塞队列里取任务
//                    VoucherOrder voucherOrder = orderTasks.take();
//
//                    // 2. 在数据库中完成真正的下单任务
//                    proxy.createVoucherOrder(voucherOrder); // 使用代理对象可以保证事务一致性
//                } catch (InterruptedException e) {
//                    log.info("阻塞队列获取任务异常..");
//                }
//
//            }
//        }
//    }

    // 创建线程任务
    private class voucherOrderHandler implements Runnable {

        private String streamName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 从消息队列里取下单信息
                    List<MapRecord<String, Object, Object>> voucherLists = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofMinutes(2)),
                            StreamOffset.create(streamName, ReadOffset.lastConsumed())
                    );
                    // 2. 消息队列里没消息，则继续监听
                    if (voucherLists == null || voucherLists.isEmpty()) {
                        continue;
                    }
                    // 2.1 消息队列有消息，则获取消息
                    MapRecord<String, Object, Object> voucherList = voucherLists.get(0);
                    Map<Object, Object> voucherEntry = voucherList.getValue();
                    log.info("voucherEntry:{}", voucherEntry);
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(voucherEntry, new VoucherOrder(), true);
                    log.info("voucherOrder:{}", voucherOrder);
                    // 2. 在数据库中完成的下单任务
                    proxy.createVoucherOrder(voucherOrder); // 使用代理对象可以保证事务一致性

                    // 3. 下单成功，向消息代理返回atk，代表消息成功处理
                    stringRedisTemplate.opsForStream().acknowledge(streamName, "g1", voucherList.getId());
                } catch (Exception e) {
                    handleExceptionOrder();
                }
            }
        }

        private void handleExceptionOrder() {
            while (true) {
                try {
                    // 1. 从pending-list里取下单信息
                    List<MapRecord<String, Object, Object>> pendingVoucherLists = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofMinutes(2)),
                            StreamOffset.create(streamName, ReadOffset.from("0")) // 从pending-list中获取信息
                    );

                    // 2. pending-list里没消息，代表所有未ack的消息都处理完了，则返回消息队列中继续处理
                    if (pendingVoucherLists == null || pendingVoucherLists.isEmpty()) {
                        return;
                    }
                    // 2.1 pending-list有消息，则获取消息
                    MapRecord<String, Object, Object> pendingVoucherList = pendingVoucherLists.get(0);
                    Map<Object, Object> voucherEntry = pendingVoucherList.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(voucherEntry, new VoucherOrder(), true);
                    // 2. 在数据库中完成的下单任务
                    proxy.createVoucherOrder(voucherOrder); // 使用代理对象可以保证事务一致性

                    // 3. 下单成功，向消息代理返回atk，代表消息成功处理
                    stringRedisTemplate.opsForStream().acknowledge(streamName, "g1", pendingVoucherList.getId());
                } catch (Exception e) {
                    log.error("pending-list处理异常", e);
                }
            }
        }
    }


//    @Override
//    public Result seckillVoucher(Long id) {
//        // 1. 判断秒杀时间
//        // 1.1 判断秒杀是否开始
//        SeckillVoucher voucher = seckillVoucherService.getById(id);
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀活动还未开始!");
//        }
//        // 1.2 判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀活动已经结束!");
//        }
//
//        // 2. 将创建订单的整个业务进行封装
// //        Long userId = UserHolder.getUser().getId(); // 给userId上锁，保证一人一单
// //        synchronized (userId.toString().intern()) { // 使用字符串常量池中的String对象,可以保证值相同的字符串使用同一个对象
// //            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
// //            return proxy.createVoucherOrder(id, voucher);
// //        }
//        // 2. 上述代码仅在单服务器下实现一人一单，下面实现在集群模式下的一人一单
//        // 2.1 获取锁
//        Long userId = UserHolder.getUser().getId();
// //        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
// //        boolean success = redisLock.tryLock(1200);
//        // 使用redisson实现互斥锁机制
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean success = lock.tryLock();// waitTime: 获取锁时的最大等待时间, leaseTime: 持有锁后过多长时间释放锁；无参: waiteTime:-1 leaseTime: 30s
//        if (!success) {
//            return Result.fail("一人只能下一单，不可重复下单");
//        }
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(id, voucher);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
// //            redisLock.unlock();
//            lock.unlock();
//        }
//    }

    // 秒杀优化
//    @Override
//    public Result seckillVoucher(Long id) {
//        // 1. 判断秒杀时间
//        // 1.1 判断秒杀是否开始
//        SeckillVoucher voucher = seckillVoucherService.getById(id);
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀活动还未开始!");
//        }
//        // 1.2 判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀活动已经结束!");
//        }
//
//        // 2. 判断用户是否具备下单资格
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                List.of(SECKILL_STOCK_KEY + id, SECKILL_VOUCHER_KEY + id),
//                userId.toString()
//        );
//
//        int success = result.intValue(); // 返回的值是Long型，无法与int型比较
//        if (success != 0) {
//            return Result.fail(success == 1 ? "库存不足" : "一人只能下一单");
//        }
//
//        // 将下单信息写入数据库
//        // 1. 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order", 16);
//        voucherOrder.setId(orderId); // offset指的是序列号有几位，这里默认为16位
//        // 创建用户id
//        voucherOrder.setUserId(userId);
//        // 创建订单id
//        voucherOrder.setVoucherId(id);
//
//        // 将订单放入到阻塞队列中
//        proxy = (IVoucherOrderService) AopContext.currentProxy(); // 1. 为了保证下单业务能够成功提交 2. proxy存储在threadLocal中，因为需要将该对象赋值给类的成员变量，这样子线程才可见
//        orderTasks.add(voucherOrder);
//        return Result.ok(orderId);
//
//    }


    @Override
    public Result seckillVoucher(Long id) {
        // 1. 判断秒杀时间(这部分放到前端做)

        // 2. 判断用户是否具备下单资格，并将订单信息加入到消息队列中
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order", 16);
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.singletonList(SECKILL_STOCK_KEY + id),
                id.toString(), userId.toString(), String.valueOf(orderId)
        );

        // 3. 返回给前端的处理结果
        int success = result.intValue(); // 返回的值是Long型，无法与int型比较
        if (success != 0) {
            return Result.fail(success == 1 ? "库存不足" : "一人只能下一单");
        }

        proxy = (IVoucherOrderService) AopContext.currentProxy(); // 1. 为了保证下单业务能够成功提交 2. proxy存储在threadLocal中，因为需要将该对象赋值给类的成员变量，这样子线程才可见
        return Result.ok(orderId);
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

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 这里不使用分布式锁了，因为lua脚本已经判断完了库存超卖和一人一旦问题了，除非lua脚本失败，但可能性很低。
        seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update(); // 解决库存超卖问题
        save(voucherOrder);
    }
}
