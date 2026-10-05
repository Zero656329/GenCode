-- =====================================================================
-- GenCode 企业级低代码平台 · Oracle 初始化脚本（Oracle 11g 及以上）
-- 对应 MySQL 版：sql/gencode-mysql.sql（表结构与种子数据完全一致）
--
-- 使用方式（一次性安装，可重复执行）：
--   1. 用 sqlplus / Navicat / DBeaver 等连接目标 Schema 执行本脚本
--   2. 已存在的表会先 DROP（PURGE），重复执行会清空业务数据，请勿连生产
--   3. 后端切换 Oracle：
--      - pom 已内置 ojdbc11 驱动，无需改动
--      - application.yml：数据源 url/driver 换成 Oracle（见文件内注释示例）
--      - application.yml：gencode.db-type 改为 oracle（分页方言）
--      - spring.sql.init.mode 改为 never（本脚本已初始化，MySQL 版自动建表脚本勿用）
--
-- 类型映射：BIGINT→NUMBER(20)，TINYINT→NUMBER(3)，INT→NUMBER(10)，
--          VARCHAR→VARCHAR2(n CHAR)，DATETIME→DATE，TEXT→CLOB
-- 约定：status 0=正常/启用 1=停用；deleted 0=未删除 1=已删除（逻辑删除）；
--      tenant_id '000000' 为系统租户；id 为雪花 ID（NUMBER(20)，Java 实体仍为 Long）
-- =====================================================================

SET DEFINE OFF

-- ---------------------------------------------------------------------
-- 清理旧表（存在则 DROP，错误 -942 = 表不存在则忽略）
-- ---------------------------------------------------------------------
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_file PURGE';     EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_oper_log PURGE';  EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_login_log PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_config PURGE';    EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_dict_data PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_dict_type PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_role_menu PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_menu PURGE';      EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_user_role PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_role PURGE';      EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_user PURGE';      EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_dept PURGE';      EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE sys_tenant PURGE';    EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /

-- ---------------------------------------------------------------------
-- 1. 租户表
-- ---------------------------------------------------------------------
CREATE TABLE sys_tenant (
  id            NUMBER(20)      NOT NULL,
  tenant_id     VARCHAR2(12 CHAR)  DEFAULT '000000' NOT NULL,
  name          VARCHAR2(100 CHAR) NOT NULL,
  contact_phone VARCHAR2(20 CHAR),
  status        NUMBER(3)       DEFAULT 0 NOT NULL,
  expire_time   DATE,
  remark        VARCHAR2(500 CHAR),
  create_by     VARCHAR2(64 CHAR),
  create_time   DATE,
  update_by     VARCHAR2(64 CHAR),
  update_time   DATE,
  deleted       NUMBER(3)       DEFAULT 0 NOT NULL,
  CONSTRAINT uk_sys_tenant_tid UNIQUE (tenant_id)
);

COMMENT ON TABLE sys_tenant IS '租户表';
COMMENT ON COLUMN sys_tenant.id IS '雪花ID';
COMMENT ON COLUMN sys_tenant.tenant_id IS '租户编号';
COMMENT ON COLUMN sys_tenant.name IS '租户名称';
COMMENT ON COLUMN sys_tenant.contact_phone IS '联系电话';
COMMENT ON COLUMN sys_tenant.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_tenant.expire_time IS '过期时间（空=永久）';
COMMENT ON COLUMN sys_tenant.remark IS '备注';
COMMENT ON COLUMN sys_tenant.create_by IS '创建者';
COMMENT ON COLUMN sys_tenant.create_time IS '创建时间';
COMMENT ON COLUMN sys_tenant.update_by IS '更新者';
COMMENT ON COLUMN sys_tenant.update_time IS '更新时间';
COMMENT ON COLUMN sys_tenant.deleted IS '逻辑删除（0未删除 1已删除）';

-- ---------------------------------------------------------------------
-- 2. 部门表（树形）
-- ---------------------------------------------------------------------
CREATE TABLE sys_dept (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  parent_id   NUMBER(20)        DEFAULT 0 NOT NULL,
  ancestors   VARCHAR2(500 CHAR)   DEFAULT '0' NOT NULL,
  name        VARCHAR2(100 CHAR) NOT NULL,
  order_no    NUMBER(10)        DEFAULT 0 NOT NULL,
  leader      VARCHAR2(64 CHAR),
  phone       VARCHAR2(20 CHAR),
  email       VARCHAR2(100 CHAR),
  status      NUMBER(3)         DEFAULT 0 NOT NULL,
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL
);

