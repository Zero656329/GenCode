package com.gencode.system.config.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.config.dto.ConfigBody;
import com.gencode.system.config.dto.ConfigQuery;
import com.gencode.system.config.entity.SysConfig;
import com.gencode.system.config.mapper.SysConfigMapper;
import com.gencode.system.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 参数配置服务实现
 */
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final SysConfigMapper configMapper;

    @Override
    public PageResult<SysConfig> page(ConfigQuery query) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(SysConfig::getConfigName, query.getKeyword())
                    .or().like(SysConfig::getConfigKey, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysConfig::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysConfig::getId);
        Page<SysConfig> page = configMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public String getValueByKey(String key) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        return config == null ? null : config.getConfigValue();
    }

    @Override
    public void create(ConfigBody body) {
        checkConfigKeyUnique(body.getConfigKey(), null);
        SysConfig config = new SysConfig();
        BeanUtil.copyProperties(body, config);
        if (config.getStatus() == null) {
            config.setStatus(Constants.STATUS_NORMAL);
        }
        if (config.getConfigValue() == null) {
            config.setConfigValue("");
        }
        configMapper.insert(config);
    }

    @Override
    public void update(ConfigBody body) {
        if (body.getId() == null) {
            throw new BizException("参数ID不能为空");
        }
        if (configMapper.selectById(body.getId()) == null) {
            throw new BizException("参数不存在");
        }
        checkConfigKeyUnique(body.getConfigKey(), body.getId());
        SysConfig config = new SysConfig();
        BeanUtil.copyProperties(body, config);
        configMapper.updateById(config);
    }

    @Override
    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 私有方法

    private void checkConfigKeyUnique(String configKey, Long excludeId) {
        if (StrUtil.isBlank(configKey)) {
            return;
        }
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .ne(excludeId != null, SysConfig::getId, excludeId);
        if (configMapper.selectCount(wrapper) > 0) {
            throw new BizException("参数键已存在");
        }
    }
}
