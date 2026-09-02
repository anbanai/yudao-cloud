package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LogisticsWaybillBatchCreateReqVO {
    @NotEmpty
    @Size(max = 100, message = "订单数量不能超过 100 个")
    private List<@NotNull(message = "订单编号不能为空") Long> orderIds;
    private Long accountId;
    private Long deviceId;
}
