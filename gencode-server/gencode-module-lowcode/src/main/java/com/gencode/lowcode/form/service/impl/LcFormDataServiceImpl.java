package com.gencode.lowcode.form.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.form.dto.FormDataQuery;
import com.gencode.lowcode.form.dto.FormDataSubmitBody;
import com.gencode.lowcode.form.entity.LcFormData;
import com.gencode.lowcode.form.entity.LcForm;
import com.gencode.lowcode.form.mapper.LcFormDataMapper;
import com.gencode.lowcode.form.mapper.LcFormMapper;
import com.gencode.lowcode.form.service.LcFormDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 表单填报数据服务实现：data 整体转 JSON 字符串存 data_json
 */
@Service
@RequiredArgsConstructor
public class LcFormDataServiceImpl implements LcFormDataService {

    /** 状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;

    private final LcFormDataMapper formDataMapper;
    private final LcFormMapper formMapper;

    @Override
    public void submit(String formCode, FormDataSubmitBody body) {
        LcForm form = formMapper.selectOne(new LambdaQueryWrapper<LcForm>()
                .eq(LcForm::getCode, formCode)
                .eq(LcForm::getStatus, STATUS_PUBLISHED));
        if (form == null) {
            throw new BizException("表单不存在或未发布");
        }
        LcFormData formData = new LcFormData();
        formData.setFormCode(formCode);
        formData.setDataJson(JSONUtil.toJsonStr(body.getData()));
        formDataMapper.insert(formData);
    }

    @Override
    public PageResult<LcFormData> page(FormDataQuery query) {
        LambdaQueryWrapper<LcFormData> wrapper = new LambdaQueryWrapper<LcFormData>()
                .eq(StrUtil.isNotBlank(query.getFormCode()), LcFormData::getFormCode, query.getFormCode())
                .orderByDesc(LcFormData::getCreateTime);
        IPage<LcFormData> page = formDataMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }
}
