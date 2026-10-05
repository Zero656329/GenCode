package com.gencode.system.dict.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.dict.dto.DictDataBody;
import com.gencode.system.dict.dto.DictDataQuery;
import com.gencode.system.dict.dto.DictDataVO;
import com.gencode.system.dict.entity.SysDictData;

import java.util.List;

/**
 * 字典数据服务
 */
public interface DictDataService {

    PageResult<SysDictData> page(DictDataQuery query);

    /** 按类型取启用字典（表单/列表渲染用） */
    List<DictDataVO> listByType(String dictType);

    void create(DictDataBody body);

    void update(DictDataBody body);

    void delete(Long id);
}