COMMENT ON TABLE sys_dept IS '部门表';
COMMENT ON COLUMN sys_dept.id IS '雪花ID';
COMMENT ON COLUMN sys_dept.parent_id IS '父部门ID，0=根';
COMMENT ON COLUMN sys_dept.ancestors IS '祖级列表，逗号分隔';
COMMENT ON COLUMN sys_dept.name IS '部门名称';
COMMENT ON COLUMN sys_dept.order_no IS '显示顺序';
COMMENT ON COLUMN sys_dept.leader IS '负责人';
COMMENT ON COLUMN sys_dept.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_dept.deleted IS '逻辑删除（0未删除 1已删除）';

-- ---------------------------------------------------------------------
-- 3. 用户表
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  dept_id     NUMBER(20),
  username    VARCHAR2(30 CHAR) NOT NULL,
  nickname    VARCHAR2(50 CHAR) NOT NULL,
  password    VARCHAR2(100 CHAR) NOT NULL,
  phone       VARCHAR2(20 CHAR),
  email       VARCHAR2(100 CHAR),
  avatar      VARCHAR2(255 CHAR),
  user_type   NUMBER(3)         DEFAULT 1 NOT NULL,
  status      NUMBER(3)         DEFAULT 0 NOT NULL,
  login_ip    VARCHAR2(128 CHAR),
  login_date  DATE,
  remark      VARCHAR2(500 CHAR),
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL,
  CONSTRAINT uk_sys_user_tid_name UNIQUE (tenant_id, username)
);

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.username IS '登录账号';
COMMENT ON COLUMN sys_user.nickname IS '用户昵称';
COMMENT ON COLUMN sys_user.password IS 'BCrypt 密文';
COMMENT ON COLUMN sys_user.user_type IS '用户类型（0超管 1普通）';
COMMENT ON COLUMN sys_user.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_user.login_ip IS '最后登录IP';
COMMENT ON COLUMN sys_user.login_date IS '最后登录时间';
COMMENT ON COLUMN sys_user.deleted IS '逻辑删除（0未删除 1已删除）';

-- ---------------------------------------------------------------------
-- 4. 角色 / 用户角色 / 菜单 / 角色菜单
-- ---------------------------------------------------------------------
CREATE TABLE sys_role (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  name        VARCHAR2(50 CHAR) NOT NULL,
  role_key    VARCHAR2(100 CHAR) NOT NULL,
  sort        NUMBER(10)        DEFAULT 0 NOT NULL,
  data_scope  NUMBER(3)         DEFAULT 1 NOT NULL,
  status      NUMBER(3)         DEFAULT 0 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL
);

COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.role_key IS '角色权限字符串（如 super_admin）';
COMMENT ON COLUMN sys_role.data_scope IS '数据范围（1全部 2本部门及以下 3本部门 4仅本人）';
COMMENT ON COLUMN sys_role.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_role.deleted IS '逻辑删除（0未删除 1已删除）';

CREATE TABLE sys_user_role (
  user_id NUMBER(20) NOT NULL,
  role_id NUMBER(20) NOT NULL,
  CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE sys_user_role IS '用户角色关联表';

CREATE TABLE sys_menu (
  id          NUMBER(20)      NOT NULL,
  parent_id   NUMBER(20)      DEFAULT 0 NOT NULL,
  name        VARCHAR2(50 CHAR) NOT NULL,
  path        VARCHAR2(200 CHAR),
  component   VARCHAR2(255 CHAR),
  menu_type   CHAR(1 CHAR)    DEFAULT 'C' NOT NULL,
  perms       VARCHAR2(100 CHAR),
  icon        VARCHAR2(100 CHAR),
  sort        NUMBER(10)      DEFAULT 0 NOT NULL,
  visible     NUMBER(3)       DEFAULT 0 NOT NULL,
  status      NUMBER(3)       DEFAULT 0 NOT NULL,
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)       DEFAULT 0 NOT NULL
);

COMMENT ON TABLE sys_menu IS '菜单权限表';
COMMENT ON COLUMN sys_menu.path IS '路由地址（目录/菜单）';
COMMENT ON COLUMN sys_menu.component IS '前端组件路径（如 system/user/index）';
COMMENT ON COLUMN sys_menu.menu_type IS '类型（M目录 C菜单 F按钮）';
COMMENT ON COLUMN sys_menu.perms IS '权限标识（如 system:user:list）';
COMMENT ON COLUMN sys_menu.icon IS '图标（AntD 图标名，如 UserOutlined）';
COMMENT ON COLUMN sys_menu.visible IS '显示（0显示 1隐藏）';
COMMENT ON COLUMN sys_menu.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_menu.deleted IS '逻辑删除（0未删除 1已删除）';

