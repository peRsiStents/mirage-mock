package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户账号
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_account")
public class UserAccount extends BaseEntity {

    private String username;

    /** BCrypt 哈希 */
    private String passwordHash;

    private String nickname;

    /** 1 平台管理员 / 0 普通用户 */
    private Integer isAdmin;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
