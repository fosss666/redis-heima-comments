package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
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

    //用于调用lua脚本
    private static final DefaultRedisScript<Long> SECKILL_ORDER;

    static {
        //用静态代码块对redis_script进行初始化
        SECKILL_ORDER = new DefaultRedisScript<>();
        SECKILL_ORDER.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_ORDER.setResultType(Long.class);
    }

    //创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //创建线程池
    private static final ExecutorService VOUCHER_ORDER_HANDLER = Executors.newSingleThreadExecutor();

    /**
     * 3-类初始化后执行该方法
     */

    @PostConstruct
    private void init() {
        //执行内部类中的方法
        VOUCHER_ORDER_HANDLER.execute(new VoucherOrderHandler());
    }

    /**
     * 2-创建内部类
     */
    //从消息队列获取订单
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            String key = "stream.orders";
            String group = "g1";

            while (true) {
                try {

                    //(1)不断从队列中获取订单
                    // "XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >"
                    List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(group, "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(key, ReadOffset.lastConsumed())
                    );
                    // 判断订单集合是否为空
                    if (orderList.isEmpty() || orderList == null) {
                        //订单为空，进入下一轮
                        continue;
                    }

                    //(2)解析订单集合，创建订单
                    MapRecord<String, Object, Object> order = orderList.get(0);
                    Map<Object, Object> value = order.getValue();
                    //将MapRecord转为订单对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //创建订单
                    handlerOrder(voucherOrder);
                    //到此处没有抛异常的话就确认XACK
                    stringRedisTemplate.opsForStream().acknowledge(key, group, order.getId());
                } catch (Exception e) {
                    log.error("订单异常", e);
                    //抛异常，就从pending_list中获取未被确认（出现异常）的订单，重新提交
                    pendingListHandler();
                }
            }
        }

        /**
         * 抛异常，就从pending_list中获取未被确认（出现异常）的订单，重新提交
         */
        private void pendingListHandler() {
            String key = "stream.orders";
            String group = "g1";

            while (true) {
                try {

                    //(1)不断从pending_list中获取订单
                    // "XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >"
                    List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(group, "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(key, ReadOffset.from("0"))
                    );
                    // 判断订单集合是否为空
                    if (orderList.isEmpty() || orderList == null) {
                        //pending_list为空，跳出循环
                        break;
                    }

                    //(2)解析订单集合，创建订单
                    MapRecord<String, Object, Object> order = orderList.get(0);
                    Map<Object, Object> value = order.getValue();
                    //将MapRecord转为订单对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //创建订单
                    handlerOrder(voucherOrder);
                    //到此处没有抛异常的话就确认XACK
                    stringRedisTemplate.opsForStream().acknowledge(key, group, order.getId());
                } catch (Exception e) {
                    log.error("pending_list异常", e);
                    //抛异常,休眠后进入下一轮循环
                    try {
                        Thread.sleep(20);
                        continue;//加不加continue效果一样
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    //从阻塞队列获取订单
    /*private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            try {
                //(1)不断从队列中获取订单
                while (true) {
                    VoucherOrder voucherOrder = orderTasks.take();
                    //(2)创建订单
                    handlerOrder(voucherOrder);
                }
            } catch (InterruptedException e) {
                log.error("订单异常", e);
            }
        }
    }*/

    /**
     * (2)创建订单
     */
    private void handlerOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //采用Redisson
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean flag = lock.tryLock();

        //判断是否上锁成功
        if (!flag) {
            //没有成功
            log.error("不能重复抢购");
            return;
        }

        try {
            //此时是子线程，不能采用该方法获取代理对象 IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //应从主线程中获取
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            //simpleRedisLock.unlock();
            lock.unlock();
        }
    }

    /**
     * 1-抢购优惠券
     */

    private IVoucherOrderService proxy;

    //基于lua脚本编写的异步下单-redis的stream消息队列
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId(voucherId.toString());
        //执行lua脚本进行能否下单的判断和把订单信息存入redis的消息队列两个操作
        Long result = stringRedisTemplate.execute(
                SECKILL_ORDER,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        //判断返回结果
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            //下单失败
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        //获取代理对象，便于handlerOrder使用
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }


    //基于lua脚本编写的异步下单-阻塞队列
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_ORDER,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString()
        );
        //判断返回结果
        int r = result.intValue();
        if (r != 0) {
            //下单失败
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        //基于阻塞队列实现秒杀下单
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        long orderId = redisIdWorker.nextId(voucherId.toString());
        voucherOrder.setId(orderId);
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        voucherOrder.setPayTime(LocalDateTime.now());
        //将订单存入阻塞队列
        orderTasks.add(voucherOrder);

        //获取代理对象，便于handlerOrder使用
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(0);
    }
*/

    //基于java代码编写的同步下单
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //查询该优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断该优惠券是否在指定日期内
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (LocalDateTime.now().isBefore(beginTime)) {
            return Result.fail("秒杀未开始！");
        }
        if (LocalDateTime.now().isAfter(endTime)) {
            return Result.fail("秒杀已结束！");
        }

        //判断优惠券库存是否足够
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("优惠券已抢光");
        }

        Long userId = UserHolder.getUser().getId();

        //synchronized (userId.toString().intern()) {//为实现一人一单，解决并发安全问题,采用悲观锁
        //    //为避免同一个类中的方法直接内部调用带事务的方法导致事务失效，采用代理
        //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //    return proxy.createVoucherOrder(voucherId, seckillVoucher);
        //}

        //采用自定义redis锁
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + UserHolder.getUser().getId());
        //boolean flag = simpleRedisLock.tryLock(1200L);

        //采用Redisson
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean flag = lock.tryLock();

        //判断是否上锁成功
        if (!flag) {
            //没有成功
            return Result.fail("不能重复抢购");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, seckillVoucher);
        } finally {
            //释放锁
            //simpleRedisLock.unlock();
            lock.unlock();
        }

    }*/

    /**
     * 为实现一人一单，解决并发安全问题
     */
    @Transactional
    //public Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) { //同步过程所需
    //异步过程所需
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //判断当前用户是否已经拥有该优惠券，实现一人一单
        //Long userId = UserHolder.getUser().getId(); 不能获得
        Long userId = voucherOrder.getUserId();
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, userId);
        Integer count = baseMapper.selectCount(wrapper);
        if (count > 0) {
            //return Result.fail("已拥有该优惠券");
            log.error("已拥有该优惠券");
            return;
        }

        //获取秒杀优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherOrder.getVoucherId());
        //到这一步说明优惠券有效，减库存，增加优惠券订单
        LambdaUpdateWrapper<SeckillVoucher> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                .gt(SeckillVoucher::getStock, 0)//乐观锁机制，解决并发安全问题（优惠券售出超出库存）,为提高抢券成功率用stock>0而不是stock=原来的stock
                .set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1);
        seckillVoucherService.update(queryWrapper);

        //VoucherOrder voucherOrder = new VoucherOrder();
        //voucherOrder.setVoucherId(voucherId);
        //long orderId = redisIdWorker.nextId(voucherId.toString());
        //voucherOrder.setId(orderId);
        //voucherOrder.setUserId(userId);
        //voucherOrder.setPayTime(LocalDateTime.now());
        baseMapper.insert(voucherOrder);

        //返回订单id
        //return Result.ok(orderId);
    }
}



























