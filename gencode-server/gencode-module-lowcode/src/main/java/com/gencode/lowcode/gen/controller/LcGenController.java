package com.gencode.lowcode.gen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.gen.GenService;
import com.gencode.lowcode.gen.dto.GenFileVO;
import com.gencode.lowcode.gen.dto.GenPreviewBody;
import com.gencode.lowcode.gen.dto.GenTableVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 代码生成（/api/lc/gen，契约见 docs/api-contract.md「低代码 /lc（五期 M5）」）
 *
 * <p>读操作 lc:gen:list，下载 lc:gen:download；preview/download 返回同一文件集
 * （Java Entity/Mapper/Service/Controller/MapperXML + Vue 列表页/api.ts + 菜单 SQL），
 * download 打包为 zip 以 application/octet-stream attachment 输出。</p>
 */
@RestController
@RequestMapping("/lc/gen")
@RequiredArgsConstructor
public class LcGenController {

    private final GenService genService;

    /** 可选表清单（平台主库，keyword 模糊匹配表名/注释） */
    @SaCheckPermission("lc:gen:list")
    @GetMapping("/tables")
    public R<List<GenTableVO>> tables(@RequestParam(required = false) String keyword) {
        return R.ok(genService.tables(keyword));
    }

    /** 生成文件集预览 */
    @OperLog(module = "代码生成", businessType = "OTHER")
    @SaCheckPermission("lc:gen:list")
    @PostMapping("/preview")
    public R<List<GenFileVO>> preview(@RequestBody GenPreviewBody body) {
        return R.ok(genService.preview(body));
    }

    /** 打包下载 zip（application/octet-stream，attachment） */
    @OperLog(module = "代码生成", businessType = "EXPORT")
    @SaCheckPermission("lc:gen:download")
    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestBody GenPreviewBody body) {
        byte[] zip = genService.download(body);
        String bizName = body != null && body.getOptions() != null
                && StrUtil.isNotBlank(body.getOptions().getBusinessName())
                ? body.getOptions().getBusinessName().trim()
                : "gencode-gen";
        String encoded = URLEncoder.encode(bizName + ".zip", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }
}
