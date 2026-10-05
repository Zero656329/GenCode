package com.gencode.system.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置密码请求体
 */
@Data
public class ResetPwdBody {

    @NotBlank(message = "密码不能为空")
    private String password;
}
