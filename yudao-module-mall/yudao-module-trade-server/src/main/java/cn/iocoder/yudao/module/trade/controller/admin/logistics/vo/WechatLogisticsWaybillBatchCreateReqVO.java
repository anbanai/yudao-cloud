package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WechatLogisticsWaybillBatchCreateReqVO {

    @NotEmpty(message = "订单编号不能为空")
    private List<Long> orderIds;
}
