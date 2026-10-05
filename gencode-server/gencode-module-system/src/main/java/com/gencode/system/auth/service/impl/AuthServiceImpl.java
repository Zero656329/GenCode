package com.gencode.system.auth.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.framework.security.mapper.AuthPermMapper;
import com.gencode.framework.tenant.TenantContext;
import com.gencode.framework.util.IpUtil;
import com.gencode.system.auth.dto.CaptchaVO;
import com.gencode.system.auth.dto.LoginBody;
import com.gencode.system.auth.dto.LoginResult;
import com.gencode.system.auth.dto.LoginUser;
import com.gencode.system.auth.service.AuthService;
import com.gencode.system.log.entity.SysLoginLog;
import com.gencode.system.log.service.LoginLogService;
import com.gencode.system.user.entity.SysUser;
import com.gencode.system.user.mapper.SysUserMapper;
import com.gencode.system.dept.entity.SysDept;
import com.gencode.system.dept.mapper.SysDeptMapper;
import com.gencode.system.tenant.entity.SysTenant;
import com.gencode.system.tenant.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 认证服务实现：算术验证码（Redis，5 分钟有效）、BCrypt 密码、失败锁定（5 次/10 分钟，Redis）、登录日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Duration CAPTCHA_EXPIRE = Duration.ofMinutes(5);
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final Pattern MATH_PATTERN = Pattern.compile("(\\d+)\\s*([+\\-])\\s*(\\d+)");
    private static final String KEY_CAPTCHA = "gencode:captcha:";
    private static final String KEY_FAIL = "gencode:login:fail:";
    private static final String KEY_LOCK = "gencode:login:lock:";

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysTenantMapper tenantMapper;
    private final AuthPermMapper authPermMapper;
    private final LoginLogService loginLogService;
    private final StringRedisTemplate redis;

    @Override
    public CaptchaVO captcha() {
        // hutool 5.8.32 无 createMathCaptcha，改用 createLineCaptcha 自绘算术表达式（两个 1~20 整数 +/-）
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40);
        captcha.setGenerator(new CodeGenerator() {
            @Override
            public String generate() {
                int a = RandomUtil.randomInt(1, 21);
                int b = RandomUtil.randomInt(1, 21);
                if (RandomUtil.randomBoolean()) {
                    return a + "+" + b;
                }
                // 减法保证结果非负
                return Math.max(a, b) + "-" + Math.min(a, b);
            }

            @Override
            public boolean verify(String userInputCode, String generatedCode) {
                return StrUtil.equals(resolveMathAnswer(generatedCode), StrUtil.trim(userInputCode));
            }
        });
        captcha.createCode();
        String answer = resolveMathAnswer(captcha.getCode());
        String captchaId = IdUtil.fastSimpleUUID();
        redis.opsForValue().set(KEY_CAPTCHA + captchaId, answer, CAPTCHA_EXPIRE);
        return new CaptchaVO(captchaId, captcha.getImageBase64Data());
    }

    @Override
    public LoginResult login(LoginBody body) {
        checkLocked(body.getUsername());
        checkCaptcha(body);

        String tenantId = StrUtil.blankToDefault(body.getTenantId(), Constants.DEFAULT_TENANT);
        String ip = IpUtil.getClientIp(currentRequest());

        TenantContext.setTenantId(tenantId);
        try {
            SysUser user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, body.getUsername()));
            if (user == null) {
                throw new BizException("用户不存在");
            }
            if (!checkPassword(body.getPassword(), user.getPassword())) {
                throw new BizException("密码错误");
            }
            if (user.getStatus() != null && user.getStatus() == 1) {
                throw new BizException("账号已停用");
            }
            SysTenant tenant = tenantMapper.selectOne(
                    new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getTenantId, tenantId));
            if (tenant == null || (tenant.getStatus() != null && tenant.getStatus() == 1)) {
                throw new BizException("租户已停用或不存在");
            }

            LoginUser loginUser = buildLoginUser(user);

            // 互斥登录（is-concurrent=false），设备 pc
            StpUtil.login(user.getId(), new SaLoginModel().setDevice("pc"));
            SaSession session = StpUtil.getSession();
            session.set("tenantId", tenantId);
            session.set("username", user.getUsername());
            session.set("loginUser", loginUser);

            // 更新最后登录信息
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getLoginIp, ip)
                    .set(SysUser::getLoginDate, LocalDateTime.now()));

            redis.delete(KEY_FAIL + body.getUsername());
            recordLoginLog(tenantId, body.getUsername(), ip, 0, "登录成功");
            return new LoginResult(StpUtil.getTokenValue(), loginUser);
        } catch (BizException e) {
            recordLoginLog(tenantId, body.getUsername(), ip, 1, e.getMessage());
            // 记录失败次数（验证码/锁定异常不计入密码失败）
            if ("用户不存在".equals(e.getMessage()) || "密码错误".equals(e.getMessage())) {
                recordFail(body.getUsername());
            }
            throw e;
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public LoginUser me() {
        LoginUser cached = (LoginUser) StpUtil.getSession().get("loginUser");
        if (cached != null) {
            return cached;
        }
        SysUser user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return buildLoginUser(user);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    // ------------------------------------------------------------------ 私有方法

    private boolean checkPassword(String raw, String hashed) {
        try {
            return BCrypt.checkpw(raw, hashed);
        } catch (Exception e) {
            return false;
        }
    }

    private void checkCaptcha(LoginBody body) {
        String key = KEY_CAPTCHA + body.getCaptchaId();
        String answer = redis.opsForValue().get(key);
        redis.delete(key);
        if (StrUtil.isBlank(body.getCaptchaCode()) || answer == null
                || !answer.equals(body.getCaptchaCode().trim())) {
            throw new BizException("验证码错误");
        }
    }

    private void checkLocked(String username) {
        if (Boolean.TRUE.equals(redis.hasKey(KEY_LOCK + username))) {
            throw new BizException("连续失败" + MAX_FAIL_COUNT + "次，账号已锁定，请10分钟后再试");
        }
    }

    private void recordFail(String username) {
        String key = KEY_FAIL + username;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, LOCK_DURATION);
        }
        if (count != null && count >= MAX_FAIL_COUNT) {
            redis.opsForValue().set(KEY_LOCK + username, "1", LOCK_DURATION);
            redis.delete(key);
        }
    }

    /** 组装 LoginUser（roles/perms 与 StpInterfaceImpl 同源） */
    private LoginUser buildLoginUser(SysUser user) {
        LoginUser lu = new LoginUser();
        lu.setId(user.getId());
        lu.setUsername(user.getUsername());
        lu.setNickname(user.getNickname());
        lu.setAvatar(user.getAvatar());
        lu.setTenantId(user.getTenantId());
        lu.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            lu.setDeptName(dept != null ? dept.getName() : null);
        }
        List<String> roles = authPermMapper.selectRoleKeysByUserId(user.getId());
        lu.setRoles(roles);
        if (roles != null && roles.contains(Constants.ROLE_SUPER_ADMIN)) {
            lu.setPerms(List.of(Constants.PERM_ALL));
        } else {
            lu.setPerms(authPermMapper.selectPermsByUserId(user.getId())
                    .stream().filter(StrUtil::isNotBlank).distinct().toList());
        }
        return lu;
    }

    private void recordLoginLog(String tenantId, String username, String ip, int status, String msg) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setTenantId(tenantId);
            loginLog.setUsername(username);
            loginLog.setIp(ip);
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogService.record(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
        }
    }

    /** 从算术验证码生成串中解析答案，兼容 "12+3="、"12+3" 或直接返回数值两种情况 */
    private String resolveMathAnswer(String code) {
        if (StrUtil.isBlank(code)) {
            return "";
        }
        String expr = code.replace("=", "").replace("?", "").trim();
        Matcher m = MATH_PATTERN.matcher(expr);
        if (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(3));
            return String.valueOf("-".equals(m.group(2)) ? a - b : a + b);
        }
        return expr;
    }

    private jakarta.servlet.http.HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
