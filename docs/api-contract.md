# GenCode API 契约（一期 · 平台底座）

> 后端与前端共同遵守的接口契约。修改任何接口必须先改本文档。

## 全局约定

- **Base URL**：后端 `context-path=/api`，即所有接口以 `/api` 开头；前端 axios `baseURL='/api'`，开发环境由 Vite 代理 `http://localhost:8080`。
- **响应包装**：所有接口返回 `R<T>`：

```json
{ "code": 0, "msg": "success", "data": { } }
```

  - `code=0` 成功；非 0 失败。**401=未登录/Token 失效**（前端统一跳转登录页）；403=无权限；500=业务异常；`msg` 为可直接展示的提示。
- **分页请求**：`pageNum`（默认 1）、`pageSize`（默认 10）+ 各业务筛选参数。
- **分页响应**：`data = { "list": [...], "total": 123 }`。
- **时间格式**：`yyyy-MM-dd HH:mm:ss`。
- **ID 精度**：雪花 ID 为 Long，后端 Jackson 全局序列化为**字符串**（防 JS 精度丢失），前端一律按字符串处理。
- **status 约定**：`0=正常/启用`，`1=停用`（全部业务表统一）。
- **认证方式**：请求头 `Authorization: Bearer <token>`；未登录接口仅 `/auth/captcha`、`/auth/login`。
- **多租户**：一期租户号登录时携带（默认 `000000`），后端通过 MyBatis-Plus 租户插件自动过滤；前端无需在每个请求里传。
- **枚举/字典**：性别等用字典 `dictType` 渲染，前端通过 `GET /system/dict/data/by-type/{dictType}` 获取。

## 认证 /auth

| 方法 | 路径 | 入参 | 出参 data |
| --- | --- | --- | --- |
| GET | `/auth/captcha` | - | `{ "captchaId": "uuid", "image": "data:image/png;base64,..." }`（算术验证码，5 分钟有效） |
| POST | `/auth/login` | `{ "username": "admin", "password": "admin123", "tenantId": "000000", "captchaId": "...", "captchaCode": "12" }`（tenantId 默认 000000；captchaId/captchaCode 必填） | `{ "token": "...", "user": LoginUser }` |
| GET | `/auth/me` | - | `LoginUser` |
| POST | `/auth/logout` | - | `null` |

**LoginUser 结构**（登录用户信息，前端全局存储）：

```json
{
  "id": "1", "username": "admin", "nickname": "超级管理员", "avatar": null,
  "tenantId": "000000", "deptId": "100", "deptName": "总公司",
  "roles": ["super_admin"], "perms": ["*:*:*"]
}
```

- `roles`：角色 key 数组；`perms`：权限标识数组。**超级管理员返回 `["*:*:*"]` 通配**。
- 登录失败统一返回 `code=500`，msg 区分：用户不存在/密码错误/验证码错误/账号已停用；连续失败 5 次锁定 10 分钟（msg 提示）。

## 系统管理 /system

### 用户 /system/user

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/user/page?keyword=&status=&deptId=&pageNum=&pageSize=` | keyword 匹配 username/nickname/phone；deptId 含子部门 |
| GET | `/system/user/{id}` | 详情，data 含 `roleIds: ["1"]` |
| POST | `/system/user` | body：`{ username, nickname, password, deptId, phone, email, status, remark, roleIds }`（password 必填） |
| PUT | `/system/user` | 同上（id 必填，password 可空=不修改，roleIds 可空=不动） |
| DELETE | `/system/user/{id}` | 逻辑删除；不允许删除内置 admin（id=1） |
| PUT | `/system/user/{id}/status/{status}` | 启用/停用 |
| PUT | `/system/user/{id}/password` | body `{ "password": "新密码" }` 重置密码 |

### 角色 /system/role

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/role/page?keyword=&status=&pageNum=&pageSize=` | keyword 匹配 name/roleKey |
| GET | `/system/role/list/all` | 不分页（下拉用），data 为数组 |
| GET | `/system/role/{id}` | 详情，data 含 `menuIds: []` |
| POST | `/system/role` | `{ name, roleKey, sort, dataScope, status, remark }` |
| PUT | `/system/role` | 同上（内置角色 id=1/2 不允许修改 roleKey、不允许删除） |
| DELETE | `/system/role/{id}` | 逻辑删除并清理关联 |
| PUT | `/system/role/{id}/menus` | body `{ "menuIds": ["1","100"] }` 保存角色菜单 |
| PUT | `/system/role/{id}/status/{status}` | 启用/停用 |

