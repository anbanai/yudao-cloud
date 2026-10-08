package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 交易报表历史分页 Request VO")
public class TradeReportHistoryPageReqVO extends PageParam {
    private String reportType;
}
