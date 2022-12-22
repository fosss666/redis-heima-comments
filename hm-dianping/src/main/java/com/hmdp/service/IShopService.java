package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 
 */
public interface IShopService extends IService<Shop> {
    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result queryShopById(String id);
    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    Result updateShop(Shop shop);
}
