package com.gencode.lowcode.recycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 回收站恢复/彻底删除请求体
 */
@Data
public class RecycleRestoreBody {

    /** 业务类型：lc_form/lc_list/lc_dashboard/lc_dataset */
    @NotBlank(message = "回收站类型不能为空")
    private String type;

    /** 记录 ID */
    @NotNull(message = "记录ID不能为空")
    private Long id;
}