### 菜单 /system/menu

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/menu/tree?name=&status=` | **完整树**，节点结构见下；name 模糊过滤 |
| GET | `/system/menu/{id}` | 详情 |
| POST | `/system/menu` | `{ parentId, name, path, component, menuType(M/C/F), perms, icon, sort, visible, status }` |
| PUT | `/system/menu` | 同上；不允许把父级设为自己或自己的子孙 |
| DELETE | `/system/menu/{id}` | 有子节点或已分配角色时报错 |

菜单树节点：`{ id, parentId, name, path, component, menuType, perms, icon, sort, visible, status, children: [] }`。
**侧边栏数据源**：前端登录后调用 `GET /system/menu/tree`，取 `menuType in (M,C) && visible=0 && status=0` 的节点渲染；C 节点 `path` 即前端路由 path，`component` 即 views 下组件路径，`icon` 为 Ant Design Vue 图标名（如 `UserOutlined`，动态解析自 `@ant-design/icons-vue`）。

### 部门 /system/dept

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/dept/tree?name=&status=` | 完整树，节点含 `{ id, parentId, ancestors, name, orderNo, leader, phone, email, status, children, createTime }` |
| POST / PUT | `/system/dept` | `{ parentId, name, orderNo, leader, phone, email, status, remark }`（后端维护 ancestors） |
| DELETE | `/system/dept/{id}` | 有子部门或有用户时报错 |

### 字典 /system/dict/type 与 /system/dict/data

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/dict/type/page?keyword=&status=&pageNum=&pageSize=` | keyword 匹配 name/dictType |
| GET | `/system/dict/type/all` | 不分页，下拉用 |
| POST / PUT / DELETE | `/system/dict/type`（`/system/dict/type/{id}`） | `{ name, dictType, status, remark }`；删除时级联删数据 |
| GET | `/system/dict/data/page?dictType=&keyword=&pageNum=&pageSize=` | 按类型分页 |
| GET | `/system/dict/data/by-type/{dictType}` | **不分页**，data 为 `[{ label, value, sort }]`（表单/列表渲染用，可缓存） |
| POST / PUT / DELETE | `/system/dict/data`（`/system/dict/data/{id}`） | `{ dictType, label, value, sort, isDefault, status, remark }` |

### 参数配置 /system/config

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/config/page?keyword=&status=&pageNum=&pageSize=` | keyword 匹配 name/key |
| GET | `/system/config/key/{key}` | data 直接为字符串值 |
| POST / PUT / DELETE | `/system/config`（`/system/config/{id}`） | `{ configName, configKey, configValue, status, remark }` |

### 租户 /system/tenant

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/tenant/page?keyword=&status=&pageNum=&pageSize=` | keyword 匹配 name/tenantId |
| POST | `/system/tenant` | `{ tenantId, name, contactPhone, status, expireTime, remark }`；tenantId 唯一 |
| PUT | `/system/tenant` | 同上 |
| DELETE | `/system/tenant/{id}` | 内置租户 000000 不允许删除 |
| PUT | `/system/tenant/{id}/status/{status}` | 启用/停用 |

### 日志 /system/log

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/system/log/login/page?username=&status=&ip=&beginTime=&endTime=&pageNum=&pageSize=` | 登录日志分页 |
| GET | `/system/log/oper/page?title=&status=&operName=&beginTime=&endTime=&pageNum=&pageSize=` | 操作日志分页 |

