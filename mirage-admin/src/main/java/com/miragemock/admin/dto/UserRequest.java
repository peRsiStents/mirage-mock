package com.miragemock.admin.dto;

import lombok.Data;

/**
 * 用户创建/更新请求。
 *
 * <p>password：创建时必填；更新时空值表示不改密码，非空表示重置为新密码。
 */
@Data
public class UserRequest {

    private String username;

    private String nickname;

    private String password;

    /** 1 平台管理员 / 0 普通用户 */
    private Integer isAdmin;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
