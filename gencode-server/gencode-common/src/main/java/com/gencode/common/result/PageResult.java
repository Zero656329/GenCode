package com.gencode.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应：{ list, total }
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;
    private long total;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setList(page.getRecords());
        r.setTotal(page.getTotal());
        return r;
    }

    public static <T> PageResult<T> of(List<T> list, long total) {
        PageResult<T> r = new PageResult<>();
        r.setList(list);
        r.setTotal(total);
        return r;
    }
}
