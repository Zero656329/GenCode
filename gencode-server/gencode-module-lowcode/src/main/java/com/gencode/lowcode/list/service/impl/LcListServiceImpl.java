package com.gencode.lowcode.list.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.datasource.service.LcDatasourceService;
import com.gencode.lowcode.list.dto.ColumnVO;
import com.gencode.lowcode.list.dto.ListCreateBody;
import com.gencode.lowcode.list.dto.ListPublishedVO;
import com.gencode.lowcode.list.dto.ListQuery;
import com.gencode.lowcode.list.dto.ListSaveBody;
import com.gencode.lowcode.list.entity.LcList;
import com.gencode.lowcode.list.mapper.LcListMapper;
import com.gencode.lowcode.list.service.LcListService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 列表定义服务实现：业务规则对齐 M1 form 域（code 唯一含已删除、发布快照、状态流转、删除保护）
 */
@Service
@RequiredArgsConstructor
public class LcListServiceImpl implements LcListService {

    /** 状态：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 状态：停用 */
    private static final int STATUS_DISABLED = 2;

    /** 表名标识符白名单（反读表列用） */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 合法数据源类型 */
    private static final Set<String> SOURCE_TYPES = Set.of("TABLE", "SQL", "API");

    private final LcListMapper listMapper;
    private final LcDatasourceService datasourceService;

    @Override
    public PageResult<LcList> page(ListQuery query) {
        LambdaQueryWrapper<LcList> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(LcList::getCode, query.getKeyword())
                    .or().like(LcList::getName, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(LcList::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LcList::getCreateTime);
        IPage<LcList> page = listMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public LcList detail(Long id) {
        LcList list = listMapper.selectById(id);
        if (list == null) {
            throw new BizException("列表不存在");
        }
        return list;
    }

    @Override
    public void create(ListCreateBody body) {
        checkCodeUnique(body.getCode());
        LcList list = new LcList();
        list.setCode(body.getCode());
        list.setName(body.getName());
        list.setSourceType(normalizeSourceType(body.getSourceType()));
        list.setSourceConfig(StrUtil.trimToNull(body.getSourceConfig()));
        list.setListSchema(StrUtil.trimToNull(body.getListSchema()));
        list.setRemark(body.getRemark());
        list.setStatus(STATUS_DRAFT);
        list.setVersion(0);
        listMapper.insert(list);
    }

    @Override
    public void save(ListSaveBody body) {
        if (listMapper.selectById(body.getId()) == null) {
            throw new BizException("列表不存在");
        }
        LcList list = new LcList();
        list.setId(body.getId());
        list.setName(body.getName());
        // ListSaveBody 无 code 字段，code 天然不会被修改
        if (StrUtil.isNotBlank(body.getSourceType())) {
            list.setSourceType(normalizeSourceType(body.getSourceType()));
        }
        list.setSourceConfig(body.getSourceConfig());
        list.setListSchema(body.getListSchema());
        list.setRemark(body.getRemark());
        listMapper.updateById(list);
    }

    @Override
    public void delete(Long id) {
        LcList list = listMapper.selectById(id);
        if (list == null) {
            throw new BizException("列表不存在");
        }
        if (list.getStatus() != null && list.getStatus() == STATUS_PUBLISHED) {
            throw new BizException("已发布列表请先停用");
        }
        listMapper.deleteById(id);
    }

    @Override
    public void publish(Long id) {
        LcList list = listMapper.selectById(id);
        if (list == null) {
            throw new BizException("列表不存在");
        }
        if (StrUtil.isBlank(list.getSourceConfig())) {
            throw new BizException("请先配置数据源");
        }
        if (StrUtil.isBlank(list.getListSchema())) {
            throw new BizException("请先设计列表结构");
        }
        list.setVersion(list.getVersion() == null ? 1 : list.getVersion() + 1);
        // 快照：JSON{sourceConfig, listSchema}（两个字段原始 JSON 字符串作为值存储）
        list.setPublishedSchema(JSONUtil.createObj()
                .set("sourceConfig", list.getSourceConfig())
                .set("listSchema", list.getListSchema())
                .toString());
        list.setStatus(STATUS_PUBLISHED);
        list.setPublishTime(LocalDateTime.now());
        listMapper.updateById(list);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || status < STATUS_DRAFT || status > STATUS_DISABLED) {
            throw new BizException("非法的列表状态");
        }
        if (listMapper.selectById(id) == null) {
            throw new BizException("列表不存在");
        }
        LcList list = new LcList();
        list.setId(id);
        list.setStatus(status);
        listMapper.updateById(list);
    }

    @Override
    public ListPublishedVO getPublished(String code) {
        LcList list = listMapper.selectOne(new LambdaQueryWrapper<LcList>()
                .eq(LcList::getCode, code)
                .eq(LcList::getStatus, STATUS_PUBLISHED));
        if (list == null || StrUtil.isBlank(list.getPublishedSchema())) {
            throw new BizException("列表不存在或未发布");
        }
        JSONObject snapshot = JSONUtil.parseObj(list.getPublishedSchema());
        ListPublishedVO vo = new ListPublishedVO();
        vo.setCode(list.getCode());
        vo.setName(list.getName());
        vo.setVersion(list.getVersion());
        vo.setSourceType(list.getSourceType());
        vo.setSourceConfig(snapshot.getStr("sourceConfig"));
        vo.setListSchema(snapshot.getStr("listSchema"));
        return vo;
    }

    @Override
    public List<ColumnVO> columns(Long datasourceId, String tableName) {
        if (StrUtil.isBlank(tableName) || !IDENTIFIER.matcher(tableName).matches()) {
            throw new BizException("非法的表名");
        }
        JdbcTemplate jdbc = datasourceService.getJdbcTemplate(datasourceId);
        return jdbc.execute((ConnectionCallback<List<ColumnVO>>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            List<ColumnVO> result = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), conn.getSchema(), tableName, "%")) {
                while (rs.next()) {
                    ColumnVO vo = new ColumnVO();
                    vo.setColumnName(rs.getString("COLUMN_NAME"));
                    vo.setTypeName(rs.getString("TYPE_NAME"));
                    vo.setComment(rs.getString("REMARKS"));
                    result.add(vo);
                }
            }
            return result;
        });
    }

    /** 校验并归一化数据源类型 */
    private String normalizeSourceType(String sourceType) {
        String type = StrUtil.isBlank(sourceType) ? "TABLE" : sourceType.trim().toUpperCase();
        if (!SOURCE_TYPES.contains(type)) {
            throw new BizException("非法的数据源类型，仅支持 TABLE/SQL/API");
        }
        return type;
    }

    /**
     * 编码查重：含已逻辑删除的行（数据库唯一键不区分 deleted，已删除记录仍占用编码）
     */
    private void checkCodeUnique(String code) {
        if (listMapper.countByCodeIncludeDeleted(code) > 0) {
            throw new BizException("列表编码已存在");
        }
    }
}
