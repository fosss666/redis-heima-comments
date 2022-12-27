package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 
 */
public interface IBlogService extends IService<Blog> {
    // 修改点赞数量
    Result likeBlog(Long id);
}
