package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 
 */
public interface IFollowService extends IService<Follow> {
    /**
     * 关注取关
     * @param id 被关注者的id
     * @param isFollow true 关注  false 取关
     */
    Result follow(Long id, Boolean isFollow);
    /**
     * 是否关注
     */
    Result isFollow(String id);
    /**
     * 获取共同关注
     */
    Result togetherFollow(Long id);
}
