package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.LoginResponse;
import com.miragemock.admin.mapper.UserAccountMapper;
import com.miragemock.admin.security.JwtUtil;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.UserAccount;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(UserAccountMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(String username, String password) {
        UserAccount user = userMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        boolean admin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        String token = jwtUtil.generate(user.getId(), user.getUsername(), admin);
        return new LoginResponse(token, user.getId(), user.getUsername(), admin);
    }
}
