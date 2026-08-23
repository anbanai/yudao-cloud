package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 创建微信物流运单 Request VO")
@Data
public class WechatLogisticsWaybillCreateReqVO {

    @NotNull(message = "订单编号不能为空")
    private Long orderId;
}
