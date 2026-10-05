package com.gencode.system.dict.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.dict.dto.DictDataBody;
import com.gencode.system.dict.dto.DictDataQuery;
import com.gencode.system.dict.dto.DictDataVO;
import com.gencode.system.dict.entity.SysDictData;
import com.gencode.system.dict.mapper.SysDictDataMapper;
import com.gencode.system.dict.service.DictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典数据服务实现
 */
@Service
@RequiredArgsConstructor
public class DictDataServiceImpl implements DictDataService {

    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<SysDictData> page(DictDataQuery query) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getDictType())) {
            wrapper.eq(SysDictData::getDictType, query.getDictType());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(SysDictData::getLabel, query.getKeyword())
                    .or().like(SysDictData::getValue, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysDictData::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysDictData::getSort).orderByAsc(SysDictData::getId);
        Page<SysDictData> page = dictDataMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public List<DictDataVO> listByType(String dictType) {
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictType, dictType)
                        .eq(SysDictData::getStatus, Constants.STATUS_NORMAL)
                        .orderByAsc(SysDictData::getSort).orderByAsc(SysDictData::getId))
                .stream()
                .map(d -> new DictDataVO(d.getLabel(), d.getValue(), d.getSort()))
                .toList();
    }

    @Override
    public void create(DictDataBody body) {
        SysDictData data = new SysDictData();
        BeanUtil.copyProperties(body, data);
        if (data.getStatus() == null) {
            data.setStatus(Constants.STATUS_NORMAL);
        }
        if (data.getIsDefault() == null) {
            data.setIsDefault(0);
        }
        if (data.getSort() == null) {
            data.setSort(0);
        }
        dictDataMapper.insert(data);
    }

    @Override
    public void update(DictDataBody body) {
        if (body.getId() == null) {
            throw new BizException("字典数据ID不能为空");
        }
        if (dictDataMapper.selectById(body.getId()) == null) {
            throw new BizException("字典数据不存在");
        }
        SysDictData data = new SysDictData();
        BeanUtil.copyProperties(body, data);
        dictDataMapper.updateById(data);
    }

    @Override
    public void delete(Long id) {
        dictDataMapper.deleteById(id);
    }
}
