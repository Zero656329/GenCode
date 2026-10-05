package com.gencode.lowcode.recycle.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站条目（已删除行摘要）
 */
@Data
public class RecycleItemVO {

    /** 记录 ID */
    private Long id;

    /** 名称（name 列，为空取 code 列） */
    private String name;

    /** 删除前的创建时间 */
    private LocalDateTime createTime;
}
