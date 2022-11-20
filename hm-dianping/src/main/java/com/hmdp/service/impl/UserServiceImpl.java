package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    // 发送短信验证码并保存验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("请输入正确的手机号码");
        }

        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存到session中
        session.setAttribute("code", code);

        //发送短信
        log.debug("验证码发送成功，验证码为：" + code);

        return Result.ok();
    }
}















