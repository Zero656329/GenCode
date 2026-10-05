package com.gencode.lowcode.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 大屏新建请求体（新建草稿：status=0，version=0，layoutJson 可空）
 */
@Data
public class DashboardCreateBody {

    /** 大屏编码（全局唯一） */
    @NotBlank(message = "大屏编码不能为空")
    private String code;

    /** 大屏名称 */
    @NotBlank(message = "大屏名称不能为空")
    private String name;

    /** 布局 JSON 字符串 */
    private String layoutJson;

    private String remark;
}
