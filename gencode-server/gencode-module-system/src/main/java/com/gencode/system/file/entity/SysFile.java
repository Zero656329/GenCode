package com.gencode.system.file.entity;

import com.gencode.common.entity.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件（对象存储元数据，二进制内容在 MinIO）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 存储桶 */
    private String bucket;
    /** 对象名（yyyyMM/uuid.suffix） */
    private String objectName;
    /** 原始文件名 */
    private String originalName;
    /** 后缀（小写，无点） */
    private String suffix;
    /** 字节数（列名 file_size：size 为 Oracle 保留字，多数据库统一命名） */
    private Long fileSize;
    /** MIME 类型 */
    private String contentType;
    private String remark;
}
