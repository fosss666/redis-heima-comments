package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 
 */
public interface IUserService extends IService<User> {
    // 发送短信验证码并保存验证码
//    Result sendCode(String phone, HttpSession session);
    Result sendCode(String phone);
    // 实现登录功能
//    Result login(LoginFormDTO loginForm, HttpSession session);
    Result login(LoginFormDTO loginForm);
}
