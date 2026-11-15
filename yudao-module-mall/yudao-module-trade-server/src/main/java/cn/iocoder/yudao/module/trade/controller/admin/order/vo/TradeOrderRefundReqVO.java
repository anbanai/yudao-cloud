package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "管理后台 - 订单部分退款 Request VO")
@Data
public class TradeOrderRefundReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "订单编号不能为空")
    private Long id;

    @Schema(description = "退款金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于零")
    private Integer refundPrice;

    @Schema(description = "退款原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "补偿运费差价")
    @Length(max = 128, message = "退款原因长度不能超过 128")
    private String reason;

}
