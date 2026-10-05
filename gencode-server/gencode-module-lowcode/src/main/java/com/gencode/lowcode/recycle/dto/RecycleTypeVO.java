package com.gencode.lowcode.recycle.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回收站支持的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecycleTypeVO {

    /** 类型标识：lc_form/lc_list/lc_dashboard/lc_dataset */
    private String type;

    /** 类型名称：表单/列表/大屏/数据集 */
    private String label;
}
