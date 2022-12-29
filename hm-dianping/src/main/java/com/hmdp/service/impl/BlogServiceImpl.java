package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;
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
    @Resource
    private IFollowService followService;

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
            return Result.ok(Collections.emptyList());
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

    /**
     * 发布探店笔记
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        int insert = baseMapper.insert(blog);
        if (insert == 0) {
            return Result.fail("新增笔记失败");
        }
        //查询该用户的所有粉丝
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowUserId, user.getId());
        List<Follow> followList = followService.list(wrapper);
        //将发布的笔记id推送到粉丝收件箱
        for (Follow follow : followList) {
            String key = FEED_KEY + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回笔记id
        return Result.ok(blog.getId());
    }

    /**
     * 分页查询当前用户的blog
     */
    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Blog::getUserId, user.getId()).orderByDesc(Blog::getCreateTime);
        IPage<Blog> page = new Page<>(current, 3);
        baseMapper.selectPage(page, wrapper);
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 滚动分页查询关注的用户的笔记
     */
    @Override
    public Result getFollowBlogs(Long lastId, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        //获取当前用户关注的人的笔记id
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                key,
                0,//不用管，时间戳的最小值
                lastId,//已查询到的笔记的时间戳的最大值
                offset,
                3
        );
        //健壮性判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //需返回笔记，minTime，offset
        ScrollResult scrollResult = new ScrollResult();
        //笔记id集合
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        //记录最小的时间戳
        long minTime = 0;
        //记录score相同的个数
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取所有笔记的id
            String id = typedTuple.getValue();
            blogIds.add(Long.valueOf(id));
            //获取score(时间戳)，并找到最小的,即遍历的最后一个
            long time = typedTuple.getScore().longValue();
            if (minTime == time) {
                os++;
            } else {
                minTime = time;
            }
        }
        //根据笔记id查询笔记
        StringBuilder sb = new StringBuilder();
        int size = 1;
        for (Long blogId : blogIds) {
            if (size != blogIds.size()) {
                sb.append(blogId + ",");
                size++;
            } else {
                sb.append(blogId);
            }
        }
        String idsStr = sb.toString();

        List<Blog> blogList = new ArrayList<>();
        for (Long blogId : blogIds) {
            LambdaUpdateWrapper<Blog> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Blog::getId, blogId).last("order by field(id," + idsStr + ")");
            Blog blog = baseMapper.selectOne(wrapper);
            blogList.add(blog);
        }
        List<Blog> blogs = new ArrayList<>();
        for (Blog blog : blogList) {
            blogs.add(getBlog(blog));
        }
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

}



















