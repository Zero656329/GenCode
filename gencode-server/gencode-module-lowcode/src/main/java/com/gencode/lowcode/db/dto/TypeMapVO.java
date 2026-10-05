package com.gencode.lowcode.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 方言类型映射项（GET /lc/db/typemap 供前端下拉）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeMapVO {

    /** 逻辑类型键：varchar/int/bigint/datetime/text/decimal */
    private String key;

    /** 展示标签 */
    private String label;
}
