package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 录制响应快照：代理模式下转发的上游响应，按 record_key（method|path|query|bodyHash）回放匹配。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recorded_response")
public class RecordedResponse extends BaseEntity {

    private Long projectId;

    private Long interfaceId;

    private String method;

    private String path;

    /** 回放匹配键：method|path|query|bodyHash 的 MD5 */
    private String recordKey;

    private Integer status;

    /** JSON 对象（响应头） */
    private String headers;

    private String body;

    /** 回放命中次数 */
    private Integer hitCount;

    private LocalDateTime lastHitTime;
}
