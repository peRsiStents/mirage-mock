package com.miragemock.admin.controller;

import com.miragemock.admin.dto.UserRequest;
import com.miragemock.admin.service.UserService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理 REST API（仅平台管理员可调用，由 UserService 校验）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<UserAccount>> list() {
        return Result.ok(userService.list());
    }

    @PostMapping
    public Result<UserAccount> create(@RequestBody UserRequest req) {
        return Result.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    public Result<UserAccount> update(@PathVariable Long id, @RequestBody UserRequest req) {
        return Result.ok(userService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
