package com.hmdp.service;

import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 
 */
public interface IShopTypeService extends IService<ShopType> {
    /**
     * 查询菜单类型集合
     */
    List<ShopType> queryTypeList();
}
