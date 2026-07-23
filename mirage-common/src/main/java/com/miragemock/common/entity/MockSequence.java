package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目级自增序列（${seq} 持久化）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_sequence")
public class MockSequence extends BaseEntity {

    private Long projectId;

    /** 序列名 */
    private String seqName;

    /** 当前值 */
    private Long currentValue;
}
