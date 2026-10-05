# GenCode 总体架构

## 1. 分层架构（对应需求总纲）

```
┌─────────────────────────────────────────────────────────────┐
│ 设计态  应用/页面/表单/列表/流程/报表/大屏 设计器（gencode-ui）      │
├─────────────────────────────────────────────────────────────┤
│ 元数据层 应用·页面·表单Schema·列表Schema·数据源·数据集·API·字典    │
│         权限·流程·报表·版本（lowcode_meta_*，二期起）             │
├─────────────────────────────────────────────────────────────┤
│ 运行时   表单渲染引擎·列表渲染引擎·规则引擎·动态SQL引擎·流程引擎    │
│ 引擎     报表引擎·脚本引擎（gencode-module-lowcode，二期起）       │
├─────────────────────────────────────────────────────────────┤
│ 平台底座 认证鉴权(Sa-Token)·用户组织角色·菜单按钮·租户·字典·日志   │
│         （一期已完成：gencode-module-system + gencode-framework）│
├─────────────────────────────────────────────────────────────┤
│ 集成层   REST API·Webhook·第三方HTTP·数据库·Excel/CSV·MQ·SSO     │
│ 支撑层   Redis·Flowable·Quartz/XXL-Job·MinIO/OSS·监控告警        │
└─────────────────────────────────────────────────────────────┘
```

## 2. 后端模块划分

| 模块 | 职责 | 状态 |
| --- | --- | --- |
| gencode-common | R 响应、分页、异常、BaseEntity、工具 | ✅ 一期 |
| gencode-framework | Sa-Token 配置、MyBatis-Plus（分页/租户/审计填充）、操作日志切面、Jackson（Long→String） | ✅ 一期 |
| gencode-module-system | 用户/角色/菜单/部门/租户/字典/参数/登录日志/操作日志/认证/仪表盘 | ✅ 一期 |
| gencode-module-flow | Flowable 7 接入（流程定义查询，二期做设计器与审批 API） | 🚧 骨架 |
| gencode-module-lowcode | 元数据 + 表单/列表渲染引擎 + 动态 SQL（二期） | 📅 规划 |
| gencode-module-report | 报表/大屏数据集与图表数据接口（四期） | 📅 规划 |
| gencode-module-generator | 代码生成（吸收 ZCodeProject/flow-codegen-server 五方言方案，五期） | 📅 规划 |

## 3. 关键机制

### 3.1 认证与四层权限
- Sa-Token：`Authorization: Bearer <token>`；同账号互斥登录（`is-concurrent=false`）；登录失败 5 次锁定 10 分钟。
- 菜单权限：`GET /system/menu/tree` 渲染侧边栏；按钮权限：前端 `v-perm` 指令（perms 匹配，`*:*:*` 通配超管）；接口权限：后端 `@SaCheckPermission`；数据权限：`sys_role.data_scope`（1全部/2本部门及以下/3本部门/4仅本人），二期在 Mapper 层拼接 SQL 条件实现。

### 3.2 多租户（一期：字段隔离）
- MyBatis-Plus `TenantLineInnerInterceptor` 自动注入/过滤 `tenant_id`；忽略表：`sys_menu`、`sys_role_menu`、`sys_user_role`、`sys_tenant`、`act_*`。
- 租户上下文：登录时确定，存 ThreadLocal；Schema 隔离/独立库隔离在数据源路由层扩展（三期规划）。

### 3.3 数据库方言适配
- 分页方言由 `gencode.db-type` 配置驱动（mysql 默认，oracle / postgresql / dm / kingbase_es 可选），MyBatis-Plus `PaginationInnerInterceptor(DbType)` 按方言生成 SQL。
- **MySQL 8（一期默认）**：`sql/gencode-mysql.sql` + 后端启动自动建表（幂等）。
- **Oracle（脚本已交付）**：`sql/gencode-oracle.sql`（11g+，DROP+CREATE 可重跑，全列 COMMENT，NUMBER(20) 兼容 Long 实体）；ojdbc11 驱动已内置；注意 Oracle 保留字——`sys_file` 字节列统一命名 `file_size`。多库统一命名规范由此而来：列名避开所有目标库保留字。
- 代码生成模块吸收 `flow-codegen-server` 成果：保留字转义（`` `order` `` / `"ORDER"` / `[order]`）、分页 DbType、DDL 类型映射，支持 MySQL/PostgreSQL/Oracle/SQLServer/达梦，人大金仓按 PG 方言适配（达梦驱动 `com.dameng:DmJdbcDriver18`）。
- 动态 SQL 引擎（二期）一律参数化占位符，杜绝注入。

### 3.4 Redis 策略（一期已接入）
- **Sa-Token 会话**：`sa-token-redis-jackson`（AutoConfiguration.imports 自动注册 `SaTokenDaoRedisJackson`，Redis 连接来自 `spring-boot-starter-data-redis`），登录会话、Token 映射全部落 Redis——后端重启 token 不失效，集群部署可共享会话。键前缀为 token-name（`Authorization:login:token:* / Authorization:login:session:*`）。
- **验证码**：`gencode:captcha:{captchaId}`，5 分钟 TTL，一次性（校验即删）。
- **登录锁定**：失败计数 `gencode:login:fail:{username}`（10 分钟滑窗）+ 锁定标记 `gencode:login:lock:{username}`；仅"用户不存在/密码错误"计入。
- Redis 不可用时后端启动失败（与 MySQL 同等待遇）；生产换地址只需改 `spring.data.redis.*`。

### 3.5 文件存储（一期已接入 MinIO）
- 便携版 MinIO：API `:9000`、控制台 `:9001`（root：minio/minio123456），数据目录 `D:\minio\data`。
- 后端 `gencode-module-system/file` 域：`POST /system/file/upload`、`GET /system/file/page`、`GET /system/file/{id}/download`、`DELETE /system/file/{id}`；二进制进 MinIO（桶 `gencode` 自动创建），元数据进 `sys_file`（租户隔离 + 逻辑删除 + 审计字段）。
- 对象名：`{yyyyMM}/{uuid}.{suffix}`；上传大小上限 100MB（`spring.servlet.multipart`）。
- MinIO 未启动不影响后端启动，仅在调用文件接口时报"请确认 MinIO 服务可用"；生产切换只改 `minio.*` 配置（OSS/S3 兼容）。
- 二期表单/列表设计器的附件、图片、富文本上传将复用该文件服务。

## 4. 前端架构

- Vite + Vue3 + TypeScript + Pinia + Vue Router + Ant Design Vue 4 + ECharts。
- `src/api` 按领域分模块，`utils/request.ts` 统一拦截（Token 注入、401 跳转、错误提示）。
- 布局：`layouts/BasicLayout.vue`（Sider 动态菜单 + Header 面包屑/用户区）。
- 路由与后端菜单树一致（`component` 字符串 → `import.meta.glob` 动态加载 `views/**`）。
- 二期新增：表单设计器（拖拽 + JSON Schema）、列表设计器、页面渲染引擎（独立路由 `/app/:code` 运行发布页面）。

## 5. 部署形态

- 开发：docker-compose（MySQL+Redis）+ 本地 mvn/npm。
- 生产（规划）：多阶段 Dockerfile（前端 nginx 静态 + 后端 Java 21 镜像），K8s Helm Chart，Actuator + Prometheus + Grafana 监控，SkyWalking 链路。
