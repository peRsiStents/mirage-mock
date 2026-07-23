package com.miragemock.admin.dto;

import lombok.Data;

/**
 * 文件生成请求（生成/预览共用；保存后的模板与其字段一致）。
 */
@Data
public class FileGenRequest {

    private Long projectId;
    private String name;
    private String headerLine;
    private String rowTemplate;
    private Integer rowCount;
    private String encoding;
    private String lineSeparator;
    private String fileExt;
}
