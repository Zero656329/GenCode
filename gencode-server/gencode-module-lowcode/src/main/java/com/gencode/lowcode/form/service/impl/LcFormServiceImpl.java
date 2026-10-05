package com.gencode.lowcode.form.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.form.dto.FormCreateBody;
import com.gencode.lowcode.form.dto.FormPublishedVO;
import com.gencode.lowcode.form.dto.FormQuery;
import com.gencode.lowcode.form.dto.FormSaveBody;
import com.gencode.lowcode.form.entity.LcForm;
import com.gencode.lowcode.form.mapper.LcFormMapper;
import com.gencode.lowcode.form.service.LcFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表单定义服务实现：租户插件自动隔离，雪花 ID 由 MP ASSIGN_ID 生成
 */
@Service
@RequiredArgsConstructor
public class LcFormServiceImpl implements LcFormService {

    /** 状态：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 状态：停用 */
    private static final int STATUS_DISABLED = 2;

    private final LcFormMapper formMapper;

    @Override
    public PageResult<LcForm> page(FormQuery query) {
        LambdaQueryWrapper<LcForm> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(LcForm::getCode, query.getKeyword())
                    .or().like(LcForm::getName, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(LcForm::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LcForm::getCreateTime);
        IPage<LcForm> page = formMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public LcForm detail(Long id) {
        LcForm form = formMapper.selectById(id);
        if (form == null) {
            throw new BizException("表单不存在");
        }
        return form;
    }

    @Override
    public void create(FormCreateBody body) {
        checkCodeUnique(body.getCode());
        LcForm form = new LcForm();
        form.setCode(body.getCode());
        form.setName(body.getName());
        form.setRemark(body.getRemark());
        form.setSchemaJson(null);
        form.setStatus(STATUS_DRAFT);
        form.setVersion(0);
        formMapper.insert(form);
    }

    @Override
    public void saveDesign(FormSaveBody body) {
        if (formMapper.selectById(body.getId()) == null) {
            throw new BizException("表单不存在");
        }
        LcForm form = new LcForm();
        BeanUtil.copyProperties(body, form);
        // FormSaveBody 无 code 字段，code 天然不会被修改
        formMapper.updateById(form);
    }

    @Override
    public void delete(Long id) {
        LcForm form = formMapper.selectById(id);
        if (form == null) {
            throw new BizException("表单不存在");
        }
        if (form.getStatus() != null && form.getStatus() == STATUS_PUBLISHED) {
            throw new BizException("已发布表单请先停用");
        }
        formMapper.deleteById(id);
    }

    @Override
    public void publish(Long id) {
        LcForm form = formMapper.selectById(id);
        if (form == null) {
            throw new BizException("表单不存在");
        }
        if (StrUtil.isBlank(form.getSchemaJson())) {
            throw new BizException("请先设计表单");
        }
        form.setVersion(form.getVersion() == null ? 1 : form.getVersion() + 1);
        form.setPublishedSchema(form.getSchemaJson());
        form.setStatus(STATUS_PUBLISHED);
        form.setPublishTime(LocalDateTime.now());
        formMapper.updateById(form);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || status < STATUS_DRAFT || status > STATUS_DISABLED) {
            throw new BizException("非法的表单状态");
        }
        if (formMapper.selectById(id) == null) {
            throw new BizException("表单不存在");
        }
        LcForm form = new LcForm();
        form.setId(id);
        form.setStatus(status);
        formMapper.updateById(form);
    }

    @Override
    public FormPublishedVO getPublished(String code) {
        LcForm form = formMapper.selectOne(new LambdaQueryWrapper<LcForm>()
                .eq(LcForm::getCode, code)
                .eq(LcForm::getStatus, STATUS_PUBLISHED));
        if (form == null) {
            throw new BizException("表单不存在或未发布");
        }
        FormPublishedVO vo = new FormPublishedVO();
        vo.setCode(form.getCode());
        vo.setName(form.getName());
        vo.setVersion(form.getVersion());
        vo.setSchemaJson(form.getPublishedSchema());
        return vo;
    }

    /**
     * 编码查重：含已逻辑删除的行（数据库唯一键不区分 deleted，已删除记录仍占用编码）
     */
    private void checkCodeUnique(String code) {
        if (formMapper.countByCodeIncludeDeleted(code) > 0) {
            throw new BizException("表单编码已存在");
        }
    }
}
