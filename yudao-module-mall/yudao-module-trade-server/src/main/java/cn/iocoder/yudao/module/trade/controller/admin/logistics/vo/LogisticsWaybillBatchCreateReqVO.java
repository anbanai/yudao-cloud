package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class LogisticsWaybillBatchCreateReqVO {
    @NotEmpty private List<Long> orderIds;
    private Long accountId;
    private Long deviceId;
}
