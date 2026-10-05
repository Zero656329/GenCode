package com.gencode.lowcode.form.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.form.dto.FormDataQuery;
import com.gencode.lowcode.form.dto.FormDataSubmitBody;
import com.gencode.lowcode.form.entity.LcFormData;

/**
 * 表单填报数据服务
 */
public interface LcFormDataService {

    /** 填报提交（表单须已发布，data 整体转 JSON 字符串存 data_json） */
    void submit(String formCode, FormDataSubmitBody body);

    /** 填报数据分页（formCode 精确过滤） */
    PageResult<LcFormData> page(FormDataQuery query);
}
