package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.FOLLOW_AND_ISFOLLOW;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

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
        String key = FOLLOW_AND_ISFOLLOW + userId;
        //判断应关注还是取关
        if (isFollow) {
            //关注，添加follow
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            int insert = baseMapper.insert(follow);
            if (insert > 0) {
                //添加成功，将信息存到redis，便于取得和其他用户的共同关注
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            //取关，删除follow
            LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id);
            int delete = baseMapper.delete(wrapper);
            if (delete > 0) {
                //删除成功，从redis中删除该信息
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 是否关注
     * @param id  被关注者的id
     */
    @Override
    public Result isFollow(String id) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getUserId, UserHolder.getUser().getId()).eq(Follow::getFollowUserId, id);
        Integer count = baseMapper.selectCount(wrapper);
        return Result.ok(count > 0);
    }

    /**
     * 获取共同关注
     */
    @Override
    public Result togetherFollow(Long id) {
        //当前用户key
        String currentUserKey = FOLLOW_AND_ISFOLLOW + UserHolder.getUser().getId();

        //被关注者key
        String userKey = FOLLOW_AND_ISFOLLOW + id;

        //从redis中查询共同关注
        Set<String> followsIds = stringRedisTemplate.opsForSet().intersect(currentUserKey, userKey);
        if (followsIds == null || followsIds.isEmpty()) {
            //没有共同关注，返回空集合
            return Result.ok(Collections.emptySet());
        }

        //查询所有的共同关注
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, followsIds);
        List<User> userList = userService.list(wrapper);
        //转成userDto，从而过滤敏感信息
        List<UserDTO> userDtoList = new ArrayList<>();
        for (User user : userList) {
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            userDtoList.add(userDTO);
        }

        //返回查询到的共同关注者集合
        return Result.ok(userDtoList);
    }
}
















