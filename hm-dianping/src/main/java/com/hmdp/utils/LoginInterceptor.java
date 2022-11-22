package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * 在现在的系统设计中，前后端分离已基本成为常态，分离之后如何获取用户信息就成了一件麻烦事，通常在用户登录后，
 * 用户信息会保存在Session或者Token中。这个时候，我们如果使用常规的手段去获取用户信息会很费劲，拿Session来说，
 * 我们要在接口参数中加上HttpServletRequest对象，然后调用 getSession方法，且每一个需要用户信息的接口都要
 * 加上这个参数，才能获取Session，这样实现就很麻烦了。
 * <p>
 * 在实际的系统设计中，我们肯定不会采用上面所说的这种方式，而是使用ThreadLocal，我们会选择在拦截器的业务中，
 * 获取到保存的用户信息，然后存入ThreadLocal，那么当前线程在任何地方如果需要拿到用户信息都可以使用ThreadLocal
 * 的get()方法 (异步程序中ThreadLocal是不可靠的)
 */
public class LoginInterceptor implements HandlerInterceptor {
    //注意拦截器中无法注入
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
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









