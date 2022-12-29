package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 修改点赞数量
     */
    @Override
    public Result likeBlog(Long id) {
        String key = BLOG_LIKED_KEY + id;
        String value = UserHolder.getUser().getId().toString();

        //判断当前用户是否已经给该博客点过赞
        //Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, value);
        Double score = stringRedisTemplate.opsForZSet().score(key, value);
        //获取当前博客
        Blog blog = baseMapper.selectById(id);

        //没有点赞，则点赞数+1
        //if (BooleanUtil.isFalse(isLiked)) {
        if (score == null) {
            LambdaUpdateWrapper<Blog> wrapper = new LambdaUpdateWrapper<>();
            wrapper.setSql("liked = liked +1").eq(Blog::getId, id);
            int count = baseMapper.update(blog, wrapper);
            //修改成功的话，将点赞信息存入redis的set集合
            if (count > 0) {
                //stringRedisTemplate.opsForSet().add(key, value);
                //实现点赞顺序的记录
                stringRedisTemplate.opsForZSet().add(key, value, System.currentTimeMillis());
            }
        } else {
            //已经点赞，点赞数-1，相当于取消点赞
            LambdaUpdateWrapper<Blog> wrapper = new LambdaUpdateWrapper<>();
            wrapper.setSql("liked = liked -1").eq(Blog::getId, id);
            int count = baseMapper.update(blog, wrapper);
            //修改成功的话，将redis中的点赞信息删除
            if (count > 0) {
                //stringRedisTemplate.opsForSet().remove(key, value);
                stringRedisTemplate.opsForZSet().remove(key, value);
            }
        }
        return Result.ok();
    }

    /**
     * 根据id获取blog
     */
    @Override
    public Result getBlogById(String id) {
        //查询并封装blog
        Blog blog = baseMapper.selectById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        Blog blog1 = getBlog(blog);
        return Result.ok(blog1);
    }

    /**
     * 分页查询笔记
     *
     * @param current 当前页
     * @return 查询到的分页数据
     */
    @Override
    public Result queryHotBlog(Integer current) {
        //分页查询
        Page<Blog> page = new Page<>(current, MAX_PAGE_SIZE);
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Blog::getLiked);
        Page<Blog> blogPage = baseMapper.selectPage(page, wrapper);
        //获取当前页数据
        List<Blog> records = blogPage.getRecords();

        // 查询用户
        List<Blog> resultList = new ArrayList<>();
        for (Blog blog : records) {
            resultList.add(getBlog(blog));
        }
        return Result.ok(resultList);
    }

    //查询并封装blog
    private Blog getBlog(Blog blog) {
        //获取用户昵称头像id
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        //设置姓名头像
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        //设置是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        //String value = UserHolder.getUser().getId().toString(); 不登录的话就会报错空指针
        //优化
        UserDTO user1 = UserHolder.getUser();
        if (user1 != null) {
            String value = user1.getId().toString();
            //Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, value);
            Double isLiked = stringRedisTemplate.opsForZSet().score(key, value);
            blog.setIsLike(isLiked != null);
        }

        return blog;
    }


    /**
     * 根据id获取前5个点赞的用户的部分信息
     */
    @Override
    public Result getLikesById(String id) {
        String key = BLOG_LIKED_KEY + id;
        //从sortedSet集合中获取前5个点赞的用户
        Set<String> userSet = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (userSet == null || userSet.isEmpty()) {
            return Result.ok();
        }
        //获取user
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        String userIdsStr = StrUtil.join(",", userSet);
        //  ... where id in (5,1) order by field (id,5,1)
        wrapper.in(User::getId, userSet).last("order by field (id," + userIdsStr + ")");
        List<User> userList = userService.list(wrapper);

        //转成userDto
        List<UserDTO> userDtoList = new ArrayList<>();
        for (User user : userList) {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setNickName(user.getNickName());
            userDTO.setIcon(user.getIcon());
            userDtoList.add(userDTO);
        }
        return Result.ok(userDtoList);
    }

    /**
     * 分页查询某一用户的博客
     */
    @Override
    public Result getPageOfUserBlogs(Long id, Integer current) {
        IPage<Blog> page = new Page<>(current, MAX_PAGE_SIZE);
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Blog::getUserId, id);
        IPage<Blog> blogs = baseMapper.selectPage(page, wrapper);
        List<Blog> records = blogs.getRecords();

        return Result.ok(records == null ? Collections.emptyList() : records);
    }

}



















