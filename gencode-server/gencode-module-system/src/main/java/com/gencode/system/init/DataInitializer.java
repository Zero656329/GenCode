package com.gencode.system.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.hutool.crypto.digest.BCrypt;
import com.gencode.common.constant.Constants;
import com.gencode.framework.tenant.TenantContext;
import com.gencode.system.user.entity.SysUser;
import com.gencode.system.user.entity.SysUserRole;
import com.gencode.system.user.mapper.SysUserMapper;
import com.gencode.system.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 内置管理员数据初始化（幂等）：
 * 1. sys_user id=1 不存在则插入完整 admin 行；
 * 2. 已存在但密码为占位符/非 BCrypt 时重置为 admin123；
 * 3. 确保 (user=1, role=1) 绑定存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 启动阶段无请求上下文，用忽略租户的方式固定系统租户查询
        TenantContext.setTenantId(Constants.DEFAULT_TENANT);
        try {
            SysUser admin = userMapper.selectById(Constants.ADMIN_USER_ID);
            if (admin == null) {
                admin = new SysUser();
                admin.setId(Constants.ADMIN_USER_ID);
                admin.setTenantId(Constants.DEFAULT_TENANT);
                admin.setDeptId(100L);
                admin.setUsername("admin");
                admin.setNickname("超级管理员");
                admin.setPassword(BCrypt.hashpw("admin123", BCrypt.gensalt()));
                admin.setUserType(0);
                admin.setStatus(Constants.STATUS_NORMAL);
                admin.setRemark("平台内置管理员");
                admin.setCreateBy("init");
                userMapper.insert(admin);
                log.info("已初始化内置管理员账号 admin");
            } else if ("INIT_BY_STARTUP".equals(admin.getPassword())
                    || admin.getPassword() == null
                    || !admin.getPassword().startsWith("$2")) {
                userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getId, Constants.ADMIN_USER_ID)
                        .set(SysUser::getPassword, BCrypt.hashpw("admin123", BCrypt.gensalt())));
                log.info("已重置内置管理员密码为默认值");
            }
            Long bound = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, Constants.ADMIN_USER_ID)
                    .eq(SysUserRole::getRoleId, 1L));
            if (bound == 0) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(Constants.ADMIN_USER_ID);
                ur.setRoleId(1L);
                userRoleMapper.insert(ur);
            }
            log.info("GenCode 启动完成，默认账号：admin / admin123（租户 000000）");
        } finally {
            TenantContext.clear();
        }
    }
}
