package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;

import java.util.List;

@Data
public class WechatLogisticsPrinterRespVO {

    private Integer count;
    private List<String> openid;
    private List<String> tagidList;
}
