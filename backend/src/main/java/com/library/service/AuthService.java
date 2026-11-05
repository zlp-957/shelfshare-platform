package com.library.service;

import com.library.vo.LoginVO;
import com.library.vo.SelfRegisterVO;

import java.util.Map;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 登录
     */
    Map<String, Object> login(LoginVO vo);

    /**
     * 读者自助注册。账号由系统分配，不接受用户自填。
     *
     * @return 系统分配的读者账号（要回给用户，否则他不知道用什么登录）
     */
    String register(SelfRegisterVO vo);
}
