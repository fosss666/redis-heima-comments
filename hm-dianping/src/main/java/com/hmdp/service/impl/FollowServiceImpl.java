package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    /**
     * 关注取关
     *
     * @param id       被关注者的id
     * @param isFollow true 关注  false 取关
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //判断应关注还是取关
        if (isFollow) {
            //关注，添加follow
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            baseMapper.insert(follow);
        } else {
            //取关，删除follow
            LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id);
            baseMapper.delete(wrapper);
        }
        return Result.ok();
    }

    /**
     * 是否关注
     */
    @Override
    public Result isFollow(String id) {
        return null;
    }
}
