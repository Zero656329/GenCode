package com.gencode.system.file.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.gencode.common.result.PageQuery;

/**
 * 文件分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileQuery extends PageQuery {

    /** 原始文件名关键字 */
    private String keyword;
}
