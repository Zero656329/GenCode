package com.gencode.lowcode.http.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.framework.tenant.TenantContext;
import com.gencode.lowcode.http.dto.HttpApiCreateBody;
import com.gencode.lowcode.http.dto.HttpApiQuery;
import com.gencode.lowcode.http.dto.HttpApiSaveBody;
import com.gencode.lowcode.http.dto.HttpCallBody;
import com.gencode.lowcode.http.dto.HttpCallResultVO;
import com.gencode.lowcode.http.dto.HttpLogQuery;
import com.gencode.lowcode.http.entity.LcHttpApi;
import com.gencode.lowcode.http.entity.LcHttpLog;
import com.gencode.lowcode.http.mapper.LcHttpApiMapper;
import com.gencode.lowcode.http.mapper.LcHttpLogMapper;
import com.gencode.lowcode.http.service.LcHttpApiService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 第三方 HTTP 接口服务实现：租户插件自动隔离，雪花 ID 由 MP ASSIGN_ID 生成。
 * 调用侧每次按配置的 timeoutMs 新建 RestTemplate（SimpleClientHttpRequestFactory），
 * 计时与调用日志（lc_http_log）在 try/finally 中保证成功失败都记录。
 */
@Service
@RequiredArgsConstructor
public class LcHttpApiServiceImpl implements LcHttpApiService {

    private static final Logger log = LoggerFactory.getLogger(LcHttpApiServiceImpl.class);

    /** 状态：启用（仅启用的接口可调用） */
    private static final int STATUS_ENABLED = 0;
    /** 默认超时（毫秒） */
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    /** 日志响应/请求体截断长度（字符） */
    private static final int MAX_BODY_LENGTH = 2000;
    /** lc_http_log.url 列宽（VARCHAR(500)） */
    private static final int MAX_URL_LENGTH = 500;

    private final LcHttpApiMapper apiMapper;
    private final LcHttpLogMapper logMapper;

    // ==================== 定义 CRUD ====================

