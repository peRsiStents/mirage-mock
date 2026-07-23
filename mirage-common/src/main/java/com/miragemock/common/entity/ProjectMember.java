package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目成员关系
 */
@Data
@TableName("project_member")
public class ProjectMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    private Long userId;

    /** ADMIN / MEMBER */
    private String memberRole;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
