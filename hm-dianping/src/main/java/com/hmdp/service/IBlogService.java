package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {
    /**
     * 修改点赞数量
     */
    Result likeBlog(Long id);

    /**
     * 根据id获取blog
     */
    Result getBlogById(String id);
    /**
     * 分页查询笔记
     * @param current 当前页
     * @return 查询到的分页数据
     */
    Result queryHotBlog(Integer current);
    /**
     * 根据id获取前5个点赞的用户的部分信息
     */
    Result getLikesById(String id);
    /**
     * 分页查询某一用户的博客
     */
    Result getPageOfUserBlogs(Long id, Integer current);
    /**
     * 发布探店笔记
     */
    Result saveBlog(Blog blog);
}
