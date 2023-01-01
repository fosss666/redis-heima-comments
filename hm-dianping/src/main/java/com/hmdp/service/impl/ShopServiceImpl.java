package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisHelper;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //注入封装的工具类
    @Resource
    private RedisHelper redisHelper;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopById(String id) {
        //解决缓存穿透
        //Shop shop = queryWithWalkThrough(id);
        //用封装的工具类
        Shop shop = redisHelper.queryWithWalkThrough(id, CACHE_SHOP_KEY, Shop.class, id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithLock(id);

        //逻辑过期时间解决缓存击穿
        //Shop shop = queryWithExpire(id);
        //用封装的工具类
        //Shop shop = redisHelper.queryWithExpire(id, CACHE_SHOP_KEY, Shop.class, id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);


        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    //独立线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 解决缓存击穿的逻辑(用逻辑过期时间)+缓存穿透
     */
    public Shop queryWithExpire(String id) {
        String key = CACHE_SHOP_KEY + id;
        //先从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否命中
        if (StringUtils.isEmpty(shopJson)) {
            //没有命中
            return null;
        }

        //命中，判断缓存是否过期
        //获取缓存的数据和逻辑过期时间
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Object data = redisData.getData();
        Shop shop = JSONUtil.toBean((JSONObject) data, Shop.class);

        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //没有过期，返回商铺信息
            return shop;
        }

        //过期，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        if (flag) {
            //获取成功，开启独立线程,实现缓存的重建
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                try {
                    saveWithExpire(id, 30L * 60);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //返回过期的商户信息
        return shop;
    }

    /**
     * 解决缓存击穿的逻辑（用互斥锁）+缓存穿透
     */
    public Shop queryWithLock(String id) {
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
        String lockKey = null;
        Shop shop = null;

        try {
            //缓存中没有数据，则从数据库中查询
            //锁的key
            lockKey = LOCK_SHOP_KEY + id;
            boolean flag = tryLock(lockKey);
            //判断加锁是否成功
            if (!flag) {
                //加锁失败,休眠后重试
                Thread.sleep(LOCK_SLEEP_TIME);
                queryWithLock(id);
            }

            //加锁成功，到数据库中查询数据
            shop = baseMapper.selectById(id);
            //判断非空
            if (shop == null) {
                //向缓存中存储空字符串，目的是解决缓存穿透问题（缓存和数据库中都没有该数据）
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//防止缓存击穿
                return null;
            }
            //放入缓存中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //解锁
            unlock(lockKey);
        }

        return shop;
    }

    /**
     * 解决缓存穿透的逻辑
     */
    /*public Shop queryWithWalkThrough(String id) {
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
*/

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
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 将数据用逻辑过期时间存入redis
     */
    public void saveWithExpire(String id, Long time) {
        //从数据库查询数据
        Shop shop = baseMapper.selectById(id);
        //封装对象
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        redisData.setData(shop);
        //存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
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

    /**
     * 根据商铺类型分页查询商铺信息
     *
     * @param typeId  商铺类型
     * @param current 页码
     * @param x       经度
     * @param y       纬度
     * @return 商铺列表
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是不是根据地理位置查询
        if (x == null || y == null) {
            //不是，则进行一般的分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        //是
        //设置分页参数
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_BATCH_SIZE;
        //设置查询key
        String key = SHOP_GEO_KEY + typeId;
        //从redis中查询 geosearch key fromlonlat x y byradius 5000m withdist
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        //健壮性判断
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        //获取数据
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //判断大小
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        //存储店铺id
        List<String> shopIds = new ArrayList<>(list.size());
        //存储店铺距离
        Map<String, Distance> shopsDistance = new HashMap<>(list.size());
        //截取from~end的商户
        list.stream().skip(from).forEach(shop -> {
            //获取店铺id
            String shopId = shop.getContent().getName();
            shopIds.add(shopId);
            //获取店铺距离
            Distance distance = shop.getDistance();
            shopsDistance.put(shopId, distance);
        });
        //拼接店铺id字符串
        String shopIdsStr = StrUtil.join(",", shopIds);
        //根据店铺id查询所有店铺
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Shop::getId, shopIds).last("order by field (id," + shopIdsStr + ")");
        List<Shop> shops = baseMapper.selectList(wrapper);
        //设置店铺的距离属性
        shops.forEach(shop -> {
            //System.err.println(shop);
            //System.err.println(shop.getId());
            shop.setDistance(shopsDistance.get(shop.getId().toString()).getValue());
        });
        //返回数据
        return Result.ok(shops);
    }
}




















