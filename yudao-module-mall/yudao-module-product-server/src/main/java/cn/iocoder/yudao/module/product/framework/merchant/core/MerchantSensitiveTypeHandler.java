package cn.iocoder.yudao.module.product.framework.merchant.core;

import cn.hutool.core.lang.Assert;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/** 商家敏感字段的版本化加密。明文兼容仅接受升级脚本明确标记的数据。 */
public class MerchantSensitiveTypeHandler extends BaseTypeHandler<String> {

    public static final String CIPHERTEXT_PREFIX = "AES:v1:";
    public static final String LEGACY_PLAINTEXT_PREFIX = "PLAIN:v0:";
    private static final String KEY_PROPERTY = "mybatis-plus.encryptor.password";
    private static final String MIGRATION_PROPERTY = "yudao.product.merchant.encryption.allow-marked-plaintext";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    public static String encrypt(String value) {
        if (value == null) {
            return null;
        }
        return CIPHERTEXT_PREFIX + SecureUtil.aes(key()).encryptBase64(value);
    }

    public static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith(LEGACY_PLAINTEXT_PREFIX)) {
            if (!Boolean.parseBoolean(SpringUtil.getProperty(MIGRATION_PROPERTY))) {
                throw new IllegalStateException("商家明文迁移兼容尚未开启");
            }
            return value.substring(LEGACY_PLAINTEXT_PREFIX.length());
        }
        String ciphertext = value.startsWith(CIPHERTEXT_PREFIX)
                ? value.substring(CIPHERTEXT_PREFIX.length()) : value;
        // 已部署的旧 EncryptTypeHandler 写入无前缀 Base64 AES。仅接受完整密文；
        // 损坏密文、任意原始明文都必须报错，不能按解密失败回退为明文。
        try {
            byte[] bytes = Base64.getDecoder().decode(ciphertext);
            if (bytes.length == 0 || bytes.length % 16 != 0) {
                throw new IllegalArgumentException("Invalid ciphertext size");
            }
            return SecureUtil.aes(key()).decryptStr(bytes, StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            // 不将字段内容或加密库异常报文交给日志/接口异常处理。
            throw new IllegalStateException("商家敏感字段密文无效或密钥不匹配");
        }
    }

    private static byte[] key() {
        String password = SpringUtil.getProperty(KEY_PROPERTY);
        Assert.notEmpty(password, "配置项({}) 不能为空", KEY_PROPERTY);
        return password.getBytes(StandardCharsets.UTF_8);
    }
}
