package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    // 修改点赞数量
    @Override
    public Result likeBlog(Long id) {
        //查询该笔记
        Blog blog = baseMapper.selectById(id);
        //获取用户id
        Long userId = blog.getUserId();
        //这里只实现不能给自己点赞，没有一个账号只点赞一次
        Long currentUserId = UserHolder.getUser().getId();
        if (!userId.equals(currentUserId)) {
            //不是寄几的笔记，进行点赞
            blog.setLiked(blog.getLiked() + 1);
            baseMapper.updateById(blog);
        }
        return Result.ok();
    }
}



















