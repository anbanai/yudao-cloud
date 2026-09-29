package cn.iocoder.yudao.module.member.service.point;

import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointRecordDO;

public interface MemberPointBatchService {
    void grant(MemberPointRecordDO record);
    void consume(Long userId, Long recordId, int quantity, String bizId);
    void restore(Long userId, Long recordId, int quantity, String bizId);
    /**
     * Rolls back granted points from their still-available batches after a reward cancellation.
     * The caller is responsible for changing the user's aggregate point balance.
     */
    int rollbackGrant(Long userId, Long sourceRecordId, int quantity, String bizId);
    void expire();
}
