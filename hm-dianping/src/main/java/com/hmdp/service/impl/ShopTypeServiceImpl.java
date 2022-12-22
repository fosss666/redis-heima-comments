package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_TTL;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询菜单类型集合
     */
    @Override
    public List<ShopType> queryTypeList() {
        //查询cache中是否有缓存
        String shopTypeJSON = stringRedisTemplate.opsForValue().get(SHOP_TYPE_KEY);
        if(!StringUtil.isNullOrEmpty(shopTypeJSON)){
            //缓存中有数据，转为对象返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJSON, ShopType.class);
            return shopTypes;

        }

        //没有的话从数据库查询，并放入cache
        LambdaQueryWrapper<ShopType> wrapper=new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ShopType::getSort);
        List<ShopType> shopTypes = baseMapper.selectList(wrapper);
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopTypes),SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return shopTypes;
    }
}





















