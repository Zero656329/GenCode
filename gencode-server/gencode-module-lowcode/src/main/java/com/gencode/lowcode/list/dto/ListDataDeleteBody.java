package com.gencode.lowcode.list.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 列表数据行删除请求体（仅 TABLE 型，按 pk 删）
 */
@Data
public class ListDataDeleteBody {

    /** 主键值 */
    @NotBlank(message = "主键值不能为空")
    private String pk;
}
