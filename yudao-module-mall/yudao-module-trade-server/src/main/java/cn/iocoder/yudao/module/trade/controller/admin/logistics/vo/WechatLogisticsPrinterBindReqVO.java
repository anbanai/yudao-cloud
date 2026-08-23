package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLogisticsPrinterBindReqVO {

    @NotBlank(message = "打印员 openid 不能为空")
    private String openid;
    @NotBlank(message = "打印员更新类型不能为空")
    private String updateType = "bind";
    private String tagidList;
}
