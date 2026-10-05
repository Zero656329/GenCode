package com.gencode.system.dict.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.dict.dto.DictTypeBody;
import com.gencode.system.dict.dto.DictTypeQuery;
import com.gencode.system.dict.entity.SysDictData;
import com.gencode.system.dict.entity.SysDictType;
import com.gencode.system.dict.mapper.SysDictDataMapper;
import com.gencode.system.dict.mapper.SysDictTypeMapper;
import com.gencode.system.dict.service.DictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典类型服务实现
 */
@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl implements DictTypeService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<SysDictType> page(DictTypeQuery query) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(SysDictType::getName, query.getKeyword())
                    .or().like(SysDictType::getDictType, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysDictType::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysDictType::getId);
        Page<SysDictType> page = dictTypeMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public List<SysDictType> listAll() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getStatus, Constants.STATUS_NORMAL)
                .orderByAsc(SysDictType::getId));
    }

    @Override
    public void create(DictTypeBody body) {
        checkDictTypeUnique(body.getDictType(), null);
        SysDictType type = new SysDictType();
        BeanUtil.copyProperties(body, type);
        if (type.getStatus() == null) {
            type.setStatus(Constants.STATUS_NORMAL);
        }
        dictTypeMapper.insert(type);
    }

    @Override
    public void update(DictTypeBody body) {
        if (body.getId() == null) {
            throw new BizException("字典类型ID不能为空");
        }
        if (dictTypeMapper.selectById(body.getId()) == null) {
            throw new BizException("字典类型不存在");
        }
        checkDictTypeUnique(body.getDictType(), body.getId());
        SysDictType type = new SysDictType();
        BeanUtil.copyProperties(body, type);
        dictTypeMapper.updateById(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException("字典类型不存在");
        }
        dictTypeMapper.deleteById(id);
        // 级联删除该类型的全部字典数据
        dictDataMapper.delete(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, type.getDictType()));
    }

    // ------------------------------------------------------------------ 私有方法

    private void checkDictTypeUnique(String dictType, Long excludeId) {
        if (StrUtil.isBlank(dictType)) {
            return;
        }
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .ne(excludeId != null, SysDictType::getId, excludeId);
        if (dictTypeMapper.selectCount(wrapper) > 0) {
            throw new BizException("字典类型已存在");
        }
    }
}
