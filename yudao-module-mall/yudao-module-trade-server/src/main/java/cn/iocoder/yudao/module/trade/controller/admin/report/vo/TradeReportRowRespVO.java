package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 交易报表商品行")
public class TradeReportRowRespVO {
    private Long spuId;
    private Long skuId;
    private String productName;
    private String picUrl;
    private String barCode;
    private String properties;
    private Integer quantity;
    private Long amount;
    private Integer orderCount;
}
