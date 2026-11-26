package cn.iocoder.yudao.module.member.dal.dataobject.importing;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName(value = "member_youzan_point_snapshot", autoResultMap = true)
@KeySequence("member_youzan_point_snapshot_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberYouzanPointSnapshotDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long batchId;
    private Long merchantId;
    private Long mappedLevelId;
    private String mobileHash;
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String encryptedMobile;
    private Integer legacyPoints;
    private Integer growthValue;
    private String freeLevel;
    private String snapshotHash;
    private Integer restoreStatus;
}