    @Override
    public PageResult<LcHttpApi> page(HttpApiQuery query) {
        LambdaQueryWrapper<LcHttpApi> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(LcHttpApi::getCode, query.getKeyword())
                    .or().like(LcHttpApi::getName, query.getKeyword()));
        }
        wrapper.orderByDesc(LcHttpApi::getCreateTime);
        IPage<LcHttpApi> page = apiMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public LcHttpApi detail(Long id) {
        LcHttpApi api = apiMapper.selectById(id);
        if (api == null) {
            throw new BizException("接口不存在");
        }
        return api;
    }

    @Override
    public void create(HttpApiCreateBody body) {
        checkMethod(body.getMethod());
        checkHeadersJson(body.getHeadersJson());
        if (apiMapper.countByCodeIncludeDeleted(body.getCode()) > 0) {
            throw new BizException("接口编码已存在");
        }
        LcHttpApi api = new LcHttpApi();
        BeanUtil.copyProperties(body, api);
        api.setStatus(STATUS_ENABLED);
        if (api.getTimeoutMs() == null || api.getTimeoutMs() <= 0) {
            api.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        }
        apiMapper.insert(api);
    }

    @Override
    public void update(HttpApiSaveBody body) {
        checkMethod(body.getMethod());
        checkHeadersJson(body.getHeadersJson());
        if (apiMapper.selectById(body.getId()) == null) {
            throw new BizException("接口不存在");
        }
        LcHttpApi api = new LcHttpApi();
        BeanUtil.copyProperties(body, api);
        // HttpApiSaveBody 无 code 字段，code 天然不会被修改；status/timeout 为空时 updateById 自动忽略
        if (api.getTimeoutMs() != null && api.getTimeoutMs() <= 0) {
            api.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        }
        apiMapper.updateById(api);
    }

    @Override
    public void delete(Long id) {
        if (apiMapper.selectById(id) == null) {
            throw new BizException("接口不存在");
        }
        apiMapper.deleteById(id);
    }

    // ==================== 调用 ====================

    @Override
    public HttpCallResultVO call(String code, HttpCallBody body) {
        LcHttpApi api = apiMapper.selectOne(new LambdaQueryWrapper<LcHttpApi>()
                .eq(LcHttpApi::getCode, code)
                .eq(LcHttpApi::getStatus, STATUS_ENABLED));
        if (api == null) {
            throw new BizException("接口不存在或已停用");
        }
        Map<String, Object> params = body == null || body.getParams() == null ? Map.of() : body.getParams();
        boolean isPost = "POST".equalsIgnoreCase(api.getMethod());
        int timeoutMs = api.getTimeoutMs() == null || api.getTimeoutMs() <= 0
                ? DEFAULT_TIMEOUT_MS : api.getTimeoutMs();

        String finalUrl = api.getUrl();
        String requestBody = null;
        boolean success = false;
        String respBody = null;
        long costMs = 0;
        long start = System.currentTimeMillis();
        try {
            // 1. url 中 {param} 替换；GET 再把 params 追加为查询串
            finalUrl = applyPlaceholders(api.getUrl(), params);
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(finalUrl);
            if (!isPost) {
                params.forEach((key, value) -> {
                    if (value != null) {
                        builder.queryParam(key, String.valueOf(value));
                    }
                });
            }
            URI uri = builder.build().encode().toUri();

            // 2. 请求头 + POST 请求体（{param} 替换后的 bodyTemplate）
            HttpHeaders headers = buildHeaders(api.getHeadersJson());
            if (isPost) {
                requestBody = applyPlaceholders(api.getBodyTemplate(), params);
                if (headers.getContentType() == null) {
                    headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
                }
            }
            HttpEntity<String> entity = new HttpEntity<>(isPost ? requestBody : null, headers);

            // 3. 发起调用（超时取配置）
            RestTemplate restTemplate = buildRestTemplate(timeoutMs);
            ResponseEntity<String> response = restTemplate.exchange(uri,
                    HttpMethod.valueOf(isPost ? "POST" : "GET"), entity, String.class);
            success = response.getStatusCode().is2xxSuccessful();
            respBody = response.getBody();
        } catch (HttpStatusCodeException e) {
            // 4xx/5xx：保留第三方响应体
            respBody = e.getResponseBodyAsString();
        } catch (Exception e) {
            respBody = "调用失败：" + e.getMessage();
        } finally {
            costMs = System.currentTimeMillis() - start;
            saveCallLog(api, finalUrl, requestBody, success, costMs, respBody);
        }
        return new HttpCallResultVO(success, costMs, truncate(respBody, MAX_BODY_LENGTH));
    }

    @Override
    public PageResult<LcHttpLog> pageLog(HttpLogQuery query) {
        LambdaQueryWrapper<LcHttpLog> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getApiCode())) {
            wrapper.eq(LcHttpLog::getApiCode, query.getApiCode());
        }
        wrapper.orderByDesc(LcHttpLog::getCreateTime);
        IPage<LcHttpLog> page = logMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    // ==================== 私有方法 ====================

    /** 每次调用按配置新建 RestTemplate（连接/读超时均取 timeoutMs），字符串编解码统一 UTF-8 */
    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().stream()
                .filter(converter -> converter instanceof StringHttpMessageConverter)
                .forEach(converter ->
                        ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8));
        return restTemplate;
    }

    /** 请求头 JSON → HttpHeaders（调用时解析失败仅告警跳过，保存时已校验格式） */
    private HttpHeaders buildHeaders(String headersJson) {
        HttpHeaders headers = new HttpHeaders();
        if (StrUtil.isBlank(headersJson)) {
            return headers;
        }
        try {
            JSONObject json = JSONUtil.parseObj(headersJson);
            for (String key : json.keySet()) {
                Object value = json.get(key);
                if (value != null) {
                    headers.set(key, String.valueOf(value));
                }
            }
        } catch (Exception e) {
            log.warn("接口请求头 JSON 解析失败，忽略自定义请求头：{}", headersJson);
        }
        return headers;
    }

    /** 将模板中的 {param} 占位符用 params 替换（null 值参数保留原占位符） */
    private String applyPlaceholders(String template, Map<String, Object> params) {
        if (StrUtil.isBlank(template) || params.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /** 写调用日志（try/finally 调入，日志失败不影响调用结果返回） */
    private void saveCallLog(LcHttpApi api, String url, String requestBody,
                             boolean success, long costMs, String respBody) {
        try {
            LcHttpLog entity = new LcHttpLog();
            String tenantId = TenantContext.getTenantId();
            entity.setTenantId(StrUtil.isBlank(tenantId) ? Constants.DEFAULT_TENANT : tenantId);
            entity.setApiCode(api.getCode());
            entity.setMethod(api.getMethod());
            entity.setUrl(truncate(url, MAX_URL_LENGTH));
            entity.setRequestBody(truncate(requestBody, MAX_BODY_LENGTH));
            entity.setSuccess(success);
            entity.setCostMs(costMs);
            entity.setRespBody(truncate(respBody, MAX_BODY_LENGTH));
            entity.setCreateTime(LocalDateTime.now());
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入 HTTP 调用日志失败：apiCode={}", api.getCode(), e);
        }
    }

    /** 请求方法校验：仅 GET/POST */
    private void checkMethod(String method) {
        if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            throw new BizException("请求方法仅支持 GET/POST");
        }
    }

    /** 请求头校验：必须是 JSON 对象（可空） */
    private void checkHeadersJson(String headersJson) {
        if (StrUtil.isNotBlank(headersJson) && !JSONUtil.isTypeJSONObject(headersJson)) {
            throw new BizException("请求头必须是 JSON 对象");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
