package com.gencode.lowcode.process.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.process.dto.ProcessCreateBody;
import com.gencode.lowcode.process.dto.ProcessPublishedVO;
import com.gencode.lowcode.process.dto.ProcessQuery;
import com.gencode.lowcode.process.dto.ProcessSaveBody;
import com.gencode.lowcode.process.entity.LcProcessDef;
import com.gencode.lowcode.process.mapper.LcProcessDefMapper;
import com.gencode.lowcode.process.service.ProcessDefService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程定义服务实现：租户插件自动隔离，雪花 ID 由 MP ASSIGN_ID 生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefServiceImpl implements ProcessDefService {

    /** 状态：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 状态：已发布（已部署） */
    private static final int STATUS_PUBLISHED = 1;
    /** 状态：停用 */
    private static final int STATUS_DISABLED = 2;

    /** BPMN process id 提取（双引号），兼容命名空间前缀，如 <bpmn2:process id="leave" ...> */
    private static final Pattern PROCESS_ID_DQ = Pattern.compile("<(?:[A-Za-z0-9]+:)?process\\b[^>]*\\bid\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    /** BPMN process id 提取（单引号兜底） */
    private static final Pattern PROCESS_ID_SQ = Pattern.compile("<(?:[A-Za-z0-9]+:)?process\\b[^>]*\\bid\\s*=\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE);

    private final LcProcessDefMapper defMapper;
    private final RepositoryService repositoryService;

    @Override
    public PageResult<LcProcessDef> page(ProcessQuery query) {
        LambdaQueryWrapper<LcProcessDef> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(LcProcessDef::getCode, query.getKeyword())
                    .or().like(LcProcessDef::getName, query.getKeyword())
                    .or().like(LcProcessDef::getCategory, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(LcProcessDef::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LcProcessDef::getCreateTime);
        IPage<LcProcessDef> page = defMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public LcProcessDef detail(Long id) {
        LcProcessDef def = defMapper.selectById(id);
        if (def == null) {
            throw new BizException("流程不存在");
        }
        return def;
    }

    @Override
    public void create(ProcessCreateBody body) {
        checkCodeUnique(body.getCode());
        LcProcessDef def = new LcProcessDef();
        def.setCode(body.getCode());
        def.setName(body.getName());
        def.setCategory(body.getCategory());
        def.setBpmnXml(body.getBpmnXml());
        def.setRemark(body.getRemark());
        def.setFlowKey(null);
        def.setPublishVersion(0);
        def.setStatus(STATUS_DRAFT);
        defMapper.insert(def);
    }

    @Override
    public void save(ProcessSaveBody body) {
        if (defMapper.selectById(body.getId()) == null) {
            throw new BizException("流程不存在");
        }
        LcProcessDef def = new LcProcessDef();
        BeanUtil.copyProperties(body, def);
        // ProcessSaveBody 无 code/flowKey/publishVersion 字段，code 不可修改，版本与状态不被覆盖
        defMapper.updateById(def);
    }

    @Override
    public void delete(Long id) {
        LcProcessDef def = defMapper.selectById(id);
        if (def == null) {
            throw new BizException("流程不存在");
        }
        defMapper.deleteById(id);
    }

    @Override
    public void deploy(Long id) {
        LcProcessDef def = defMapper.selectById(id);
        if (def == null) {
            throw new BizException("流程不存在");
        }
        if (StrUtil.isBlank(def.getBpmnXml())) {
            throw new BizException("BPMN XML 不能为空，请先设计流程");
        }
        String flowKey = extractProcessId(def.getBpmnXml());

        String deploymentName = def.getCode() + "-" + def.getName();
        try {
            Deployment deployment = repositoryService.createDeployment()
                    .name(deploymentName)
                    .addString(flowKey + ".bpmn20.xml", def.getBpmnXml())
                    .deploy();
            log.info("流程 [{}] 部署成功：deploymentId={}", def.getCode(), deployment.getId());
        } catch (Exception e) {
            log.warn("流程 [{}] 部署失败: {}", def.getCode(), e.getMessage());
            throw new BizException("BPMN 部署失败：" + e.getMessage());
        }

        def.setFlowKey(flowKey);
        def.setPublishVersion(def.getPublishVersion() == null ? 1 : def.getPublishVersion() + 1);
        def.setStatus(STATUS_PUBLISHED);
        defMapper.updateById(def);
    }

    @Override
    public ProcessPublishedVO getPublished(String code) {
        LcProcessDef def = defMapper.selectOne(new LambdaQueryWrapper<LcProcessDef>()
                .eq(LcProcessDef::getCode, code)
                .eq(LcProcessDef::getStatus, STATUS_PUBLISHED));
        if (def == null || StrUtil.isBlank(def.getFlowKey())) {
            throw new BizException("流程不存在或未部署");
        }
        ProcessPublishedVO vo = new ProcessPublishedVO();
        vo.setCode(def.getCode());
        vo.setName(def.getName());
        vo.setFlowKey(def.getFlowKey());
        vo.setPublishVersion(def.getPublishVersion());
        return vo;
    }

    // ------------------------------------------------------------------ 私有方法

    /**
     * 编码查重：含已逻辑删除的行（数据库唯一键 uk_lc_process_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"流程编码已存在"）
     */
    private void checkCodeUnique(String code) {
        if (defMapper.countByCodeIncludeDeleted(code) > 0) {
            throw new BizException("流程编码已存在");
        }
    }

    /** 从 BPMN XML 解析 <process id="..."> 作为 Flowable processDefinitionKey */
    private String extractProcessId(String bpmnXml) {
        String id = matchProcessId(PROCESS_ID_DQ, bpmnXml);
        if (StrUtil.isBlank(id)) {
            id = matchProcessId(PROCESS_ID_SQ, bpmnXml);
        }
        if (StrUtil.isBlank(id)) {
            throw new BizException("无法从 BPMN XML 解析 process id，请检查流程定义");
        }
        return id;
    }

    private String matchProcessId(Pattern pattern, String bpmnXml) {
        Matcher matcher = pattern.matcher(bpmnXml);
        return matcher.find() ? matcher.group(1) : null;
    }
}
