package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.annotations.Select;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 */
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

    /**
     * 抢购优惠券
     */
    @Override
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
      /*
        synchronized (userId.toString().intern()) {//为实现一人一单，解决并发安全问题,采用悲观锁
            //为避免同一个类中的方法直接内部调用带事务的方法导致事务失效，采用代理
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, seckillVoucher);
        }
        */

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

    }

    /**
     * 为实现一人一单，解决并发安全问题
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        //判断当前用户是否已经拥有该优惠券，实现一人一单
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, userId);
        Integer count = baseMapper.selectCount(wrapper);
        if (count > 0) {
            return Result.fail("已拥有该优惠券");
        }

        //到这一步说明优惠券有效，减库存，增加优惠券订单
        LambdaUpdateWrapper<SeckillVoucher> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)//乐观锁机制，解决并发安全问题（优惠券售出超出库存）,为提高抢券成功率用stock>0而不是stock=原来的stock
                .set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1);
        seckillVoucherService.update(queryWrapper);

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        long orderId = redisIdWorker.nextId(voucherId.toString());
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setPayTime(LocalDateTime.now());
        baseMapper.insert(voucherOrder);

        //返回订单id
        return Result.ok(orderId);
    }
}



























