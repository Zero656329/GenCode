package com.gencode.lowcode.process.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.process.dto.ProcessCreateBody;
import com.gencode.lowcode.process.dto.ProcessPublishedVO;
import com.gencode.lowcode.process.dto.ProcessQuery;
import com.gencode.lowcode.process.dto.ProcessSaveBody;
import com.gencode.lowcode.process.entity.LcProcessDef;

/**
 * 流程定义服务：CRUD + 部署到 Flowable
 */
public interface ProcessDefService {

    /** 分页（keyword 匹配 code/name/category） */
    PageResult<LcProcessDef> page(ProcessQuery query);

    /** 详情（含 bpmnXml） */
    LcProcessDef detail(Long id);

    /** 新建草稿（publishVersion=0，status=0） */
    void create(ProcessCreateBody body);

    /** 保存（code 不可修改） */
    void save(ProcessSaveBody body);

    /** 逻辑删除 */
    void delete(Long id);

    /**
     * 部署到 Flowable：bpmnXml 必填非空；flow_key 取 BPMN process id；
     * publish_version+1，status=1
     */
    void deploy(Long id);

    /** 运行时接口：按编码取已部署定义 */
    ProcessPublishedVO getPublished(String code);
}
