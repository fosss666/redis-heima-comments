package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 解决登录状态刷新问题
 * <p>
 * 在这个方案中，他确实可以使用对应路径的拦截，同时刷新登录token令牌的存活时间，但是现在这个拦截器他只是拦截
 * 需要被拦截的路径，假设当前用户访问了一些不需要拦截的路径，那么这个拦截器就不会生效，所以此时令牌刷新的动作
 * 实际上就不会执行，所以这个方案他是存在问题的
 * <p>
 * 既然之前的拦截器无法对不需要拦截的路径生效，那么我们可以添加一个拦截器，在第一个拦截器中拦截所有的路径，把
 * 第二个拦截器做的事情放入到第一个拦截器中，同时刷新令牌，因为第一个拦截器有了threadLocal的数据，所以此时
 * 第二个拦截器只需要判断拦截器中的user对象是否存在即可，完成整体刷新功能。
 * <p>
 * 这个类专门用于刷新
 */
public class TokenRefreshInterceptor implements HandlerInterceptor {

    //注意拦截器中无法注入
    private StringRedisTemplate stringRedisTemplate;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //从session中获取用户信息
//        User user = (User) request.getSession().getAttribute("user");

        //获取token
        String token = request.getHeader("authorization");//请求头的名字是和前端对应的
        if (StringUtils.isEmpty(token)) {
            response.setStatus(401);
            return false;
        }
        //从redis中获取用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);

        if (userMap.isEmpty()) {
            //没有登录，进行拦截
            response.setStatus(401);
            return false;
        }
        //转为UserDTO
        //登录了，存入线程
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.parseLong(userMap.get("id").toString()));
        userDTO.setNickName((String) userMap.get("nickname"));
        userDTO.setIcon((String) userMap.get("icon"));
        UserHolder.saveUser(userDTO);

        //刷新token有效期，使用户在登录状态进行相关操作时redis有效日期保持为30分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清除线程
        UserHolder.removeUser();
    }

}









