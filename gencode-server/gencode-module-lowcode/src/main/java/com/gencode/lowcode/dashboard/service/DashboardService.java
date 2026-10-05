package com.gencode.lowcode.dashboard.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.dashboard.dto.DashboardCreateBody;
import com.gencode.lowcode.dashboard.dto.DashboardPublishedVO;
import com.gencode.lowcode.dashboard.dto.DashboardQuery;
import com.gencode.lowcode.dashboard.dto.DashboardSaveBody;
import com.gencode.lowcode.dashboard.entity.LcDashboard;

/**
 * 大屏定义服务
 */
public interface DashboardService {

    /** 分页查询（keyword 匹配 code/name，status 过滤） */
    PageResult<LcDashboard> page(DashboardQuery query);

    /** 详情（含 layoutJson） */
    LcDashboard detail(Long id);

    /** 新建草稿（status=0，version=0） */
    void create(DashboardCreateBody body);

    /** 保存布局（code 不可修改） */
    void saveDesign(DashboardSaveBody body);

    /** 逻辑删除 */
    void delete(Long id);

    /** 发布：version+1，publishedSchema=当前 layoutJson，status=1，publishTime=now */
    void publish(Long id);

    /** 启用/停用（仅更新 status，不影响发布快照） */
    void updateStatus(Long id, Integer status);

    /** 运行时接口：按编码取已发布大屏（未发布过报"大屏不存在或未发布"） */
    DashboardPublishedVO getPublished(String code);
}
