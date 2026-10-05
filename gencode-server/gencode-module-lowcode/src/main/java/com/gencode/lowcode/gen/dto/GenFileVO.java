package com.gencode.lowcode.gen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成文件项（POST /lc/gen/preview → data = { files: [{ path, content }] }）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenFileVO {

    /** 相对路径（如 com/gencode/business/entity/BizOrder.java） */
    private String path;

    /** 文件内容（UTF-8 文本） */
    private String content;
}
