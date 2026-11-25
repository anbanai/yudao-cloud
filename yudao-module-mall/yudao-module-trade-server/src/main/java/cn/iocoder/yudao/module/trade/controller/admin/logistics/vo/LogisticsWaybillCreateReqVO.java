package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LogisticsWaybillCreateReqVO {
    @NotNull private Long orderId;
    /** 商家履约订单编号。 */
    @NotNull
    private Long subOrderId;
    private Long accountId;
    private Long deviceId;
}