CREATE TABLE sys_role_menu (
  role_id NUMBER(20) NOT NULL,
  menu_id NUMBER(20) NOT NULL,
  CONSTRAINT pk_sys_role_menu PRIMARY KEY (role_id, menu_id)
);

COMMENT ON TABLE sys_role_menu IS '角色菜单关联表';

-- ---------------------------------------------------------------------
-- 5. 字典 / 参数配置
-- ---------------------------------------------------------------------
CREATE TABLE sys_dict_type (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  name        VARCHAR2(100 CHAR) NOT NULL,
  dict_type   VARCHAR2(100 CHAR) NOT NULL,
  status      NUMBER(3)         DEFAULT 0 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL,
  CONSTRAINT uk_sys_dict_type_type UNIQUE (dict_type)
);

COMMENT ON TABLE sys_dict_type IS '字典类型表';
COMMENT ON COLUMN sys_dict_type.dict_type IS '字典类型（唯一键）';
COMMENT ON COLUMN sys_dict_type.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_dict_type.deleted IS '逻辑删除（0未删除 1已删除）';

CREATE TABLE sys_dict_data (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  dict_type   VARCHAR2(100 CHAR) NOT NULL,
  label       VARCHAR2(100 CHAR) NOT NULL,
  value       VARCHAR2(100 CHAR) NOT NULL,
  sort        NUMBER(10)        DEFAULT 0 NOT NULL,
  is_default  NUMBER(3)         DEFAULT 0 NOT NULL,
  status      NUMBER(3)         DEFAULT 0 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL
);

COMMENT ON TABLE sys_dict_data IS '字典数据表';
COMMENT ON COLUMN sys_dict_data.label IS '标签';
COMMENT ON COLUMN sys_dict_data.value IS '值';
COMMENT ON COLUMN sys_dict_data.is_default IS '是否默认（0否 1是）';
COMMENT ON COLUMN sys_dict_data.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_dict_data.deleted IS '逻辑删除（0未删除 1已删除）';

CREATE TABLE sys_config (
  id           NUMBER(20)        NOT NULL,
  tenant_id    VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  config_name  VARCHAR2(100 CHAR) NOT NULL,
  config_key   VARCHAR2(100 CHAR) NOT NULL,
  config_value VARCHAR2(500 CHAR) NOT NULL,
  status       NUMBER(3)         DEFAULT 0 NOT NULL,
  remark       VARCHAR2(500 CHAR),
  create_by    VARCHAR2(64 CHAR),
  create_time  DATE,
  update_by    VARCHAR2(64 CHAR),
  update_time  DATE,
  deleted      NUMBER(3)         DEFAULT 0 NOT NULL,
  CONSTRAINT uk_sys_config_key UNIQUE (config_key)
);

COMMENT ON TABLE sys_config IS '系统参数配置表';
COMMENT ON COLUMN sys_config.config_name IS '参数名称';
COMMENT ON COLUMN sys_config.config_key IS '参数键（唯一）';
COMMENT ON COLUMN sys_config.config_value IS '参数值';
COMMENT ON COLUMN sys_config.status IS '状态（0正常 1停用）';
COMMENT ON COLUMN sys_config.deleted IS '逻辑删除（0未删除 1已删除）';

-- ---------------------------------------------------------------------
-- 6. 日志表
-- ---------------------------------------------------------------------
CREATE TABLE sys_login_log (
  id         NUMBER(20)        NOT NULL,
  tenant_id  VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  username   VARCHAR2(50 CHAR) NOT NULL,
  ip         VARCHAR2(128 CHAR),
  browser    VARCHAR2(64 CHAR),
  os         VARCHAR2(64 CHAR),
  status     NUMBER(3)         DEFAULT 0 NOT NULL,
  msg        VARCHAR2(255 CHAR),
  login_time DATE
);

COMMENT ON TABLE sys_login_log IS '登录日志表';
COMMENT ON COLUMN sys_login_log.status IS '（0成功 1失败）';
COMMENT ON COLUMN sys_login_log.msg IS '提示信息';
COMMENT ON COLUMN sys_login_log.login_time IS '登录时间';

