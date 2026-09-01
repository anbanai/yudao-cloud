package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LogisticsWaybillCreateReqVO {
    @NotNull private Long orderId;
    private Long accountId;
    private Long deviceId;
}
