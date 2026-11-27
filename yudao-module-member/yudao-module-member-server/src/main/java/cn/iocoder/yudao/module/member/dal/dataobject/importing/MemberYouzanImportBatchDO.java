package cn.iocoder.yudao.module.member.dal.dataobject.importing;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName(value = "member_youzan_import_batch", autoResultMap = true)
@KeySequence("member_youzan_import_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberYouzanImportBatchDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long operatorId;
    private Long merchantId;
    private String maskedRows;
    private Integer status;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer importedRows;
    private Integer skippedRows;
    private Integer changedRows;
    private LocalDateTime expiresAt;
    @TableField(typeHandler = EncryptTypeHandler.class, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String encryptedPayload;
}
