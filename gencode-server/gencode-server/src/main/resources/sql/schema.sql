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