日志行结构：登录日志 `{ id, username, ip, browser, os, status, msg, loginTime }`；操作日志 `{ id, title, businessType, method, requestMethod, operUrl, operParam, operName, ip, status, errorMsg, costMs, operTime }`。

## 仪表盘 /dashboard

| 方法 | 路径 | 出参 data |
| --- | --- | --- |
| GET | `/dashboard/stats` | `{ "userCount": 1, "roleCount": 2, "deptCount": 3, "tenantCount": 1, "menuCount": 41, "loginTrend": [{ "date": "2026-09-24", "count": 5 }], "loginTrend7d": [0,0,0,0,0,0,0], "latestLogins": [{ "username", "ip", "status", "msg", "loginTime" }] }` |

- `loginTrend`：最近 7 天登录趋势（按日聚合，含无数据的日期，date 升序）。
- `latestLogins`：最近 10 条登录日志（供首页表格）。

### 文件 /system/file（MinIO 对象存储）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/system/file/upload` | multipart 字段名 `file`；data 为 SysFile 记录 |
| GET | `/system/file/page?keyword=&pageNum=&pageSize=` | keyword 匹配原始文件名，按创建时间倒序 |
| GET | `/system/file/{id}/download` | **二进制流**（非 R 包装），Content-Disposition 携带 UTF-8 编码的原始文件名 |
| DELETE | `/system/file/{id}` | 移除 MinIO 对象 + 逻辑删除记录 |

SysFile 结构：`{ id, tenantId, bucket, objectName, originalName, suffix, fileSize, contentType, createTime }`。
存储约定：桶 `gencode`（不存在自动创建），对象名 `{yyyyMM}/{uuid}.{suffix}`；MinIO 不可用时后端可启动，上传/下载返回"请确认 MinIO 服务可用"。依赖本地 MinIO：9000 API / 9001 控制台（root 账号 minio / minio123456）。

## 低代码 /lc（二期 M1：表单）

### 表单定义 /lc/form

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/lc/form/page?keyword=&status=&pageNum=&pageSize=` | keyword 匹配 code/name |
| GET | `/lc/form/{id}` | 详情（含 schemaJson） |
| POST | `/lc/form` | `{ code, name, remark }` 新建草稿（schemaJson 置空，version=0，status=0） |
| PUT | `/lc/form` | `{ id, name, remark, schemaJson }` 保存设计（schemaJson 为 JSON 字符串） |
| DELETE | `/lc/form/{id}` | 逻辑删除 |
| PUT | `/lc/form/{id}/publish` | 发布：version+1，published_schema 快照=当前 schema_json，status=1 |
| PUT | `/lc/form/{id}/status/{status}` | 启用/停用（1=已发布 2=停用 0=草稿；仅改状态不产生快照） |
| GET | `/lc/form/publish/{code}` | **运行时接口**：返回 `{ code, name, version, schemaJson }`（未发布过报"表单未发布"） |

LcForm 结构：`{ id, tenantId, code, name, schemaJson, status(0草稿 1已发布 2停用), version, publishedSchema, publishTime, remark, createTime }`。code 全局唯一（重复报"表单编码已存在"）。内置保护：已发布表单删除时提示先停用；code 不可修改。

### 表单数据 /lc/form/data

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/lc/form/data/{code}` | 填报提交，body `{ "data": { ...字段值 } }`（服务端整体转 JSON 字符串存 data_json；表单须已发布） |
| GET | `/lc/form/data/page?formCode=&pageNum=&pageSize=` | 填报数据分页，list 元素 `{ id, formCode, dataJson, createBy, createTime }` |

## 流程模块 /flow（一期骨架）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/flow/definition/list?name=&pageNum=&pageSize=` | Flowable 流程定义分页（key/name/version/deployTime），Flowable 表未就绪时返回空页不报错 |

## 错误处理

- 参数校验失败（`@Validated`）：code=500，msg 为首个校验错误信息。
- 重复键（用户名/角色 key/字典 type/参数 key/租户 id 冲突）：code=500，msg 中文提示。
- 所有异常被全局处理器捕获，永不返回堆栈。
