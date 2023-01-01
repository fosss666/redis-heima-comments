package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 发送短信验证码并保存验证码
    @Override
//    public Result sendCode(String phone, HttpSession session) {
    public Result sendCode(String phone) {
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
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
    public Result login(LoginFormDTO loginForm) {
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

    /**
     * 根据id查询用户,注意过滤掉敏感信息
     */
    @Override
    public Result getUserById(Long id) {
        User user = baseMapper.selectById(id);
        if (user == null) {
            return Result.fail("该用户已注销");
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        return Result.ok(userDTO);
    }

    /**
     * 签到功能
     */
    @Override
    public Result sign() {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key  sign:userId:2022/04
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是这个月的第几天，注意从零开始
        int dayOfMonth = now.getDayOfMonth() - 1;
        //进行签到
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);
        return Result.ok();
    }

    /**
     * 统计连续签到此天数
     */
    @Override
    public Result signCount() {
        //拼接key
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + date;

        //获取当前是这个月第几天，从零开始
        int dayOfMonth = now.getDayOfMonth() - 1;
        //获取记录签到情况的bit数组
        //BITFIELD key get u7 0
        List<Long> bitField = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        //健壮性判断
        if (bitField == null || bitField.isEmpty()) {
            return Result.ok();
        }
        //集合第一个数记录的就是签到情况
        //判断该数是不是0或空，是则直接结束
        Long num = bitField.get(0);
        if (num == null || num == 0) {
            return Result.ok();
        }
        //计数器
        int count = 0;
        //循环判断连续签到次数
        while (true) {
            //最后一位和1进行与运算
            if ((num & 1) == 0) {
                //没签到，直接结束
                break;
            } else {
                //为1，计数器加一
                count++;
            }
            //右移一位
            //>>表示右移，如果该数为正，则高位补0，若为负数，则高位补1；
            //>>>表示无符号右移，也叫逻辑右移，即若该数为正，则高位补0，而若该数为负数，则右移后高位同样补0。
            num = num >>> 1;
        }
        //返回最近连续签到数
        return Result.ok(count);
    }
}















