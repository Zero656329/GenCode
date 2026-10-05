package com.gencode.lowcode.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 大屏保存布局请求体（code 不可修改，故不含 code 字段）
 */
@Data
public class DashboardSaveBody {

    /** 大屏 ID（修改时必填） */
    @NotNull(message = "大屏ID不能为空")
    private Long id;

    /** 大屏名称 */
    @NotBlank(message = "大屏名称不能为空")
    private String name;

    /** 布局 JSON 字符串（设计器画布整体 JSON 字符串） */
    private String layoutJson;

    private String remark;
}
