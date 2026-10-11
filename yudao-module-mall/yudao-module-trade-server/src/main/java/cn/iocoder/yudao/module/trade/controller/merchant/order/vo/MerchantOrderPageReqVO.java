package cn.iocoder.yudao.module.trade.controller.merchant.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "商家端 - 履约订单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantOrderPageReqVO extends PageParam {

    @Schema(description = "商家履约订单号")
    private String no;

    @Schema(description = "履约状态")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
