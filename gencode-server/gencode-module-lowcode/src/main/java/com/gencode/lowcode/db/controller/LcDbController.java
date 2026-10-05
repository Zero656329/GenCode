package com.gencode.lowcode.db.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.db.DdlService;
import com.gencode.lowcode.db.dto.ColumnInfo;
import com.gencode.lowcode.db.dto.DdlPreviewVO;
import com.gencode.lowcode.db.dto.TableSpec;
import com.gencode.lowcode.db.dto.TypeMapVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可视化建表（/api/lc/db，契约见 docs/api-contract.md「低代码 /lc（五期 M5）」）
 *
 * <p>安全：表名/列名标识符白名单；仅允许 CREATE TABLE 语义（DDL 全部由服务端内部生成，
 * 不接收裸 DDL 文本）。读操作 lc:db:list，写操作 lc:db:create。</p>
 */
@RestController
@RequestMapping("/lc/db")
@RequiredArgsConstructor
public class LcDbController {

    private final DdlService ddlService;

    /** 逆向读表结构（平台主库，含主键标记） */
    @SaCheckPermission("lc:db:list")
    @GetMapping("/columns")
    public R<List<ColumnInfo>> columns(@RequestParam String tableName) {
        return R.ok(ddlService.columns(tableName));
    }

    /** DDL 预览：按 gencode.db-type 方言生成 CREATE TABLE（含统一约定列） */
    @OperLog(module = "数据管理", businessType = "OTHER")
    @SaCheckPermission("lc:db:create")
    @PostMapping("/ddl/preview")
    public R<DdlPreviewVO> preview(@RequestBody TableSpec body) {
        return R.ok(ddlService.preview(body));
    }

    /** 执行建表（表已存在报错；Oracle/PG/SQLServer 注释语句逐条执行） */
    @OperLog(module = "数据管理", businessType = "CREATE")
    @SaCheckPermission("lc:db:create")
    @PostMapping("/ddl/execute")
    public R<Void> execute(@RequestBody TableSpec body) {
        ddlService.execute(body);
        return R.ok();
    }

    /** 方言类型映射表（供前端下拉） */
    @SaCheckPermission("lc:db:list")
    @GetMapping("/typemap")
    public R<List<TypeMapVO>> typemap() {
        return R.ok(ddlService.typemap());
    }
}
