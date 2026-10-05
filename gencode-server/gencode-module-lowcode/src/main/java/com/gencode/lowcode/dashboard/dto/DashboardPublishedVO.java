package com.gencode.lowcode.dashboard.dto;

import lombok.Data;

/**
 * 已发布大屏 VO（运行时接口出参：大屏运行页按 code 渲染）
 */
@Data
public class DashboardPublishedVO {

    /** 大屏编码 */
    private String code;
    /** 大屏名称 */
    private String name;
    /** 发布版本号 */
    private Integer version;
    /** 已发布布局快照（对外仍叫 layoutJson） */
    private String layoutJson;
}
