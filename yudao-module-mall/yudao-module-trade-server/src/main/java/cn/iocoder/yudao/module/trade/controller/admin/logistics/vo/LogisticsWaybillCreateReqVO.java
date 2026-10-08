package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LogisticsWaybillCreateReqVO {
    @NotNull private Long orderId;
    /** 商家履约订单编号；为空时兼容历史主订单面单。 */
    private Long subOrderId;
    private Long accountId;
    private Long deviceId;
}
