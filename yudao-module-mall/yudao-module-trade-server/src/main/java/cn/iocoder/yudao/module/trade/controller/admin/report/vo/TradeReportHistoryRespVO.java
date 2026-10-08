package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "管理后台 - 交易报表历史 Response VO")
public class TradeReportHistoryRespVO {
    private Long id;
    private String reportType;
    private Long orderCount;
    private Integer productTypeCount;
    private Integer productCount;
    private Long amount;
    private LocalDateTime createTime;
    private String filtersJson;
}
