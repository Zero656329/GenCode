package com.gencode.system.file.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.file.dto.FileQuery;
import com.gencode.system.file.entity.SysFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件服务（MinIO 对象存储）
 */
public interface FileService {

    /** 上传文件并落库元数据 */
    SysFile upload(MultipartFile file);

    /** 分页查询文件元数据 */
    PageResult<SysFile> page(FileQuery query);

    /** 下载：返回流 + 原始文件名 + MIME */
    DownloadResult download(Long id);

    /** 删除：移除 MinIO 对象并逻辑删除记录 */
    void delete(Long id);

    /** 下载结果 */
    record DownloadResult(InputStream stream, String fileName, String contentType) {
    }
}
