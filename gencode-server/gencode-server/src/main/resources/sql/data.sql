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
