-- =====================================================================
-- GenCode 企业级低代码平台 · MySQL 8 初始化脚本
-- 数据库：gencode（utf8mb4）
-- 约定：status 0=正常/启用 1=停用；deleted 0=未删除 1=已删除（逻辑删除）
--       tenant_id '000000' 为系统租户；id 均为雪花 ID，种子数据使用固定 ID
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. 租户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id     VARCHAR(12)  NOT NULL DEFAULT '000000' COMMENT '租户编号',
  name          VARCHAR(100) NOT NULL COMMENT '租户名称',
  contact_phone VARCHAR(20)  NULL COMMENT '联系电话',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态（0正常 1停用）',
  expire_time   DATETIME     NULL COMMENT '过期时间（空=永久）',
  remark        VARCHAR(500) NULL,
  create_by     VARCHAR(64)  NULL,
  create_time   DATETIME     NULL,
  update_by     VARCHAR(64)  NULL,
  update_time   DATETIME     NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ---------------------------------------------------------------------
-- 2. 部门表（树形）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dept (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，0=根',
  ancestors   VARCHAR(500) NOT NULL DEFAULT '0' COMMENT '祖级列表，逗号分隔',
  name        VARCHAR(100) NOT NULL COMMENT '部门名称',
  order_no    INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  leader      VARCHAR(64)  NULL COMMENT '负责人',
  phone       VARCHAR(20)  NULL,
  email       VARCHAR(100) NULL,
  status      TINYINT      NOT NULL DEFAULT 0,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant_parent (tenant_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ---------------------------------------------------------------------
-- 3. 用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  dept_id     BIGINT       NULL COMMENT '部门ID',
  username    VARCHAR(30)  NOT NULL COMMENT '登录账号',
  nickname    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '用户昵称',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  phone       VARCHAR(20)  NULL,
  email       VARCHAR(100) NULL,
  avatar      VARCHAR(255) NULL COMMENT '头像URL',
  user_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '用户类型（0超管 1普通）',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态（0正常 1停用）',
  login_ip    VARCHAR(128) NULL COMMENT '最后登录IP',
  login_date  DATETIME     NULL COMMENT '最后登录时间',
  remark      VARCHAR(500) NULL,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_username (tenant_id, username),
  KEY idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------------------------------------------------------------------
-- 4. 角色表 / 用户角色 / 菜单 / 角色菜单
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  name        VARCHAR(50)  NOT NULL COMMENT '角色名称',
  role_key    VARCHAR(100) NOT NULL COMMENT '角色权限字符串（如 super_admin）',
  sort        INT          NOT NULL DEFAULT 0,
  data_scope  TINYINT      NOT NULL DEFAULT 1 COMMENT '数据范围（1全部 2本部门及以下 3本部门 4仅本人）',
  status      TINYINT      NOT NULL DEFAULT 0,
  remark      VARCHAR(500) NULL,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_menu (
  id          BIGINT       NOT NULL COMMENT '菜单ID（种子数据固定ID）',
  parent_id   BIGINT       NOT NULL DEFAULT 0,
  name        VARCHAR(50)  NOT NULL COMMENT '菜单/按钮名称',
  path        VARCHAR(200) NULL COMMENT '路由地址（目录/菜单）',
  component   VARCHAR(255) NULL COMMENT '前端组件路径（如 system/user/index）',
  menu_type   CHAR(1)      NOT NULL DEFAULT 'C' COMMENT '类型（M目录 C菜单 F按钮）',
  perms       VARCHAR(100) NULL COMMENT '权限标识（如 system:user:list）',
  icon        VARCHAR(100) NULL COMMENT '图标（AntD 图标名，如 UserOutlined）',
  sort        INT          NOT NULL DEFAULT 0,
  visible     TINYINT      NOT NULL DEFAULT 0 COMMENT '显示（0显示 1隐藏）',
  status      TINYINT      NOT NULL DEFAULT 0,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ---------------------------------------------------------------------
-- 5. 字典 / 参数配置
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_type (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  name        VARCHAR(100) NOT NULL COMMENT '字典名称',
  dict_type   VARCHAR(100) NOT NULL COMMENT '字典类型（唯一键）',
  status      TINYINT      NOT NULL DEFAULT 0,
  remark      VARCHAR(500) NULL,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS sys_dict_data (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  dict_type   VARCHAR(100) NOT NULL,
  label       VARCHAR(100) NOT NULL COMMENT '标签',
  value       VARCHAR(100) NOT NULL COMMENT '值',
  sort        INT          NOT NULL DEFAULT 0,
  is_default  TINYINT      NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 0,
  remark      VARCHAR(500) NULL,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT       NOT NULL,
  tenant_id    VARCHAR(12)  NOT NULL DEFAULT '000000',
  config_name  VARCHAR(100) NOT NULL COMMENT '参数名称',
  config_key   VARCHAR(100) NOT NULL COMMENT '参数键（唯一）',
  config_value VARCHAR(500) NOT NULL DEFAULT '' COMMENT '参数值',
  status       TINYINT      NOT NULL DEFAULT 0,
  remark       VARCHAR(500) NULL,
  create_by    VARCHAR(64)  NULL,
  create_time  DATETIME     NULL,
  update_by    VARCHAR(64)  NULL,
  update_time  DATETIME     NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置表';

-- ---------------------------------------------------------------------
-- 6. 日志表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
  id         BIGINT       NOT NULL,
  tenant_id  VARCHAR(12)  NOT NULL DEFAULT '000000',
  username   VARCHAR(50)  NOT NULL,
  ip         VARCHAR(128) NULL,
  browser    VARCHAR(64)  NULL,
  os         VARCHAR(64)  NULL,
  status     TINYINT      NOT NULL DEFAULT 0 COMMENT '（0成功 1失败）',
  msg        VARCHAR(255) NULL COMMENT '提示信息',
  login_time DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_username (username),
  KEY idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(12)  NOT NULL DEFAULT '000000',
  title           VARCHAR(50)  NULL COMMENT '模块标题',
  business_type   VARCHAR(20)  NULL COMMENT '业务类型（INSERT/UPDATE/DELETE/EXPORT等）',
  method          VARCHAR(200) NULL COMMENT '方法名',
  request_method  VARCHAR(10)  NULL COMMENT '请求方式',
  oper_url        VARCHAR(255) NULL,
  oper_param      TEXT         NULL,
  oper_id         BIGINT       NULL COMMENT '操作人ID',
  oper_name       VARCHAR(64)  NULL COMMENT '操作人账号',
  ip              VARCHAR(128) NULL,
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '（0成功 1失败）',
  error_msg       TEXT         NULL,
  cost_ms         BIGINT       NULL COMMENT '耗时毫秒',
  oper_time       DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_oper_time (oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =====================================================================
-- 初始化数据
-- =====================================================================

-- 租户
INSERT INTO sys_tenant (id, tenant_id, name, contact_phone, status, create_by, create_time) VALUES
(1, '000000', '系统租户', '00000000000', 0, 'init', NOW());

-- 部门
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, name, order_no, leader, status, create_by, create_time) VALUES
(100, '000000', 0,   '0',     '总公司', 1, '管理员', 0, 'init', NOW()),
(101, '000000', 100, '0,100', '研发部', 1, NULL,     0, 'init', NOW()),
(102, '000000', 100, '0,100', '市场部', 2, NULL,     0, 'init', NOW());

-- 用户（admin：密码由后端启动时用 BCrypt 初始化为 admin123）
INSERT INTO sys_user (id, tenant_id, dept_id, username, nickname, password, user_type, status, remark, create_by, create_time) VALUES
(1, '000000', 100, 'admin', '超级管理员', 'INIT_BY_STARTUP', 0, 0, '平台内置管理员', 'init', NOW());

-- 角色
INSERT INTO sys_role (id, tenant_id, name, role_key, sort, data_scope, status, remark, create_by, create_time) VALUES
(1, '000000', '超级管理员', 'super_admin', 1, 1, 0, '拥有全部权限', 'init', NOW()),
(2, '000000', '普通用户',   'common',      2, 4, 0, '仅本人数据',   'init', NOW());

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 菜单（目录 M / 菜单 C / 按钮 F）
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(1,   0, '仪表盘',   '/dashboard',      'dashboard/index',    'C', 'dashboard:view',  'DashboardOutlined', 1, 0, 0, 'init', NOW()),
(100, 0, '系统管理', '/system',         NULL,                 'M', NULL,              'SettingOutlined',   2, 0, 0, 'init', NOW()),
(101, 100, '用户管理', '/system/user',     'system/user/index',   'C', 'system:user:list',  'UserOutlined',     1, 0, 0, 'init', NOW()),
(102, 100, '角色管理', '/system/role',     'system/role/index',   'C', 'system:role:list',  'TeamOutlined',     2, 0, 0, 'init', NOW()),
(103, 100, '菜单管理', '/system/menu',     'system/menu/index',   'C', 'system:menu:list',  'MenuOutlined',     3, 0, 0, 'init', NOW()),
(104, 100, '部门管理', '/system/dept',     'system/dept/index',   'C', 'system:dept:list',  'ApartmentOutlined',4, 0, 0, 'init', NOW()),
(105, 100, '字典管理', '/system/dict',     'system/dict/index',   'C', 'system:dict:list',  'BookOutlined',     5, 0, 0, 'init', NOW()),
(106, 100, '参数配置', '/system/config',   'system/config/index', 'C', 'system:config:list','ControlOutlined',  6, 0, 0, 'init', NOW()),
(107, 100, '租户管理', '/system/tenant',   'system/tenant/index', 'C', 'system:tenant:list','CloudOutlined',    7, 0, 0, 'init', NOW()),
(108, 100, '登录日志', '/system/login-log','system/log/login',    'C', 'system:loginLog:list','LoginOutlined',  8, 0, 0, 'init', NOW()),
(109, 100, '操作日志', '/system/oper-log', 'system/log/oper',     'C', 'system:operLog:list','FileTextOutlined', 9, 0, 0, 'init', NOW()),
-- 用户管理按钮
(1101, 101, '用户查询', NULL, NULL, 'F', 'system:user:list',     NULL, 1, 0, 0, 'init', NOW()),
(1102, 101, '用户新增', NULL, NULL, 'F', 'system:user:add',      NULL, 2, 0, 0, 'init', NOW()),
(1103, 101, '用户修改', NULL, NULL, 'F', 'system:user:edit',     NULL, 3, 0, 0, 'init', NOW()),
(1104, 101, '用户删除', NULL, NULL, 'F', 'system:user:delete',   NULL, 4, 0, 0, 'init', NOW()),
(1105, 101, '重置密码', NULL, NULL, 'F', 'system:user:resetPwd', NULL, 5, 0, 0, 'init', NOW()),
-- 角色管理按钮
(1111, 102, '角色查询', NULL, NULL, 'F', 'system:role:list',   NULL, 1, 0, 0, 'init', NOW()),
(1112, 102, '角色新增', NULL, NULL, 'F', 'system:role:add',    NULL, 2, 0, 0, 'init', NOW()),
(1113, 102, '角色修改', NULL, NULL, 'F', 'system:role:edit',   NULL, 3, 0, 0, 'init', NOW()),
(1114, 102, '角色删除', NULL, NULL, 'F', 'system:role:delete', NULL, 4, 0, 0, 'init', NOW()),
-- 菜单管理按钮
(1121, 103, '菜单查询', NULL, NULL, 'F', 'system:menu:list',   NULL, 1, 0, 0, 'init', NOW()),
(1122, 103, '菜单新增', NULL, NULL, 'F', 'system:menu:add',    NULL, 2, 0, 0, 'init', NOW()),
(1123, 103, '菜单修改', NULL, NULL, 'F', 'system:menu:edit',   NULL, 3, 0, 0, 'init', NOW()),
(1124, 103, '菜单删除', NULL, NULL, 'F', 'system:menu:delete', NULL, 4, 0, 0, 'init', NOW()),
-- 部门管理按钮
(1131, 104, '部门查询', NULL, NULL, 'F', 'system:dept:list',   NULL, 1, 0, 0, 'init', NOW()),
(1132, 104, '部门新增', NULL, NULL, 'F', 'system:dept:add',    NULL, 2, 0, 0, 'init', NOW()),
(1133, 104, '部门修改', NULL, NULL, 'F', 'system:dept:edit',   NULL, 3, 0, 0, 'init', NOW()),
(1134, 104, '部门删除', NULL, NULL, 'F', 'system:dept:delete', NULL, 4, 0, 0, 'init', NOW()),
-- 字典管理按钮
(1141, 105, '字典查询', NULL, NULL, 'F', 'system:dict:list',   NULL, 1, 0, 0, 'init', NOW()),
(1142, 105, '字典新增', NULL, NULL, 'F', 'system:dict:add',    NULL, 2, 0, 0, 'init', NOW()),
(1143, 105, '字典修改', NULL, NULL, 'F', 'system:dict:edit',   NULL, 3, 0, 0, 'init', NOW()),
(1144, 105, '字典删除', NULL, NULL, 'F', 'system:dict:delete', NULL, 4, 0, 0, 'init', NOW()),
-- 参数配置按钮
(1151, 106, '参数查询', NULL, NULL, 'F', 'system:config:list',   NULL, 1, 0, 0, 'init', NOW()),
(1152, 106, '参数新增', NULL, NULL, 'F', 'system:config:add',    NULL, 2, 0, 0, 'init', NOW()),
(1153, 106, '参数修改', NULL, NULL, 'F', 'system:config:edit',   NULL, 3, 0, 0, 'init', NOW()),
(1154, 106, '参数删除', NULL, NULL, 'F', 'system:config:delete', NULL, 4, 0, 0, 'init', NOW()),
-- 租户管理按钮
(1161, 107, '租户查询', NULL, NULL, 'F', 'system:tenant:list',   NULL, 1, 0, 0, 'init', NOW()),
(1162, 107, '租户新增', NULL, NULL, 'F', 'system:tenant:add',    NULL, 2, 0, 0, 'init', NOW()),
(1163, 107, '租户修改', NULL, NULL, 'F', 'system:tenant:edit',   NULL, 3, 0, 0, 'init', NOW()),
(1164, 107, '租户删除', NULL, NULL, 'F', 'system:tenant:delete', NULL, 4, 0, 0, 'init', NOW());

-- 超级管理员角色绑定全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 字典类型
INSERT INTO sys_dict_type (id, tenant_id, name, dict_type, status, remark, create_by, create_time) VALUES
(1, '000000', '系统状态', 'sys_normal_status', 0, '通用启停状态', 'init', NOW()),
(2, '000000', '用户性别', 'sys_user_sex',      0, NULL,           'init', NOW()),
(3, '000000', '是/否',    'sys_yes_no',        0, NULL,           'init', NOW()),
(4, '000000', '显示/隐藏','sys_show_hide',     0, '菜单显示状态', 'init', NOW());

-- 字典数据
INSERT INTO sys_dict_data (id, tenant_id, dict_type, label, value, sort, is_default, status, create_by, create_time) VALUES
(1, '000000', 'sys_normal_status', '正常', '0', 1, 1, 0, 'init', NOW()),
(2, '000000', 'sys_normal_status', '停用', '1', 2, 0, 0, 'init', NOW()),
(3, '000000', 'sys_user_sex',      '未知', '0', 1, 1, 0, 'init', NOW()),
(4, '000000', 'sys_user_sex',      '男',   '1', 2, 0, 0, 'init', NOW()),
(5, '000000', 'sys_user_sex',      '女',   '2', 3, 0, 0, 'init', NOW()),
(6, '000000', 'sys_yes_no',        '否',   '0', 1, 1, 0, 'init', NOW()),
(7, '000000', 'sys_yes_no',        '是',   '1', 2, 0, 0, 'init', NOW()),
(8, '000000', 'sys_show_hide',     '显示', '0', 1, 1, 0, 'init', NOW()),
(9, '000000', 'sys_show_hide',     '隐藏', '1', 2, 0, 0, 'init', NOW());

-- 参数配置
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, status, remark, create_by, create_time) VALUES
(1, '000000', '平台名称',     'platform.name',        'GenCode 低代码平台', 0, '登录页/浏览器标题', 'init', NOW()),
(2, '000000', '平台版权',     'platform.copyright',   'GenCode Platform',   0, NULL,               'init', NOW()),
(3, '000000', '默认分页大小', 'sys.default.pageSize', '10',                 0, NULL,               'init', NOW());

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 7. 文件表（元数据，二进制在 MinIO）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_file (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id     VARCHAR(12)  NOT NULL DEFAULT '000000',
  bucket        VARCHAR(100) NOT NULL COMMENT '存储桶',
  object_name   VARCHAR(500) NOT NULL COMMENT '对象名',
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  suffix        VARCHAR(32)  NULL COMMENT '后缀（小写无点）',
  file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
  content_type  VARCHAR(100) NULL COMMENT 'MIME 类型',
  remark        VARCHAR(500) NULL,
  create_by     VARCHAR(64)  NULL,
  create_time   DATETIME     NULL,
  update_by     VARCHAR(64)  NULL,
  update_time   DATETIME     NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- ---------------------------------------------------------------------
-- 8. 低代码表单（M1：表单定义 + 填报数据）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lc_form (
  id                BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id         VARCHAR(12)  NOT NULL DEFAULT '000000',
  code              VARCHAR(64)  NOT NULL COMMENT '表单编码（唯一）',
  name              VARCHAR(100) NOT NULL COMMENT '表单名称',
  schema_json       MEDIUMTEXT   NULL COMMENT '设计 Schema JSON',
  status            TINYINT      NOT NULL DEFAULT 0 COMMENT '（0草稿 1已发布 2停用）',
  version           INT          NOT NULL DEFAULT 0 COMMENT '发布版本号',
  published_schema  MEDIUMTEXT   NULL COMMENT '已发布 Schema 快照',
  publish_time      DATETIME     NULL COMMENT '最近发布时间',
  remark            VARCHAR(500) NULL,
  create_by         VARCHAR(64)  NULL,
  create_time       DATETIME     NULL,
  update_by         VARCHAR(64)  NULL,
  update_time       DATETIME     NULL,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lc_form_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码表单定义表';

CREATE TABLE IF NOT EXISTS lc_form_data (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  form_code   VARCHAR(64)  NOT NULL COMMENT '表单编码',
  data_json   MEDIUMTEXT   NOT NULL COMMENT '填报数据 JSON',
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_lc_form_data_code (form_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码表单填报数据表';

-- 低代码菜单（目录/表单设计/隐藏的设计器与填报页/按钮）
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(200, 0, '低代码', '/lc', NULL, 'M', NULL, 'AppstoreOutlined', 3, 0, 0, 'init', NOW()),
(201, 200, '表单设计', '/lc/form', 'lc/form/index', 'C', 'lc:form:list', 'FormOutlined', 1, 0, 0, 'init', NOW()),
(202, 200, '表单填报页', '/app/form/:code', 'app/form-render', 'C', NULL, NULL, 9, 1, 0, 'init', NOW()),
(203, 200, '表单设计器', '/lc/form/design/:id', 'lc/form/design', 'C', 'lc:form:edit', NULL, 9, 1, 0, 'init', NOW()),
(2021, 201, '表单新增', NULL, NULL, 'F', 'lc:form:add', NULL, 1, 0, 0, 'init', NOW()),
(2022, 201, '表单修改', NULL, NULL, 'F', 'lc:form:edit', NULL, 2, 0, 0, 'init', NOW()),
(2023, 201, '表单删除', NULL, NULL, 'F', 'lc:form:delete', NULL, 3, 0, 0, 'init', NOW()),
(2024, 201, '表单发布', NULL, NULL, 'F', 'lc:form:publish', NULL, 4, 0, 0, 'init', NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (200, 201, 202, 203, 2021, 2022, 2023, 2024)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);

-- ---------------------------------------------------------------------
-- 9. 低代码列表（M2：列表定义 + 数据源）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lc_list (
  id                BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id         VARCHAR(12)  NOT NULL DEFAULT '000000',
  code              VARCHAR(64)  NOT NULL COMMENT '列表编码（唯一）',
  name              VARCHAR(100) NOT NULL COMMENT '列表名称',
  source_type       VARCHAR(16)  NOT NULL DEFAULT 'TABLE' COMMENT '数据源类型（TABLE单表 SQL自定义 API接口预留）',
  source_config     MEDIUMTEXT   NULL COMMENT '数据源配置 JSON：TABLE{datasourceId,tableName,pkField} SQL{datasourceId,sql}',
  list_schema       MEDIUMTEXT   NULL COMMENT '列表 Schema JSON：columns/search/buttons',
  status            TINYINT      NOT NULL DEFAULT 0 COMMENT '（0草稿 1已发布 2停用）',
  version           INT          NOT NULL DEFAULT 0,
  published_schema  MEDIUMTEXT   NULL COMMENT '已发布快照（含 source_config + list_schema）',
  publish_time      DATETIME     NULL,
  remark            VARCHAR(500) NULL,
  create_by         VARCHAR(64)  NULL,
  create_time       DATETIME     NULL,
  update_by         VARCHAR(64)  NULL,
  update_time       DATETIME     NULL,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lc_list_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码列表定义表';

CREATE TABLE IF NOT EXISTS lc_datasource (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  name        VARCHAR(100) NOT NULL COMMENT '数据源名称',
  driver      VARCHAR(100) NOT NULL COMMENT '驱动类',
  jdbc_url    VARCHAR(500) NOT NULL COMMENT 'JDBC URL',
  username    VARCHAR(100) NOT NULL,
  password    VARCHAR(200) NOT NULL COMMENT 'AES 加密存储',
  remark      VARCHAR(500) NULL,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码外部数据源表';

-- 列表菜单（列表设计/设计器/运行时/数据源管理/按钮）
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(204, 200, '列表设计', '/lc/list', 'lc/list/index', 'C', 'lc:list:list', 'TableOutlined', 2, 0, 0, 'init', NOW()),
(205, 200, '列表设计器', '/lc/list/design/:id', 'lc/list/design', 'C', 'lc:list:edit', NULL, 9, 1, 0, 'init', NOW()),
(206, 200, '列表运行页', '/app/list/:code', 'app/list-render', 'C', NULL, NULL, 9, 1, 0, 'init', NOW()),
(207, 200, '数据源管理', '/lc/datasource', 'lc/datasource/index', 'C', 'lc:datasource:list', 'DatabaseOutlined', 3, 0, 0, 'init', NOW()),
(2101, 204, '列表新增', NULL, NULL, 'F', 'lc:list:add', NULL, 1, 0, 0, 'init', NOW()),
(2102, 204, '列表修改', NULL, NULL, 'F', 'lc:list:edit', NULL, 2, 0, 0, 'init', NOW()),
(2103, 204, '列表删除', NULL, NULL, 'F', 'lc:list:delete', NULL, 3, 0, 0, 'init', NOW()),
(2104, 204, '列表发布', NULL, NULL, 'F', 'lc:list:publish', NULL, 4, 0, 0, 'init', NOW()),
(2110, 207, '数据源新增', NULL, NULL, 'F', 'lc:datasource:add', NULL, 1, 0, 0, 'init', NOW()),
(2111, 207, '数据源修改', NULL, NULL, 'F', 'lc:datasource:edit', NULL, 2, 0, 0, 'init', NOW()),
(2112, 207, '数据源删除', NULL, NULL, 'F', 'lc:datasource:delete', NULL, 3, 0, 0, 'init', NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (204, 205, 206, 207, 2101, 2102, 2103, 2104, 2110, 2111, 2112)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);

-- ---------------------------------------------------------------------
-- 10. 低代码流程（M3：流程定义 / 实例 / 审批任务）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lc_process_def (
  id              BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id       VARCHAR(12)  NOT NULL DEFAULT '000000',
  code            VARCHAR(64)  NOT NULL COMMENT '流程编码（唯一）',
  name            VARCHAR(100) NOT NULL COMMENT '流程名称',
  category        VARCHAR(50)  NULL COMMENT '分类',
  bpmn_xml        MEDIUMTEXT   NULL COMMENT 'BPMN 2.0 XML',
  flow_key        VARCHAR(64)  NULL COMMENT 'Flowable processDefinitionKey',
  publish_version INT          NOT NULL DEFAULT 0 COMMENT '部署版本',
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '（0草稿 1已发布 2停用）',
  remark          VARCHAR(500) NULL,
  create_by       VARCHAR(64)  NULL,
  create_time     DATETIME     NULL,
  update_by       VARCHAR(64)  NULL,
  update_time     DATETIME     NULL,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lc_process_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码流程定义表';

CREATE TABLE IF NOT EXISTS lc_process_instance (
  id                BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id         VARCHAR(12)  NOT NULL DEFAULT '000000',
  process_def_id    BIGINT       NOT NULL COMMENT 'lc_process_def.id',
  flow_instance_id  VARCHAR(64)  NULL COMMENT 'Flowable 流程实例ID',
  business_key      VARCHAR(64)  NULL COMMENT '业务键',
  form_code         VARCHAR(64)  NULL COMMENT '关联表单编码',
  form_data_id      BIGINT       NULL COMMENT '关联表单数据ID',
  title             VARCHAR(200) NOT NULL COMMENT '流程标题',
  current_node      VARCHAR(100) NULL COMMENT '当前节点',
  status            VARCHAR(16)  NOT NULL DEFAULT 'running' COMMENT '（running审批中 approved已通过 rejected已驳回 withdrawn已撤回）',
  start_user        VARCHAR(64)  NULL COMMENT '发起人账号',
  create_by         VARCHAR(64)  NULL,
  create_time       DATETIME     NULL,
  update_by         VARCHAR(64)  NULL,
  update_time       DATETIME     NULL,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_lc_pi_def (process_def_id),
  KEY idx_lc_pi_user (start_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码流程实例表';

CREATE TABLE IF NOT EXISTS lc_process_task (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  instance_id BIGINT       NOT NULL COMMENT 'lc_process_instance.id',
  task_id     VARCHAR(64)  NULL COMMENT 'Flowable 任务ID',
  node_name   VARCHAR(100) NULL COMMENT '节点名称',
  assignee    VARCHAR(64)  NULL COMMENT '办理人账号',
  result      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '（pending待审批 approved通过 rejected驳回 transferred转办）',
  comment     VARCHAR(500) NULL COMMENT '审批意见',
  handle_time DATETIME     NULL COMMENT '办理时间',
  create_time DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_lc_pt_instance (instance_id),
  KEY idx_lc_pt_assignee (assignee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码流程审批任务表';

-- 流程菜单（流程设计/设计器/我的流程/按钮）
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(208, 200, '流程设计', '/lc/process', 'lc/process/index', 'C', 'lc:process:list', 'ClusterOutlined', 4, 0, 0, 'init', NOW()),
(209, 200, '流程设计器', '/lc/process/design/:id', 'lc/process/design', 'C', 'lc:process:edit', NULL, 9, 1, 0, 'init', NOW()),
(210, 200, '我的流程', '/lc/process/mine', 'lc/process/mine', 'C', 'lc:process:mine', 'SendOutlined', 5, 0, 0, 'init', NOW()),
(2121, 208, '流程新增', NULL, NULL, 'F', 'lc:process:add', NULL, 1, 0, 0, 'init', NOW()),
(2122, 208, '流程修改', NULL, NULL, 'F', 'lc:process:edit', NULL, 2, 0, 0, 'init', NOW()),
(2123, 208, '流程删除', NULL, NULL, 'F', 'lc:process:delete', NULL, 3, 0, 0, 'init', NOW()),
(2124, 208, '流程部署', NULL, NULL, 'F', 'lc:process:deploy', NULL, 4, 0, 0, 'init', NOW()),
(2125, 210, '流程发起', NULL, NULL, 'F', 'lc:process:start', NULL, 1, 0, 0, 'init', NOW()),
(2126, 210, '流程审批', NULL, NULL, 'F', 'lc:process:approve', NULL, 2, 0, 0, 'init', NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (208, 209, 210, 2121, 2122, 2123, 2124, 2125, 2126)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id IN (210, 2125, 2126)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = sys_menu.id);

-- ---------------------------------------------------------------------
-- 11. 报表与大屏（M4：数据集 / 大屏）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lc_dataset (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   VARCHAR(12)  NOT NULL DEFAULT '000000',
  code        VARCHAR(64)  NOT NULL COMMENT '数据集编码（唯一）',
  name        VARCHAR(100) NOT NULL COMMENT '数据集名称',
  sql_text    MEDIUMTEXT   NOT NULL COMMENT '查询 SQL（SELECT，支持 #{param} 参数化）',
  params_json MEDIUMTEXT   NULL COMMENT '参数定义 JSON [{name,label,type,required,defaultValue}]',
  remark      VARCHAR(500) NULL,
  create_by   VARCHAR(64)  NULL,
  create_time DATETIME     NULL,
  update_by   VARCHAR(64)  NULL,
  update_time DATETIME     NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lc_dataset_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码数据集表';

CREATE TABLE IF NOT EXISTS lc_dashboard (
  id                BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id         VARCHAR(12)  NOT NULL DEFAULT '000000',
  code              VARCHAR(64)  NOT NULL COMMENT '大屏编码（唯一）',
  name              VARCHAR(100) NOT NULL COMMENT '大屏名称',
  layout_json       MEDIUMTEXT   NULL COMMENT '布局 JSON [{type,chartType,datasetCode,title,xField,yField,seriesField,x,y,w,h,refreshSec}]',
  status            TINYINT      NOT NULL DEFAULT 0 COMMENT '（0草稿 1已发布 2停用）',
  version           INT          NOT NULL DEFAULT 0,
  published_schema  MEDIUMTEXT   NULL COMMENT '已发布布局快照',
  publish_time      DATETIME     NULL,
  remark            VARCHAR(500) NULL,
  create_by         VARCHAR(64)  NULL,
  create_time       DATETIME     NULL,
  update_by         VARCHAR(64)  NULL,
  update_time       DATETIME     NULL,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lc_dashboard_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码数据大屏表';

-- 报表大屏菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(211, 200, '报表设计', '/lc/report', 'lc/report/index', 'C', 'lc:report:list', 'BarChartOutlined', 6, 0, 0, 'init', NOW()),
(212, 200, '大屏设计', '/lc/dashboard', 'lc/dashboard/index', 'C', 'lc:dashboard:list', 'MonitorOutlined', 7, 0, 0, 'init', NOW()),
(213, 200, '大屏运行页', '/app/dashboard/:code', 'app/dashboard-render', 'C', NULL, NULL, 9, 1, 0, 'init', NOW()),
(2201, 211, '报表新增', NULL, NULL, 'F', 'lc:report:add', NULL, 1, 0, 0, 'init', NOW()),
(2202, 211, '报表修改', NULL, NULL, 'F', 'lc:report:edit', NULL, 2, 0, 0, 'init', NOW()),
(2203, 211, '报表删除', NULL, NULL, 'F', 'lc:report:delete', NULL, 3, 0, 0, 'init', NOW()),
(2211, 212, '大屏新增', NULL, NULL, 'F', 'lc:dashboard:add', NULL, 1, 0, 0, 'init', NOW()),
(2212, 212, '大屏修改', NULL, NULL, 'F', 'lc:dashboard:edit', NULL, 2, 0, 0, 'init', NOW()),
(2213, 212, '大屏删除', NULL, NULL, 'F', 'lc:dashboard:delete', NULL, 3, 0, 0, 'init', NOW()),
(2214, 212, '大屏发布', NULL, NULL, 'F', 'lc:dashboard:publish', NULL, 4, 0, 0, 'init', NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (211, 212, 213, 2201, 2202, 2203, 2211, 2212, 2213, 2214)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);

-- 数据管理与代码生成菜单（M5）
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(214, 200, '数据管理', '/lc/db', 'lc/db/index', 'C', 'lc:db:list', 'ToolOutlined', 8, 0, 0, 'init', NOW()),
(215, 200, '代码生成', '/lc/gen', 'lc/gen/index', 'C', 'lc:gen:list', 'CodeOutlined', 9, 0, 0, 'init', NOW()),
(2130, 214, '可视化建表', NULL, NULL, 'F', 'lc:db:create', NULL, 1, 0, 0, 'init', NOW()),
(2140, 215, '生成下载', NULL, NULL, 'F', 'lc:gen:download', NULL, 1, 0, 0, 'init', NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (214, 215, 2130, 2140)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);

-- ---------------------------------------------------------------------
-- 12. 集成（M6：第三方 HTTP 接口 / 调用日志）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lc_http_api (
  id             BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id      VARCHAR(12)  NOT NULL DEFAULT '000000',
  code           VARCHAR(64)  NOT NULL COMMENT '接口编码（唯一）',
  name           VARCHAR(100) NOT NULL COMMENT '接口名称',
  method         VARCHAR(10)  NOT NULL DEFAULT 'GET' COMMENT 'GET/POST',
  url            VARCHAR(500) NOT NULL COMMENT '目标地址（支持 {param} 路径参数）',
  headers_json   VARCHAR(500) NULL COMMENT '请求头 JSON',
  body_template  MEDIUMTEXT   NULL COMMENT 'POST 请求体模板（支持 {param} 占位）',
  timeout_ms     INT          NOT NULL DEFAULT 5000,
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '（0启用 1停用）',
  remark         VARCHAR(500) NULL,
  create_by      VARCHAR(64)  NULL,
  create_time    DATETIME     NULL,
  update_by      VARCHAR(64)  NULL,
  update_time    DATETIME     NULL,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lc_http_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方 HTTP 接口配置表';

CREATE TABLE IF NOT EXISTS lc_http_log (
  id           BIGINT       NOT NULL,
  tenant_id    VARCHAR(12)  NOT NULL DEFAULT '000000',
  api_code     VARCHAR(64)  NOT NULL,
  method       VARCHAR(10)  NULL,
  url          VARCHAR(500) NULL,
  request_body MEDIUMTEXT   NULL,
  success      TINYINT      NOT NULL DEFAULT 0,
  cost_ms      BIGINT       NULL,
  resp_body    MEDIUMTEXT   NULL COMMENT '响应（截断 2000 字符）',
  create_time  DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_lc_http_log_code (api_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方 HTTP 调用日志表';

-- M6 菜单
INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
(216, 200, '接口管理', '/lc/http', 'lc/http/index', 'C', 'lc:http:list', 'ApiOutlined', 10, 0, 0, 'init', NOW()),
(217, 200, '服务监控', '/lc/monitor', 'lc/monitor/index', 'C', 'lc:monitor:list', 'FundOutlined', 11, 0, 0, 'init', NOW()),
(218, 200, '数据回收站', '/lc/recycle', 'lc/recycle/index', 'C', 'lc:recycle:list', 'DeleteOutlined', 12, 0, 0, 'init', NOW()),
(2221, 216, '接口新增', NULL, NULL, 'F', 'lc:http:add', NULL, 1, 0, 0, 'init', NOW()),
(2222, 216, '接口修改', NULL, NULL, 'F', 'lc:http:edit', NULL, 2, 0, 0, 'init', NOW()),
(2223, 216, '接口删除', NULL, NULL, 'F', 'lc:http:delete', NULL, 3, 0, 0, 'init', NOW()),
(2224, 216, '接口调用', NULL, NULL, 'F', 'lc:http:call', NULL, 4, 0, 0, 'init', NOW()),
(2231, 218, '回收站恢复', NULL, NULL, 'F', 'lc:recycle:restore', NULL, 1, 0, 0, 'init', NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (216, 217, 218, 2221, 2222, 2223, 2224, 2231)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);
