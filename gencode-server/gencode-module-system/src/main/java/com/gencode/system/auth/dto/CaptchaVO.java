package com.gencode.system.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 图形验证码
 */
@Data
@AllArgsConstructor
public class CaptchaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String captchaId;
    /** dataURL：data:image/png;base64,... */
    private String image;
}
