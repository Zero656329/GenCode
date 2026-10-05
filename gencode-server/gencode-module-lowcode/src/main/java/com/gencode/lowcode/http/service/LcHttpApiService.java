package com.gencode.lowcode.http.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.http.dto.HttpApiCreateBody;
import com.gencode.lowcode.http.dto.HttpApiQuery;
import com.gencode.lowcode.http.dto.HttpApiSaveBody;
import com.gencode.lowcode.http.dto.HttpCallBody;
import com.gencode.lowcode.http.dto.HttpCallResultVO;
import com.gencode.lowcode.http.dto.HttpLogQuery;
import com.gencode.lowcode.http.entity.LcHttpApi;
import com.gencode.lowcode.http.entity.LcHttpLog;

/**
 * 第三方 HTTP 接口服务
 */
public interface LcHttpApiService {

    /** 分页查询（keyword 匹配 code/name） */
    PageResult<LcHttpApi> page(HttpApiQuery query);

    /** 详情 */
    LcHttpApi detail(Long id);

    /** 新建（status=0 启用） */
    void create(HttpApiCreateBody body);

    /** 保存（code 不可修改，status 空=不修改） */
    void update(HttpApiSaveBody body);

    /** 逻辑删除 */
    void delete(Long id);

    /**
     * 按编码调用第三方接口：替换 url/bodyTemplate 中 {param} 后发起请求，
     * 写 lc_http_log（成功失败都记），返回 { success, costMs, respBody(截断 2k) }
     */
    HttpCallResultVO call(String code, HttpCallBody body);

    /** 调用日志分页（apiCode 精确过滤，createTime 倒序） */
    PageResult<LcHttpLog> pageLog(HttpLogQuery query);
}
