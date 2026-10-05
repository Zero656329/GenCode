package com.gencode.lowcode.form.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 表单填报提交请求体
 */
@Data
public class FormDataSubmitBody {

    /** 填报数据（key=字段编码，value=字段值，服务端整体转 JSON 字符串存库） */
    @NotNull(message = "填报数据不能为空")
    private Map<String, Object> data;
}
