package com.gencode.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类，业务筛选字段可继承扩展
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;
    /** 每页条数 */
    private Integer pageSize = 10;

    public <T> Page<T> toPage() {
        return new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
    }
}
