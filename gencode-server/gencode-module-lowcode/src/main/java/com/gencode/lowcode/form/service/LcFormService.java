package com.gencode.lowcode.form.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.form.dto.FormCreateBody;
import com.gencode.lowcode.form.dto.FormPublishedVO;
import com.gencode.lowcode.form.dto.FormQuery;
import com.gencode.lowcode.form.dto.FormSaveBody;
import com.gencode.lowcode.form.entity.LcForm;

/**
 * 表单定义服务
 */
public interface LcFormService {

    /** 分页查询（keyword 匹配 code/name） */
    PageResult<LcForm> page(FormQuery query);

    /** 详情（含 schemaJson） */
    LcForm detail(Long id);

    /** 新建草稿（schemaJson 置空，version=0，status=0） */
    void create(FormCreateBody body);

    /** 保存设计（code 不可修改） */
    void saveDesign(FormSaveBody body);

    /** 逻辑删除（已发布表单需先停用） */
    void delete(Long id);

    /** 发布：version+1，publishedSchema=当前 schemaJson，status=1，publishTime=now */
    void publish(Long id);

    /** 修改状态（0草稿 1已发布 2停用） */
    void changeStatus(Long id, Integer status);

    /** 运行时接口：按编码取已发布表单（未发布过报"表单不存在或未发布"） */
    FormPublishedVO getPublished(String code);
}
