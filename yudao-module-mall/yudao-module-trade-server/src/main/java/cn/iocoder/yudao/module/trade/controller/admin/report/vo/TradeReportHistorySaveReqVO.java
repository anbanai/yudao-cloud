package cn.iocoder.yudao.module.trade.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 保存交易报表历史 Request VO")
public class TradeReportHistorySaveReqVO {
    @NotBlank
    private String reportType;
    @Valid
    private TradeReportPageReqVO query;
}
