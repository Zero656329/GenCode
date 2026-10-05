package com.gencode.lowcode.process.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 转办请求体：{ assignee, comment }
 */
@Data
public class ProcessTransferBody {

    /** 新办理人账号 */
    @NotBlank(message = "新办理人不能为空")
    private String assignee;

    /** 转办意见 */
    private String comment;
}
