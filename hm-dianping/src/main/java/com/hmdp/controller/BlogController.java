package com.hmdp.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 根据id获取blog
     */
    @GetMapping("{id}")
    public Result getBlogById(@PathVariable String id) {
        return blogService.getBlogById(id);
    }

    /**
     * 发布探店笔记
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 根据id获取前5个点赞的用户的部分信息
     */
    @GetMapping("/likes/{id}")
    public Result getLikesById(@PathVariable String id) {
        return blogService.getLikesById(id);
    }

    /**
     * 给探店笔记点赞
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likeBlog(id);
    }

    /**
     * 分页查询当前用户的blog
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 分页查询笔记
     *
     * @param current 当前页
     * @return 查询到的分页数据
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }

    /**
     * 分页查询某一用户的博客
     */
    @GetMapping("/of/user")
    public Result getPageOfUserBlogs(@RequestParam("id") Long id, @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.getPageOfUserBlogs(id, current);
    }

    /**
     * 滚动分页查询关注的用户的笔记
     */
    @GetMapping("/of/follow")
    public Result getFollowBlogs(@RequestParam("lastId") Long lastId, @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.getFollowBlogs(lastId, offset);
    }
}















