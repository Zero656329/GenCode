# GenCode 企业级低代码平台

前后端分离的企业级低代码平台：通过可视化拖拽、配置化表单/列表/流程/报表/大屏，快速搭建业务系统。支持多租户、多数据库（含达梦、人大金仓等国产库）、第三方接口集成、代码生成与运维监控。

**核心闭环**：设计器 → JSON Schema / 元数据 → 权限绑定 → 发布 → 运行时渲染 → 数据/接口/流程 → 日志与监控

## 技术栈

| 层级 | 选型 |
| --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue 4 + Pinia + Vue Router + ECharts |
| 后端 | Java 21 + Spring Boot 3.4.x + MyBatis-Plus（多模块 Maven） |
| 鉴权 | Sa-Token（登录、按钮/接口/数据权限、会话管理） |
| 多租户 | 字段隔离（tenant_id，MyBatis-Plus 租户插件）；预留 Schema/独立库隔离扩展 |
| 流程 | Flowable 7（BPMN 2.0） |
| 数据库 | MySQL（一期），方言适配层预留 PostgreSQL / 达梦 / 人大金仓 |
| 部署 | Docker Compose（MySQL + Redis）、K8s / CI-CD 预留 |

## 仓库结构

```
GenCode/
├── gencode-server/            # 后端（Maven 多模块）
│   ├── gencode-common/        # 通用：R/分页/异常/基础实体
│   ├── gencode-framework/     # 框架：Sa-Token、MyBatis-Plus、租户、日志切面
│   ├── gencode-module-system/ # 系统模块：用户/角色/菜单/部门/租户/字典/参数/日志
│   ├── gencode-module-flow/   # 流程模块：Flowable 接入（一期骨架）
│   └── gencode-server/        # 启动模块（application.yml、SQL 初始化脚本）
├── gencode-ui/                # 前端（Vite + Vue3 + TS + Ant Design Vue）
├── sql/
│   ├── gencode-mysql.sql      # MySQL 建库脚本（DDL + 初始化数据，幂等）
│   └── gencode-oracle.sql     # Oracle 建库脚本（11g+，一次性安装，可重跑）
├── docker-compose.yml         # 一键启动 MySQL + Redis
└── docs/                      # 架构、API 契约、路线图
```

## 快速开始（本地运行，免 Docker）

本机环境已就绪：JDK 21、Maven 3.9.9、Node 20（`D:\nvm\v20.10.0`）、便携版 MySQL 8（`D:\mysql-8.0.28-winx64`，端口 3306，root/root123456，库 gencode，数据目录随软件目录）、便携版 Redis 5（`D:\redis-5.0.14.1`，端口 6379，仅监听本机）、便携版 MinIO（`D:\minio`，API 9000 / 控制台 9001，账号 minio/minio123456，数据 `D:\minio\data`）。Redis 承载 Sa-Token 会话、验证码与登录锁定，MinIO 承载文件存储，**启动平台前需先启动两者**。

### 方式一：一键脚本

双击或运行 [scripts/start-all.bat](scripts/start-all.bat)：依次拉起 **Redis → MinIO → MySQL → 后端 → 前端** 五个窗口，就绪后访问 http://localhost:5173 。

- 已在运行的服务会自动跳过，脚本可重复执行；每个窗口都有中文提示和实时日志，关闭某服务窗口即停止该服务
- 一键停止全部服务：[scripts/stop-all.bat](scripts/stop-all.bat)（按端口精确停止，不影响其他程序）
- 也可单独运行 `start-redis.bat` / `start-minio.bat` / `start-mysql.bat` / `start-backend.bat` / `start-frontend.bat`
- 后端脚本优先用已打包的 jar 直启（约 30 秒就绪）；jar 不存在时自动 `mvn package`
- 注意：脚本为 GBK 编码 + CRLF 换行（Windows cmd 原生格式），请勿用 UTF-8 保存覆盖，否则中文会乱码、解析会出错

### 方式二：手动命令

