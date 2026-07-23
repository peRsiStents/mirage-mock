package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 批量文件生成模板：用户给一行模板（字面量 + ${...} 函数），按行数生成；可选首行标题。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_template")
public class FileTemplate extends BaseEntity {

    private Long projectId;

    private String name;

    /** 首行标题（可空，字符串模板，支持 ${...}） */
    private String headerLine;

    /** 行模板：字面量 + ${...} 生成器函数 */
    private String rowTemplate;

    /** 生成行数 */
    private Integer rowCount;

    /** GBK / UTF-8 */
    private String encoding;

    /** CRLF / LF */
    private String lineSeparator;

    /** txt / dat */
    private String fileExt;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private String remark;
}
