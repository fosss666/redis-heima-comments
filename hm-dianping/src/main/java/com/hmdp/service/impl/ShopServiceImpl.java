package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopById(String id) {
        //解决缓存穿透
        Shop shop = queryWithWalkThrough(id);

        //解决缓存击穿

        return Result.ok(shop);
    }

    /**
     * 解决缓存穿透的逻辑
     */
    public Shop queryWithWalkThrough(String id){
        String key = CACHE_SHOP_KEY + id;
        //先从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断非空
        if (!StringUtils.isEmpty(shopJson)) {
            //转成对象返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if ("".equals(shopJson)) {//防止缓存击穿
            //数据为空字符串，报错
            return null;
        }

        //缓存中没有数据，则从数据库中查询
        Shop shop = baseMapper.selectById(id);
        //判断非空
        if (shop == null) {
            //向缓存中存储空字符串，目的是解决缓存穿透问题（缓存和数据库中都没有该数据）
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//防止缓存击穿
            return null;
        }
        //放入缓存中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 上锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//做拆箱操作
    }

    /**
     * 解锁
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        baseMapper.updateById(shop);
        //2.删除缓存以保持缓存和数据库数据的一致性
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}




















