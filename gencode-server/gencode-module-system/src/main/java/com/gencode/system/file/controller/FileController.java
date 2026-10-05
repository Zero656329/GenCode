package com.gencode.system.file.controller;

import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.system.file.dto.FileQuery;
import com.gencode.system.file.entity.SysFile;
import com.gencode.system.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件接口（/api/system/file）
 */
@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 文件分页 */
    @GetMapping("/page")
    public R<PageResult<SysFile>> page(FileQuery query) {
        return R.ok(fileService.page(query));
    }

    /** 上传（multipart 字段名 file） */
    @OperLog(module = "文件管理", businessType = "UPLOAD")
    @PostMapping("/upload")
    public R<SysFile> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(fileService.upload(file));
    }

    /** 下载（二进制流，Content-Disposition 携带原始文件名） */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        FileService.DownloadResult d = fileService.download(id);
        String encoded = URLEncoder.encode(d.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType;
        try {
            mediaType = d.contentType() != null ? MediaType.parseMediaType(d.contentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .body(new InputStreamResource(d.stream()));
    }

    /** 删除（移除 MinIO 对象 + 逻辑删除记录） */
    @OperLog(module = "文件管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return R.ok();
    }
}
