package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class YouzanClaimStore {

    private final JdbcTemplate jdbc;

    public record Snapshot(long id, Long merchantId, int points, Long levelId, int status, Long claimant,
                           boolean dated, String encryptedMobile, String fingerprint, boolean deleted) { }

    public List<Snapshot> lockByMobile(String mobileHash) {
        return jdbc.query("""
                SELECT id, merchant_id, legacy_points, mapped_level_id, restore_status, claimed_user_id,
                       claimed_at, encrypted_mobile, snapshot_hash, deleted
                FROM member_youzan_point_snapshot
                WHERE tenant_id = ? AND mobile_hash = ?
                ORDER BY id FOR UPDATE
                """, (row, index) -> new Snapshot(row.getLong("id"), row.getObject("merchant_id", Long.class),
                row.getInt("legacy_points"), row.getObject("mapped_level_id", Long.class), row.getInt("restore_status"),
                row.getObject("claimed_user_id", Long.class), row.getTimestamp("claimed_at") != null,
                row.getString("encrypted_mobile"), row.getString("snapshot_hash"), row.getBoolean("deleted")),
                TenantContextHolder.getRequiredTenantId(), mobileHash);
    }

    public void markClaimed(long snapshotId, String mobileHash, long userId) {
        int updated = jdbc.update("""
                UPDATE member_youzan_point_snapshot
                SET restore_status = 1, claimed_user_id = ?, claimed_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND tenant_id = ? AND mobile_hash = ?
                  AND restore_status = 0 AND claimed_user_id IS NULL AND claimed_at IS NULL AND deleted = 0
                """, userId, snapshotId, TenantContextHolder.getRequiredTenantId(), mobileHash);
        if (updated != 1) {
            throw new IllegalStateException("领取标记未更新，撤销本次到账");
        }
    }
}
