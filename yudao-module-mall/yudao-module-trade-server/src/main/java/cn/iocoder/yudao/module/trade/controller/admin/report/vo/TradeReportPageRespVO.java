package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理后台 - 交易报表分页 Response VO")
public class TradeReportPageRespVO {
    private Long total;
    private Long orderCount;
    private Integer productTypeCount;
    private Integer productCount;
    private Long amount;
    private List<TradeReportRowRespVO> list;
}