```bat
:: 0. 启动 Redis（Sa-Token 会话/验证码/登录锁定依赖，端口 6379）
D:\redis-5.0.14.1\redis-server.exe D:\redis-5.0.14.1\redis.conf

:: 0.1 启动 MinIO（文件存储，API 9000 / 控制台 9001）
set MINIO_ROOT_USER=minio&& set MINIO_ROOT_PASSWORD=minio123456
D:\minio\minio.exe server D:\minio\data --address ":9000" --console-address ":9001"

:: 1. 启动 MySQL（首次需建库：bin\mysql -uroot -proot123456 -e "CREATE DATABASE IF NOT EXISTS gencode CHARACTER SET utf8mb4"）
D:\mysql-8.0.28-winx64\bin\mysqld.exe --defaults-file=D:\mysql-8.0.28-winx64\my.ini --console

:: 2. 启动后端（端口 8080，上下文 /api；首次启动自动建表+种子数据，幂等不清库）
cd /d E:\GitHub\GenCode\gencode-server && mvn -q -DskipTests spring-boot:run -pl gencode-server -am

:: 3. 启动前端（端口 5173）
set PATH=D:\nvm\v20.10.0;%PATH%
cd /d E:\GitHub\GenCode\gencode-ui && npm run dev
```

浏览器访问 http://localhost:5173 ，默认账号：**admin / admin123**（超级管理员，租户 000000）。

### 默认账号

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| admin | admin123 | 超级管理员（super_admin） | 全部权限（`*:*:*`），内置不可删除 |
| test | test123456 | 普通用户（common，数据范围=仅本人） | 演示权限分层：可见菜单仅 仪表盘 / 用户管理（只读）/ 登录日志；页面按钮隐藏、写接口返回 403 |

> test 账号创建于本机演示库。若重跑 `sql/gencode-*.sql` 初始化脚本（会清库重建），test 不会被自动恢复：用 admin 登录后在「用户管理」重建（昵称随意、部门研发部、角色勾选"普通用户"），「角色管理 → 普通用户 → 菜单权限」勾选 仪表盘 + 用户管理 + 用户查询 + 登录日志 即可还原演示效果。
接口文档：http://localhost:8080/api/swagger-ui/index.html

### 备选：Docker

```bash
docker compose up -d          # 拉起 MySQL(3306) + Redis(6379)，自动执行 sql/gencode-mysql.sql
```

其他机器部署时：安装 MySQL 8 后创建数据库 `gencode`（utf8mb4）即可，表结构由后端首次启动自动初始化（也可手动执行 `sql/gencode-mysql.sql`）。

### 切换 Oracle（多数据库）

Oracle 驱动已内置（ojdbc11，无需改 pom），三步切换：

1. **初始化**：在目标 Schema 执行 [sql/gencode-oracle.sql](sql/gencode-oracle.sql)（DDL + 注释 + 索引 + 种子数据，一次性安装，可重复执行；重复执行会先 DROP 清空）。
2. **数据源**：`application.yml` 换成 Oracle 连接（文件内有注释示例）：`url: jdbc:oracle:thin:@//host:1521/服务名`、`driver-class-name: oracle.jdbc.OracleDriver`。
3. **方言与初始化**：`gencode.db-type: oracle`（分页语句按方言生成）；`spring.sql.init.mode: never`（自动建表仅适用于 MySQL）。

实体与业务代码零改动（雪花 ID 列为 NUMBER(20)，JDBC 兼容 Long）。达梦/人大金仓同模式：执行对应方言脚本 + 换驱动 + 改 `gencode.db-type`（dm / kingbase_es）。注意 `sys_file` 字节列统一命名为 `file_size`（`size` 是 Oracle 保留字）。

## 已实现功能（一期 · 平台底座）

- 登录认证（Sa-Token，图形验证码、BCrypt 密码、互斥登录、登录日志；**会话持久化到 Redis**，后端重启 token 不失效，验证码与失败锁定同样走 Redis）
- 文件存储（MinIO）：上传/下载/删除 REST API，元数据落 `sys_file`（租户隔离、审计字段），桶 `gencode` 自动创建
- 四层权限的底座：菜单权限（动态菜单树）、按钮权限（v-perm 指令 + perms）、接口权限（@SaCheckPermission）、数据权限字段（data_scope 预留）
- 系统管理：用户、角色（分配菜单）、菜单（树形）、部门（树形）、租户、字典、参数配置、登录/操作日志
- 多租户字段隔离（tenant_id 自动注入/过滤，可按表忽略）
- 多模块脚手架 + Flowable 模块接入位 + ECharts 仪表盘
- 后端 OpenAPI 文档、统一 R 响应、全局异常、操作日志切面

## 路线图

见 [docs/roadmap.md](docs/roadmap.md)：二期表单/列表设计器与渲染引擎、三期 Flowable 审批流、四期报表大屏、五期代码生成与国产库适配、六期可观测性。
