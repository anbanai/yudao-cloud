package cn.iocoder.yudao.module.pay.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 未发起易宝预支付时，原子更新支付金额及对应商家资金计划。 */
@Data
public class PayOrderUpdatePriceReqDTO {

    @NotNull(message = "支付单编号不能为空")
    private Long id;

    @NotNull(message = "支付金额不能为空")
    @Min(value = 1, message = "支付金额必须大于零")
    private Integer payPrice;

    @Valid
    @NotNull(message = "商家分账计划不能为空")
    private List<PayOrderDividePlanDTO> dividePlan;

}
