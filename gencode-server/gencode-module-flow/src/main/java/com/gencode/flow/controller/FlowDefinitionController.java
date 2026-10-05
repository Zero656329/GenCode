package com.gencode.flow.controller;

import cn.hutool.core.util.StrUtil;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.flow.vo.FlowDefinitionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程定义（一期骨架）：Flowable 表未就绪时返回空页不报错
 */
@Slf4j
@RestController
@RequestMapping("/flow/definition")
@RequiredArgsConstructor
public class FlowDefinitionController {

    private final RepositoryService repositoryService;

    @GetMapping("/list")
    public R<PageResult<FlowDefinitionVO>> list(@RequestParam(required = false) String name,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
            if (StrUtil.isNotBlank(name)) {
                query.processDefinitionNameLike("%" + name + "%");
            }
            long total = query.count();
            List<ProcessDefinition> definitions = query
                    .orderByProcessDefinitionKey().asc()
                    .listPage((pageNum - 1) * pageSize, pageSize);
            List<FlowDefinitionVO> vos = definitions.stream().map(this::toVO).toList();
            return R.ok(PageResult.of(vos, total));
        } catch (Exception e) {
            log.warn("查询流程定义失败（Flowable 表可能未就绪）: {}", e.getMessage());
            return R.ok(PageResult.of(List.of(), 0));
        }
    }

    private FlowDefinitionVO toVO(ProcessDefinition definition) {
        FlowDefinitionVO vo = new FlowDefinitionVO();
        vo.setId(definition.getId());
        vo.setKey(definition.getKey());
        vo.setName(definition.getName());
        vo.setVersion(definition.getVersion());
        vo.setDeploymentId(definition.getDeploymentId());
        try {
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(definition.getDeploymentId())
                    .singleResult();
            if (deployment != null) {
                vo.setDeployTime(deployment.getDeploymentTime());
            }
        } catch (Exception ignored) {
        }
        return vo;
    }
}
