package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

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
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, value);
        //获取当前博客
        Blog blog = baseMapper.selectById(id);
        //没有点赞，则点赞数+1
        if (BooleanUtil.isFalse(isLiked)) {
            LambdaUpdateWrapper<Blog> wrapper = new LambdaUpdateWrapper<>();
            wrapper.setSql("liked = liked +1").eq(Blog::getId, id);
            int count = baseMapper.update(blog, wrapper);
            //修改成功的话，将点赞信息存入redis的set集合
            if (count > 0) {
                stringRedisTemplate.opsForSet().add(key, value);
            }
        } else {
            //已经点赞，点赞数-1，相当于取消点赞
            LambdaUpdateWrapper<Blog> wrapper = new LambdaUpdateWrapper<>();
            wrapper.setSql("liked = liked -1").eq(Blog::getId, id);
            int count = baseMapper.update(blog, wrapper);
            //修改成功的话，将redis中的点赞信息删除
            if (count > 0) {
                stringRedisTemplate.opsForSet().remove(key, value);
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
        blog = getBlog(blog);
        return Result.ok(blog);
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
        for (Blog blog : records) {
            blog = getBlog(blog);
        }
        return Result.ok(records);
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
        String value = UserHolder.getUser().getId().toString();
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, value);
        blog.setIsLike(isLiked);
        return blog;
    }

}



















