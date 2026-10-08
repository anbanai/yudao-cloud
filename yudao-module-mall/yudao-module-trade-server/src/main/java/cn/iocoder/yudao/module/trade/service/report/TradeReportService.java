package cn.iocoder.yudao.module.trade.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.controller.admin.report.vo.*;

public interface TradeReportService {
    TradeReportPageRespVO getReportPage(String reportType, TradeReportPageReqVO reqVO);

    Long saveHistory(Long operatorId, String reportType, TradeReportPageReqVO reqVO);

    PageResult<TradeReportHistoryRespVO> getHistoryPage(TradeReportHistoryPageReqVO reqVO);

    TradeReportPageRespVO getHistory(String reportType, Long id);

    PageResult<TradeReportRowRespVO> getRows(String reportType, TradeReportPageReqVO reqVO);
}
