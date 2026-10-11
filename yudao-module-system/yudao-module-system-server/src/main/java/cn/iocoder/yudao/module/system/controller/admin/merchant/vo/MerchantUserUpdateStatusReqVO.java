package cn.iocoder.yudao.module.system.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 商家账号状态更新 Request VO")
@Data
public class MerchantUserUpdateStatusReqVO {

    @Schema(description = "账号编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "账号编号不能为空")
    private Long id;

    @Schema(description = "账号状态，参见 CommonStatusEnum", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "修改状态必须是 {value}")
    private Integer status;

}
