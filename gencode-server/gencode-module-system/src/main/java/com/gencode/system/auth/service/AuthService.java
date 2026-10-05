package com.gencode.system.auth.service;

import com.gencode.system.auth.dto.CaptchaVO;
import com.gencode.system.auth.dto.LoginBody;
import com.gencode.system.auth.dto.LoginResult;
import com.gencode.system.auth.dto.LoginUser;

/**
 * 认证服务
 */
public interface AuthService {

    /** 生成算术验证码（5 分钟有效） */
    CaptchaVO captcha();

    /** 登录 */
    LoginResult login(LoginBody body);

    /** 当前登录用户 */
    LoginUser me();

    /** 退出登录 */
    void logout();
}
