package cn.iocoder.yudao.module.member.service.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

/** Explicit scope predicates are intentional: JdbcTemplate is not covered by MyBatis tenant interception. */
@Repository
@RequiredArgsConstructor
public class IdentityRepository {
    private final JdbcTemplate jdbc;
    public void lockReadyScope(String scope) {
        List<Integer> rows = jdbc.query("SELECT ready FROM member_identity_scope WHERE scope=? FOR UPDATE",
                (rs, n) -> rs.getInt(1), scope);
        if (rows.size() != 1 || rows.get(0) != 1) { throw IdentityPolicy.unavailable(); }
    }
    public Map<String, Object> account(String scope, String openid) {
        return one("SELECT * FROM member_identity_account WHERE scope=? AND openid=?", scope, openid);
    }
    public Map<String, Object> member(String scope, Long member) {
        return one("SELECT * FROM member_identity_account WHERE scope=? AND member_id=?", scope, member);
    }
    public Map<String, Object> subject(String scope, String subject) {
        return one("SELECT * FROM member_identity_account WHERE scope=? AND subject_id=?", scope, subject);
    }
    public Map<String, Object> operation(String scope, String requestId) {
        return one("SELECT * FROM member_identity_operation WHERE scope=? AND request_id=?", scope, requestId);
    }
    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.size() > 1) { throw IdentityPolicy.conflict(); }
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void update(String sql, Object... args) { jdbc.update(sql, args); }
    public List<Map<String, Object>> pending(String scope) {
        return jdbc.queryForList("SELECT * FROM member_identity_outbox WHERE scope=? AND state='PENDING' AND next_attempt<=? ORDER BY id LIMIT 50", scope, System.currentTimeMillis());
    }
    public List<Map<String, Object>> expiredPhoneConsents(String scope, String consentRevision) {
        return jdbc.queryForList("SELECT * FROM member_identity_account WHERE scope=? AND phone_sharing=1 "
                + "AND (phone_consent_revision IS NULL OR phone_consent_revision<>?) "
                + "ORDER BY member_id LIMIT 50", scope, consentRevision);
    }
    public static String string(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key); return value == null ? null : value.toString();
    }
    public static long number(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key); return value == null ? 0 : value instanceof Boolean ? ((Boolean) value ? 1 : 0) : ((Number) value).longValue();
    }
}
