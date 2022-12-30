package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testInsert() {
        shopService.saveWithExpire("10", 10L);
    }

    /**
     * 导入店铺数据到geo
     */
    @Test
    void loadShopData() {
        //获取所有的店铺
        List<Shop> shopList = shopService.list();
        //将店铺根据typeId分类
        Map<Long, List<Shop>> shopMap = shopList.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        //根据typeId存储到geo
        for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
            //获取typeId
            Long typeId = entry.getKey();
            //获取店铺集合
            List<Shop> shops = entry.getValue();
            //设置key
            String key = SHOP_GEO_KEY + typeId;
            //存储到geo
            //======方式一=======
           /* for (Shop shop : shops) {
                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
            }*/

            //======方式二========= 减少连接redis耗费性能
            List<RedisGeoCommands.GeoLocation<String>> list = new ArrayList<>(shops.size());
            //将shops转为list
            for (Shop shop : shops) {
                list.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, list);
        }
    }
}






















