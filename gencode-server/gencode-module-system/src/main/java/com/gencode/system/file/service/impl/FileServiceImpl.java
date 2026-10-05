package com.gencode.system.file.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.file.dto.FileQuery;
import com.gencode.system.file.entity.SysFile;
import com.gencode.system.file.mapper.SysFileMapper;
import com.gencode.system.file.service.FileService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文件服务实现：二进制存 MinIO，元数据存 sys_file（租户插件自动隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final SysFileMapper fileMapper;

    @Value("${minio.bucket}")
    private String bucket;

    /** 桶是否已确认存在（进程内缓存，首次上传时检查/创建） */
    private volatile boolean bucketChecked = false;

    @Override
    public SysFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件为空");
        }
        ensureBucket();
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "unnamed");
        String suffix = StrUtil.contains(originalName, '.')
                ? StrUtil.subAfter(originalName, '.', true).toLowerCase() : "";
        String objectName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "/" + IdUtil.fastSimpleUUID() + (StrUtil.isBlank(suffix) ? "" : "." + suffix);
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(in, file.getSize(), -1)
                    .contentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"))
                    .build());
        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败: {}", originalName, e);
            throw new BizException("文件上传失败，请确认 MinIO 服务可用");
        }
        SysFile sf = new SysFile();
        sf.setBucket(bucket);
        sf.setObjectName(objectName);
        sf.setOriginalName(originalName);
        sf.setSuffix(suffix);
        sf.setFileSize(file.getSize());
        sf.setContentType(file.getContentType());
        fileMapper.insert(sf);
        return sf;
    }

    @Override
    public PageResult<SysFile> page(FileQuery query) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .like(StrUtil.isNotBlank(query.getKeyword()), SysFile::getOriginalName, query.getKeyword())
                .orderByDesc(SysFile::getCreateTime);
        IPage<SysFile> page = fileMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    @Override
    public DownloadResult download(Long id) {
        SysFile sf = fileMapper.selectById(id);
        if (sf == null) {
            throw new BizException("文件不存在");
        }
        try {
            InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(sf.getBucket()).object(sf.getObjectName()).build());
            return new DownloadResult(in, sf.getOriginalName(), sf.getContentType());
        } catch (Exception e) {
            log.error("从 MinIO 下载文件失败: {}", sf.getObjectName(), e);
            throw new BizException("文件下载失败，请确认 MinIO 服务可用");
        }
    }

    @Override
    public void delete(Long id) {
        SysFile sf = fileMapper.selectById(id);
        if (sf == null) {
            throw new BizException("文件不存在");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(sf.getBucket()).object(sf.getObjectName()).build());
        } catch (Exception e) {
            log.error("从 MinIO 删除对象失败: {}", sf.getObjectName(), e);
            throw new BizException("文件删除失败，请确认 MinIO 服务可用");
        }
        fileMapper.deleteById(id);
    }

    private synchronized void ensureBucket() {
        if (bucketChecked) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 存储桶: {}", bucket);
            }
            bucketChecked = true;
        } catch (Exception e) {
            log.error("检查/创建 MinIO 存储桶失败", e);
            throw new BizException("MinIO 服务不可用，请先启动 MinIO");
        }
    }
}
