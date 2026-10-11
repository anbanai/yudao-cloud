package cn.iocoder.yudao.module.system.dal.dataobject.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商家工作台用户。
 */
@TableName("system_merchant_user")
@KeySequence("system_merchant_user_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantUserDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String mobile;
    private Integer sex;
    private String avatar;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;

}
