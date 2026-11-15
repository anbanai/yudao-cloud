package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "管理后台 - 运营取消订单 Request VO")
@Data
public class TradeOrderCancelReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "订单编号不能为空")
    private Long id;

    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户申请取消")
    @Length(max = 128, message = "取消原因长度不能超过 128")
    private String reason;

}
