package com.miragemock.core.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP Mock 处理结果：携带命中信息供日志记录使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpMockResult {

    private Long projectId;

    private Long interfaceId;

    private Long ruleId;

    /** 是否命中规则（未命中时 interfaceId/ruleId 可能为空） */
    private boolean matched;

    private MockResponse response;

    public static HttpMockResult notMatched(Long projectId, Long interfaceId, MockResponse response) {
        return new HttpMockResult(projectId, interfaceId, null, false, response);
    }
}
