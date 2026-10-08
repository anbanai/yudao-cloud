package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@Schema(description = "管理后台 - 交易报表查询 Request VO")
public class TradeReportPageReqVO extends PageParam {

    @Schema(description = "时间范围，备货单按付款时间，对账单按发货时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] dateTime;

    @Schema(description = "订单状态")
    private List<Integer> statuses;

    @Schema(description = "订单来源")
    private Integer terminal;

    @Schema(description = "配送方式")
    private Integer deliveryType;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "销售属性")
    private String properties;

    @Schema(description = "SKU 条码")
    private String barCode;

    @Schema(description = "物流单号")
    private String logisticsNo;
}
