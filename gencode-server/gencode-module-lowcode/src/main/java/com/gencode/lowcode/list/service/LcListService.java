package com.gencode.lowcode.list.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.list.dto.ColumnVO;
import com.gencode.lowcode.list.dto.ListCreateBody;
import com.gencode.lowcode.list.dto.ListPublishedVO;
import com.gencode.lowcode.list.dto.ListQuery;
import com.gencode.lowcode.list.dto.ListSaveBody;
import com.gencode.lowcode.list.entity.LcList;

import java.util.List;

/**
 * 列表定义服务
 */
public interface LcListService {

    /** 分页查询（keyword 匹配 code/name） */
    PageResult<LcList> page(ListQuery query);

    /** 详情（含 sourceConfig/listSchema JSON 字符串） */
    LcList detail(Long id);

    /** 新建（sourceConfig/listSchema 置空，version=0，status=0） */
    void create(ListCreateBody body);

    /** 保存（code 不可修改） */
    void save(ListSaveBody body);

    /** 逻辑删除（已发布列表需先停用） */
    void delete(Long id);

    /** 发布：version+1，published_schema=JSON{sourceConfig,listSchema} 快照，status=1 */
    void publish(Long id);

    /** 修改状态（0草稿 1已发布 2停用） */
    void changeStatus(Long id, Integer status);

    /** 运行时接口：按编码取已发布列表（未发布过报"列表不存在或未发布"） */
    ListPublishedVO getPublished(String code);

    /** 反读表列（datasourceId 空=平台主库） */
    List<ColumnVO> columns(Long datasourceId, String tableName);
}
