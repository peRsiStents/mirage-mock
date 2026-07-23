package com.miragemock.admin.dto;

import lombok.Data;

@Data
public class MemberRequest {

    private Long userId;
    private String memberRole;
}
