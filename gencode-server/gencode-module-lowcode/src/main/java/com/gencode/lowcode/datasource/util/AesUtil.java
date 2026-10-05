package com.gencode.lowcode.datasource.util;

import cn.hutool.core.util.StrUtil;
import com.gencode.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * AES 加解密工具（AES/ECB/PKCS5Padding）
 * 用于外部数据源密码：存库加密、使用时解密，任何出参一律置 null 不回显。
 * 密钥取 lc.crypto-key 配置（默认 gencode-lc-2026-key），归一化为 16 字节 AES-128 密钥。
 */
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    /** AES-128 密钥固定 16 字节 */
    private static final int KEY_BYTES = 16;

    @Value("${lc.crypto-key:gencode-lc-2026-key}")
    private String key;

    /** 加密：明文 -> Base64 密文（空值原样返回） */
    public String encrypt(String plain) {
        if (StrUtil.isEmpty(plain)) {
            return plain;
        }
        return doFinal(plain, Cipher.ENCRYPT_MODE);
    }

    /** 解密：Base64 密文 -> 明文（空值原样返回） */
    public String decrypt(String cipherText) {
        if (StrUtil.isEmpty(cipherText)) {
            return cipherText;
        }
        return doFinal(cipherText, Cipher.DECRYPT_MODE);
    }

    private String doFinal(String input, int mode) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(normalizedKey(), ALGORITHM));
            byte[] bytes;
            if (mode == Cipher.ENCRYPT_MODE) {
                bytes = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(bytes);
            }
            bytes = cipher.doFinal(Base64.getDecoder().decode(input));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BizException("数据源密码加解密失败");
        }
    }

    /** 密钥归一化为 16 字节：超出截断，不足右补字符 '0' */
    private byte[] normalizedKey() {
        byte[] raw = (key == null ? "" : key).getBytes(StandardCharsets.UTF_8);
        byte[] fixed = new byte[KEY_BYTES];
        for (int i = 0; i < KEY_BYTES; i++) {
            fixed[i] = i < raw.length ? raw[i] : (byte) '0';
        }
        return fixed;
    }
}
