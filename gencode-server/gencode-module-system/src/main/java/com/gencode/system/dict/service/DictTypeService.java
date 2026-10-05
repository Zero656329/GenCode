package com.gencode.system.dict.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.dict.dto.DictTypeBody;
import com.gencode.system.dict.dto.DictTypeQuery;
import com.gencode.system.dict.entity.SysDictType;

import java.util.List;

/**
 * 字典类型服务
 */
public interface DictTypeService {

    PageResult<SysDictType> page(DictTypeQuery query);

    List<SysDictType> listAll();

    void create(DictTypeBody body);

    void update(DictTypeBody body);

    /** 删除类型并级联删除数据 */
    void delete(Long id);
}
