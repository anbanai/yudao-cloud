import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class SfAccountSql {
    static String encrypt(String value, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String sql(long tenant, long id, String partner, String check) {
        String filter = "tenant_id = " + tenant + " AND id = " + id + " AND deleted = 0";
        String fingerprint = "SHA2(CONCAT(partner_id, ':', check_word), 256)";
        return """
                -- 先执行本条，只核对所选账号。1 表示该字段已匹配。
                SELECT id, name, BINARY partner_id = '%s' AS partner_ok,
                       BINARY check_word = '%s' AS check_ok, %s AS old_fingerprint
                FROM trade_logistics_account WHERE %s;

                -- 两项都匹配时只需配置 Secret 和重启。确需重置同一顺丰账号时，先备份该行，
                -- 再用上面查询的 old_fingerprint 替换下面的占位符，只执行这一条 UPDATE。
                UPDATE trade_logistics_account
                SET partner_id = '%s', check_word = '%s'
                WHERE %s AND %s = 'REPLACE_WITH_OLD_FINGERPRINT';
                """.formatted(partner, check, fingerprint, filter, partner, check, filter, fingerprint);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("用法：java SfAccountSql.java 租户ID 账号ID");
        long tenant = Long.parseLong(args[0]);
        long id = Long.parseLong(args[1]);
        if (tenant < 0 || id < 1) throw new IllegalArgumentException("请检查租户和账号 ID");
        Console console = System.console();
        if (console == null) throw new IllegalStateException("请在交互式终端运行");
        byte[] key = new String(console.readPassword("商城 Secret 值：")).getBytes(StandardCharsets.UTF_8);
        if (key.length != 16 && key.length != 24 && key.length != 32)
            throw new IllegalArgumentException("密钥必须是 16、24 或 32 字节");
        String partner = new String(console.readPassword("该账号完整 Partner ID："));
        String check = new String(console.readPassword("该账号完整顺丰校验码："));
        if (partner.isBlank() || check.isBlank()) throw new IllegalArgumentException("两个顺丰凭据都必须填写");
        System.out.println(sql(tenant, id, encrypt(partner, key), encrypt(check, key)));
    }
}
