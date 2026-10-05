package com.gencode.system.dict.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典数据精简项（by-type 接口渲染用）
 */
@Data
@AllArgsConstructor
public class DictDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String label;
    private String value;
    private Integer sort;
}
