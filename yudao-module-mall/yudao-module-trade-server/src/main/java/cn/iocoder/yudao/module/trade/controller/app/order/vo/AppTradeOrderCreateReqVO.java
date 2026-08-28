package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Schema(description = "用户 App - 交易订单创建 Request VO")
@Data
public class AppTradeOrderCreateReqVO extends AppTradeOrderSettlementReqVO {

    @Schema(description = "备注", example = "这个是我的订单哟")
    private String remark;

    @Schema(description = "用户确认时的应付金额，单位：分", example = "8801")
    @PositiveOrZero(message = "预期应付金额不能小于 0")
    private Integer expectedPayPrice;

    @Schema(description = "用户确认时的实际使用积分", example = "3889")
    @PositiveOrZero(message = "预期使用积分不能小于 0")
    private Integer expectedUsePoint;

    @AssertTrue(message = "配送方式不能为空")
    @JsonIgnore
    public boolean isDeliveryTypeNotNull() {
        return getDeliveryType() != null;
    }

}