CREATE TABLE sys_oper_log (
  id             NUMBER(20)      NOT NULL,
  tenant_id      VARCHAR2(12 CHAR)  DEFAULT '000000' NOT NULL,
  title          VARCHAR2(50 CHAR),
  business_type  VARCHAR2(20 CHAR),
  method         VARCHAR2(200 CHAR),
  request_method VARCHAR2(10 CHAR),
  oper_url       VARCHAR2(255 CHAR),
  oper_param     CLOB,
  oper_id        NUMBER(20),
  oper_name      VARCHAR2(64 CHAR),
  ip             VARCHAR2(128 CHAR),
  status         NUMBER(3)       DEFAULT 0 NOT NULL,
  error_msg      CLOB,
  cost_ms        NUMBER(20),
  oper_time      DATE
);

COMMENT ON TABLE sys_oper_log IS '操作日志表';
COMMENT ON COLUMN sys_oper_log.title IS '模块标题';
COMMENT ON COLUMN sys_oper_log.business_type IS '业务类型（INSERT/UPDATE/DELETE/EXPORT等）';
COMMENT ON COLUMN sys_oper_log.method IS '方法名';
COMMENT ON COLUMN sys_oper_log.request_method IS '请求方式';
COMMENT ON COLUMN sys_oper_log.oper_param IS '请求参数';
COMMENT ON COLUMN sys_oper_log.status IS '（0成功 1失败）';
COMMENT ON COLUMN sys_oper_log.error_msg IS '异常信息';
COMMENT ON COLUMN sys_oper_log.cost_ms IS '耗时毫秒';
COMMENT ON COLUMN sys_oper_log.oper_time IS '操作时间';

-- ---------------------------------------------------------------------
-- 7. 文件表（元数据，二进制在 MinIO；注意 file_size：size 为 Oracle 保留字）
-- ---------------------------------------------------------------------
CREATE TABLE sys_file (
  id            NUMBER(20)        NOT NULL,
  tenant_id     VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  bucket        VARCHAR2(100 CHAR) NOT NULL,
  object_name   VARCHAR2(500 CHAR) NOT NULL,
  original_name VARCHAR2(255 CHAR) NOT NULL,
  suffix        VARCHAR2(32 CHAR),
  file_size     NUMBER(20)        DEFAULT 0 NOT NULL,
  content_type  VARCHAR2(100 CHAR),
  remark        VARCHAR2(500 CHAR),
  create_by     VARCHAR2(64 CHAR),
  create_time   DATE,
  update_by     VARCHAR2(64 CHAR),
  update_time   DATE,
  deleted       NUMBER(3)         DEFAULT 0 NOT NULL
);

COMMENT ON TABLE sys_file IS '文件表';
COMMENT ON COLUMN sys_file.bucket IS '存储桶';
COMMENT ON COLUMN sys_file.object_name IS '对象名';
COMMENT ON COLUMN sys_file.original_name IS '原始文件名';
COMMENT ON COLUMN sys_file.suffix IS '后缀（小写无点）';
COMMENT ON COLUMN sys_file.file_size IS '字节数';
COMMENT ON COLUMN sys_file.content_type IS 'MIME 类型';
COMMENT ON COLUMN sys_file.deleted IS '逻辑删除（0未删除 1已删除）';

-- ---------------------------------------------------------------------
-- 索引
-- ---------------------------------------------------------------------
CREATE INDEX idx_sys_dept_tenant_parent ON sys_dept (tenant_id, parent_id);
CREATE INDEX idx_sys_user_dept ON sys_user (dept_id);
CREATE INDEX idx_sys_role_tenant ON sys_role (tenant_id);
CREATE INDEX idx_sys_menu_parent ON sys_menu (parent_id);
CREATE INDEX idx_sys_dict_data_type ON sys_dict_data (dict_type);
CREATE INDEX idx_sys_login_log_username ON sys_login_log (username);
CREATE INDEX idx_sys_login_log_time ON sys_login_log (login_time);
CREATE INDEX idx_sys_oper_log_time ON sys_oper_log (oper_time);
CREATE INDEX idx_sys_file_tenant ON sys_file (tenant_id);

-- =====================================================================
-- 初始化数据（与 gencode-mysql.sql 完全一致）
-- =====================================================================

-- 租户
INSERT INTO sys_tenant (id, tenant_id, name, contact_phone, status, create_by, create_time) VALUES (1, '000000', '系统租户', '00000000000', 0, 'init', SYSDATE);

-- 部门
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, name, order_no, leader, status, create_by, create_time) VALUES (100, '000000', 0, '0', '总公司', 1, '管理员', 0, 'init', SYSDATE);
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, name, order_no, leader, status, create_by, create_time) VALUES (101, '000000', 100, '0,100', '研发部', 1, NULL, 0, 'init', SYSDATE);
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, name, order_no, leader, status, create_by, create_time) VALUES (102, '000000', 100, '0,100', '市场部', 2, NULL, 0, 'init', SYSDATE);

