package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 发送短信验证码并保存验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("请输入正确的手机号码");
        }

        //生成验证码
        String code = RandomUtil.randomNumbers(6);
//        //保存到session中
//        session.setAttribute(phone, code);

        //保存到redis中,并设置过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送短信
        log.debug("验证码发送成功，验证码为：" + code);

        return Result.ok();
    }

    // 实现登录功能
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //手机号格式验证
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("请输入正确的手机号码");
        }

        //获取code
//        Object code = session.getAttribute(loginForm.getPhone());
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());

        //判断验证码是否存在和与输入的验证码是否一样，不存在说明发送验证码后手机号被修改了
        if (code == null || !loginForm.getCode().equals(code.toString())) {
            return Result.fail("验证码错误");
        }

        //判断用户是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, loginForm.getPhone());
        User user = baseMapper.selectOne(wrapper);
        if (user == null) {
            //不存在，则注册新用户
            user = addUserWithPhone(loginForm);
        }

//        //将用户信息保存到session中
//        session.setAttribute("user", user);

        //获取token
        String token = JwtUtils.getJwtToken(user.getId().toString(), user.getNickName());

        //将user转为map
        Map<String, Object> hashMap = new HashMap<>();
        hashMap.put("id", user.getId().toString());
        hashMap.put("nickname", user.getNickName());
        hashMap.put("icon", user.getIcon());

        //将用户信息保存到redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, hashMap);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回携带token
        return Result.ok(token);
    }

    //注册新用户
    private User addUserWithPhone(LoginFormDTO loginForm) {
        User user = new User();
        user.setPhone(loginForm.getPhone());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        baseMapper.insert(user);
        return user;
    }
}















