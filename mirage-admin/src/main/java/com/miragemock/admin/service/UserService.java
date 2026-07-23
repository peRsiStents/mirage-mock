package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.UserRequest;
import com.miragemock.admin.mapper.UserAccountMapper;
import com.miragemock.admin.security.AuthContext;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.UserAccount;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理：仅平台管理员可操作。密码 BCrypt 哈希，哈希值不返回给前端。
 */
@Service
public class UserService {

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserAccountMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户列表：对任意已登录用户开放（项目成员选择等场景需要），密码哈希不返回。
     * 账号的增删改仍仅管理员可操作（见 create/update/delete）。
     */
    public List<UserAccount> list() {
        List<UserAccount> all = userMapper.selectList(
                new LambdaQueryWrapper<UserAccount>().orderByDesc(UserAccount::getCreateTime));
        all.forEach(this::mask);
        return all;
    }

    @Transactional
    public UserAccount create(UserRequest req) {
        requireAdmin();
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码不能为空");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CONFLICT, "用户名已存在: " + req.getUsername());
        }
        UserAccount u = new UserAccount();
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setNickname(req.getNickname());
        u.setIsAdmin(req.getIsAdmin() == null ? 0 : req.getIsAdmin());
        u.setStatus(req.getStatus() == null ? Constants.STATUS_ENABLED : req.getStatus());
        userMapper.insert(u);
        return mask(u);
    }

    @Transactional
    public UserAccount update(Long id, UserRequest req) {
        requireAdmin();
        UserAccount u = get(id);
        if (req.getNickname() != null) {
            u.setNickname(req.getNickname());
        }
        if (req.getIsAdmin() != null) {
            u.setIsAdmin(req.getIsAdmin());
        }
        if (req.getStatus() != null) {
            u.setStatus(req.getStatus());
        }
        // 非空密码视为重置密码
        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(u);
        return mask(u);
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        UserAccount u = get(id);
        if ("admin".equals(u.getUsername())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除内置 admin 账号");
        }
        if (id.equals(AuthContext.currentUserId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除当前登录账号");
        }
        userMapper.deleteById(id);
    }

    private UserAccount get(Long id) {
        UserAccount u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return u;
    }

    /** 擦除密码哈希，配合全局 non_null 序列化使其不出现在响应中 */
    private UserAccount mask(UserAccount u) {
        if (u != null) {
            u.setPasswordHash(null);
        }
        return u;
    }

    private void requireAdmin() {
        if (!AuthContext.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可管理用户");
        }
    }
}