-- 用户（admin：密码由后端启动时用 BCrypt 初始化为 admin123）
INSERT INTO sys_user (id, tenant_id, dept_id, username, nickname, password, user_type, status, remark, create_by, create_time) VALUES (1, '000000', 100, 'admin', '超级管理员', 'INIT_BY_STARTUP', 0, 0, '平台内置管理员', 'init', SYSDATE);

-- 角色
INSERT INTO sys_role (id, tenant_id, name, role_key, sort, data_scope, status, remark, create_by, create_time) VALUES (1, '000000', '超级管理员', 'super_admin', 1, 1, 0, '拥有全部权限', 'init', SYSDATE);
INSERT INTO sys_role (id, tenant_id, name, role_key, sort, data_scope, status, remark, create_by, create_time) VALUES (2, '000000', '普通用户', 'common', 2, 4, 0, '仅本人数据', 'init', SYSDATE);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 菜单（目录 M / 菜单 C / 按钮 F）
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (1, 0, '仪表盘', '/dashboard', 'dashboard/index', 'C', 'dashboard:view', 'DashboardOutlined', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (100, 0, '系统管理', '/system', NULL, 'M', NULL, 'SettingOutlined', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (101, 100, '用户管理', '/system/user', 'system/user/index', 'C', 'system:user:list', 'UserOutlined', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (102, 100, '角色管理', '/system/role', 'system/role/index', 'C', 'system:role:list', 'TeamOutlined', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (103, 100, '菜单管理', '/system/menu', 'system/menu/index', 'C', 'system:menu:list', 'MenuOutlined', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (104, 100, '部门管理', '/system/dept', 'system/dept/index', 'C', 'system:dept:list', 'ApartmentOutlined', 4, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (105, 100, '字典管理', '/system/dict', 'system/dict/index', 'C', 'system:dict:list', 'BookOutlined', 5, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (106, 100, '参数配置', '/system/config', 'system/config/index', 'C', 'system:config:list', 'ControlOutlined', 6, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (107, 100, '租户管理', '/system/tenant', 'system/tenant/index', 'C', 'system:tenant:list', 'CloudOutlined', 7, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (108, 100, '登录日志', '/system/login-log', 'system/log/login', 'C', 'system:loginLog:list', 'LoginOutlined', 8, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (109, 100, '操作日志', '/system/oper-log', 'system/log/oper', 'C', 'system:operLog:list', 'FileTextOutlined', 9, 0, 0, 'init', SYSDATE);
-- 用户管理按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1101, 101, '用户查询', 'F', 'system:user:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1102, 101, '用户新增', 'F', 'system:user:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1103, 101, '用户修改', 'F', 'system:user:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1104, 101, '用户删除', 'F', 'system:user:delete', 4, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1105, 101, '重置密码', 'F', 'system:user:resetPwd', 5, 0, 0, 'init', SYSDATE);
-- 角色管理按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1111, 102, '角色查询', 'F', 'system:role:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1112, 102, '角色新增', 'F', 'system:role:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1113, 102, '角色修改', 'F', 'system:role:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1114, 102, '角色删除', 'F', 'system:role:delete', 4, 0, 0, 'init', SYSDATE);
-- 菜单管理按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1121, 103, '菜单查询', 'F', 'system:menu:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1122, 103, '菜单新增', 'F', 'system:menu:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1123, 103, '菜单修改', 'F', 'system:menu:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1124, 103, '菜单删除', 'F', 'system:menu:delete', 4, 0, 0, 'init', SYSDATE);
-- 部门管理按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1131, 104, '部门查询', 'F', 'system:dept:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1132, 104, '部门新增', 'F', 'system:dept:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1133, 104, '部门修改', 'F', 'system:dept:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1134, 104, '部门删除', 'F', 'system:dept:delete', 4, 0, 0, 'init', SYSDATE);
-- 字典管理按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1141, 105, '字典查询', 'F', 'system:dict:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1142, 105, '字典新增', 'F', 'system:dict:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1143, 105, '字典修改', 'F', 'system:dict:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1144, 105, '字典删除', 'F', 'system:dict:delete', 4, 0, 0, 'init', SYSDATE);
-- 参数配置按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1151, 106, '参数查询', 'F', 'system:config:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1152, 106, '参数新增', 'F', 'system:config:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1153, 106, '参数修改', 'F', 'system:config:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1154, 106, '参数删除', 'F', 'system:config:delete', 4, 0, 0, 'init', SYSDATE);
-- 租户管理按钮
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1161, 107, '租户查询', 'F', 'system:tenant:list', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1162, 107, '租户新增', 'F', 'system:tenant:add', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1163, 107, '租户修改', 'F', 'system:tenant:edit', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (1164, 107, '租户删除', 'F', 'system:tenant:delete', 4, 0, 0, 'init', SYSDATE);

-- 超级管理员角色绑定全部菜单
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;

-- 字典类型
INSERT INTO sys_dict_type (id, tenant_id, name, dict_type, status, remark, create_by, create_time) VALUES (1, '000000', '系统状态', 'sys_normal_status', 0, '通用启停状态', 'init', SYSDATE);
INSERT INTO sys_dict_type (id, tenant_id, name, dict_type, status, remark, create_by, create_time) VALUES (2, '000000', '用户性别', 'sys_user_sex', 0, NULL, 'init', SYSDATE);
INSERT INTO sys_dict_type (id, tenant_id, name, dict_type, status, remark, create_by, create_time) VALUES (3, '000000', '是/否', 'sys_yes_no', 0, NULL, 'init', SYSDATE);
INSERT INTO sys_dict_type (id, tenant_id, name, dict_type, status, remark, create_by, create_time) VALUES (4, '000000', '显示/隐藏', 'sys_show_hide', 0, '菜单显示状态', 'init', SYSDATE);

-- 字典数据
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (1, '000000', 'sys_normal_status', '正常', '0', 1, 1, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (2, '000000', 'sys_normal_status', '停用', '1', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (3, '000000', 'sys_user_sex', '未知', '0', 1, 1, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (4, '000000', 'sys_user_sex', '男', '1', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (5, '000000', 'sys_user_sex', '女', '2', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (6, '000000', 'sys_yes_no', '否', '0', 1, 1, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (7, '000000', 'sys_yes_no', '是', '1', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (8, '000000', 'sys_show_hide', '显示', '0', 1, 1, 0, 'init', SYSDATE);
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES (9, '000000', 'sys_show_hide', '隐藏', '1', 2, 0, 0, 'init', SYSDATE);

-- 参数配置
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, status, remark, create_by, create_time) VALUES (1, '000000', '平台名称', 'platform.name', 'GenCode 低代码平台', 0, '登录页/浏览器标题', 'init', SYSDATE);
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, status, remark, create_by, create_time) VALUES (2, '000000', '平台版权', 'platform.copyright', 'GenCode Platform', 0, NULL, 'init', SYSDATE);
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, status, remark, create_by, create_time) VALUES (3, '000000', '默认分页大小', 'sys.default.pageSize', '10', 0, NULL, 'init', SYSDATE);

COMMIT;

-- ---------------------------------------------------------------------
-- 8. 低代码表单（M1：表单定义 + 填报数据）
-- ---------------------------------------------------------------------
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_form_data PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_form PURGE';      EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /

CREATE TABLE lc_form (
  id               NUMBER(20)        NOT NULL,
  tenant_id        VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  code             VARCHAR2(64 CHAR) NOT NULL,
  name             VARCHAR2(100 CHAR) NOT NULL,
  schema_json      CLOB,
  status           NUMBER(3)         DEFAULT 0 NOT NULL,
  version          NUMBER(10)        DEFAULT 0 NOT NULL,
  published_schema CLOB,
  publish_time     DATE,
  remark           VARCHAR2(500 CHAR),
  create_by        VARCHAR2(64 CHAR),
  create_time      DATE,
  update_by        VARCHAR2(64 CHAR),
  update_time      DATE,
  deleted          NUMBER(3)         DEFAULT 0 NOT NULL,
  CONSTRAINT uk_lc_form_code UNIQUE (code)
);

COMMENT ON TABLE lc_form IS '低代码表单定义表';
COMMENT ON COLUMN lc_form.code IS '表单编码（唯一）';
COMMENT ON COLUMN lc_form.schema_json IS '设计 Schema JSON';
COMMENT ON COLUMN lc_form.status IS '（0草稿 1已发布 2停用）';
COMMENT ON COLUMN lc_form.version IS '发布版本号';
COMMENT ON COLUMN lc_form.published_schema IS '已发布 Schema 快照';

CREATE TABLE lc_form_data (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  form_code   VARCHAR2(64 CHAR) NOT NULL,
  data_json   CLOB              NOT NULL,
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL
);

COMMENT ON TABLE lc_form_data IS '低代码表单填报数据表';
COMMENT ON COLUMN lc_form_data.form_code IS '表单编码';
COMMENT ON COLUMN lc_form_data.data_json IS '填报数据 JSON';

CREATE INDEX idx_lc_form_data_code ON lc_form_data (form_code);

-- 低代码菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (200, 0, '低代码', '/lc', NULL, 'M', NULL, 'AppstoreOutlined', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (201, 200, '表单设计', '/lc/form', 'lc/form/index', 'C', 'lc:form:list', 'FormOutlined', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (202, 200, '表单填报页', '/app/form/:code', 'app/form-render', 'C', NULL, NULL, 9, 1, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (203, 200, '表单设计器', '/lc/form/design/:id', 'lc/form/design', 'C', 'lc:form:edit', NULL, 9, 1, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2021, 201, '表单新增', 'F', 'lc:form:add', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2022, 201, '表单修改', 'F', 'lc:form:edit', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2023, 201, '表单删除', 'F', 'lc:form:delete', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2024, 201, '表单发布', 'F', 'lc:form:publish', 4, 0, 0, 'init', SYSDATE);

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE id IN (200, 201, 202, 203, 2021, 2022, 2023, 2024);

-- ---------------------------------------------------------------------
-- 9. 低代码列表（M2：列表定义 + 数据源）
-- ---------------------------------------------------------------------
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_list PURGE';       EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_datasource PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /

CREATE TABLE lc_list (
  id               NUMBER(20)        NOT NULL,
  tenant_id        VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  code             VARCHAR2(64 CHAR) NOT NULL,
  name             VARCHAR2(100 CHAR) NOT NULL,
  source_type      VARCHAR2(16 CHAR)    DEFAULT 'TABLE' NOT NULL,
  source_config    CLOB,
  list_schema      CLOB,
  status           NUMBER(3)         DEFAULT 0 NOT NULL,
  version          NUMBER(10)        DEFAULT 0 NOT NULL,
  published_schema CLOB,
  publish_time     DATE,
  remark           VARCHAR2(500 CHAR),
  create_by        VARCHAR2(64 CHAR),
  create_time      DATE,
  update_by        VARCHAR2(64 CHAR),
  update_time      DATE,
  deleted          NUMBER(3)         DEFAULT 0 NOT NULL,
  CONSTRAINT uk_lc_list_code UNIQUE (code)
);
COMMENT ON TABLE lc_list IS '低代码列表定义表';
COMMENT ON COLUMN lc_list.source_type IS '数据源类型（TABLE SQL API预留）';
COMMENT ON COLUMN lc_list.source_config IS '数据源配置 JSON';
COMMENT ON COLUMN lc_list.list_schema IS '列表 Schema JSON：columns/search/buttons';

CREATE TABLE lc_datasource (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  name        VARCHAR2(100 CHAR) NOT NULL,
  driver      VARCHAR2(100 CHAR) NOT NULL,
  jdbc_url    VARCHAR2(500 CHAR) NOT NULL,
  username    VARCHAR2(100 CHAR) NOT NULL,
  password    VARCHAR2(200 CHAR) NOT NULL,
  remark      VARCHAR2(500 CHAR),
  create_by   VARCHAR2(64 CHAR),
  create_time DATE,
  update_by   VARCHAR2(64 CHAR),
  update_time DATE,
  deleted     NUMBER(3)         DEFAULT 0 NOT NULL
);
COMMENT ON TABLE lc_datasource IS '低代码外部数据源表';
COMMENT ON COLUMN lc_datasource.password IS 'AES 加密存储';

-- 列表菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (204, 200, '列表设计', '/lc/list', 'lc/list/index', 'C', 'lc:list:list', 'TableOutlined', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (205, 200, '列表设计器', '/lc/list/design/:id', 'lc/list/design', 'C', 'lc:list:edit', NULL, 9, 1, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (206, 200, '列表运行页', '/app/list/:code', 'app/list-render', 'C', NULL, NULL, 9, 1, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (207, 200, '数据源管理', '/lc/datasource', 'lc/datasource/index', 'C', 'lc:datasource:list', 'DatabaseOutlined', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2101, 204, '列表新增', 'F', 'lc:list:add', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2102, 204, '列表修改', 'F', 'lc:list:edit', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2103, 204, '列表删除', 'F', 'lc:list:delete', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2104, 204, '列表发布', 'F', 'lc:list:publish', 4, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2110, 207, '数据源新增', 'F', 'lc:datasource:add', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2111, 207, '数据源修改', 'F', 'lc:datasource:edit', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2112, 207, '数据源删除', 'F', 'lc:datasource:delete', 3, 0, 0, 'init', SYSDATE);

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE id IN (204, 205, 206, 207, 2101, 2102, 2103, 2104, 2110, 2111, 2112);

-- ---------------------------------------------------------------------
-- 10. 低代码流程（M3：流程定义 / 实例 / 审批任务）
-- ---------------------------------------------------------------------
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_process_task PURGE';     EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_process_instance PURGE'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /
DECLARE BEGIN EXECUTE IMMEDIATE 'DROP TABLE lc_process_def PURGE';      EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END; /

CREATE TABLE lc_process_def (
  id              NUMBER(20)        NOT NULL,
  tenant_id       VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  code            VARCHAR2(64 CHAR) NOT NULL,
  name            VARCHAR2(100 CHAR) NOT NULL,
  category        VARCHAR2(50 CHAR),
  bpmn_xml        CLOB,
  flow_key        VARCHAR2(64 CHAR),
  publish_version NUMBER(10)        DEFAULT 0 NOT NULL,
  status          NUMBER(3)         DEFAULT 0 NOT NULL,
  remark          VARCHAR2(500 CHAR),
  create_by       VARCHAR2(64 CHAR),
  create_time     DATE,
  update_by       VARCHAR2(64 CHAR),
  update_time     DATE,
  deleted         NUMBER(3)         DEFAULT 0 NOT NULL,
  CONSTRAINT uk_lc_process_code UNIQUE (code)
);
COMMENT ON TABLE lc_process_def IS '低代码流程定义表';

CREATE TABLE lc_process_instance (
  id               NUMBER(20)        NOT NULL,
  tenant_id        VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  process_def_id   NUMBER(20)        NOT NULL,
  flow_instance_id VARCHAR2(64 CHAR),
  business_key     VARCHAR2(64 CHAR),
  form_code        VARCHAR2(64 CHAR),
  form_data_id     NUMBER(20),
  title            VARCHAR2(200 CHAR) NOT NULL,
  current_node     VARCHAR2(100 CHAR),
  status           VARCHAR2(16 CHAR)  DEFAULT 'running' NOT NULL,
  start_user       VARCHAR2(64 CHAR),
  create_by        VARCHAR2(64 CHAR),
  create_time      DATE,
  update_by        VARCHAR2(64 CHAR),
  update_time      DATE,
  deleted          NUMBER(3)         DEFAULT 0 NOT NULL
);
COMMENT ON TABLE lc_process_instance IS '低代码流程实例表';

CREATE TABLE lc_process_task (
  id          NUMBER(20)        NOT NULL,
  tenant_id   VARCHAR2(12 CHAR)    DEFAULT '000000' NOT NULL,
  instance_id NUMBER(20)        NOT NULL,
  task_id     VARCHAR2(64 CHAR),
  node_name   VARCHAR2(100 CHAR),
  assignee    VARCHAR2(64 CHAR),
  result      VARCHAR2(16 CHAR)  DEFAULT 'pending' NOT NULL,
  comment     VARCHAR2(500 CHAR),
  handle_time DATE,
  create_time DATE
);
COMMENT ON TABLE lc_process_task IS '低代码流程审批任务表';

CREATE INDEX idx_lc_pi_def ON lc_process_instance (process_def_id);
CREATE INDEX idx_lc_pi_user ON lc_process_instance (start_user);
CREATE INDEX idx_lc_pt_instance ON lc_process_task (instance_id);
CREATE INDEX idx_lc_pt_assignee ON lc_process_task (assignee);

-- 流程菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (208, 200, '流程设计', '/lc/process', 'lc/process/index', 'C', 'lc:process:list', 'ClusterOutlined', 4, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (209, 200, '流程设计器', '/lc/process/design/:id', 'lc/process/design', 'C', 'lc:process:edit', NULL, 9, 1, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES (210, 200, '我的流程', '/lc/process/mine', 'lc/process/mine', 'C', 'lc:process:mine', 'SendOutlined', 5, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2121, 208, '流程新增', 'F', 'lc:process:add', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2122, 208, '流程修改', 'F', 'lc:process:edit', 2, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2123, 208, '流程删除', 'F', 'lc:process:delete', 3, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2124, 208, '流程部署', 'F', 'lc:process:deploy', 4, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2125, 210, '流程发起', 'F', 'lc:process:start', 1, 0, 0, 'init', SYSDATE);
INSERT INTO sys_menu (id, parent_id, name, menu_type, perms, sort, visible, status, create_by, create_time) VALUES (2126, 210, '流程审批', 'F', 'lc:process:approve', 2, 0, 0, 'init', SYSDATE);

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE id IN (208, 209, 210, 2121, 2122, 2123, 2124, 2125, 2126);
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE id IN (210, 2125, 2126);
