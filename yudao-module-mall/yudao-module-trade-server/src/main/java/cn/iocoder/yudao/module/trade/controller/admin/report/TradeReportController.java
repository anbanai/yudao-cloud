package cn.iocoder.yudao.module.trade.controller.admin.report;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.trade.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.trade.service.report.TradeReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 交易报表")
@RestController
@RequestMapping("/trade/report")
@Validated
public class TradeReportController {

    @Resource
    private TradeReportService tradeReportService;

    @GetMapping("/picking/page")
    @Operation(summary = "获得备货单分页")
    @PreAuthorize("@ss.hasPermission('trade:report:picking:query')")
    public CommonResult<TradeReportPageRespVO> getPickingPage(@Valid TradeReportPageReqVO reqVO) {
        return success(tradeReportService.getReportPage("picking", reqVO));
    }

    @GetMapping("/reconciliation/page")
    @Operation(summary = "获得对账单分页")
    @PreAuthorize("@ss.hasPermission('trade:report:reconciliation:query')")
    public CommonResult<TradeReportPageRespVO> getReconciliationPage(@Valid TradeReportPageReqVO reqVO) {
        return success(tradeReportService.getReportPage("reconciliation", reqVO));
    }

    @PostMapping("/{reportType}/history/save")
    @Operation(summary = "保存交易报表历史快照")
    @PreAuthorize("@ss.hasPermission('trade:report:' + #reportType + ':save')")
    public CommonResult<Long> saveHistory(@PathVariable String reportType,
                                          @Valid @RequestBody TradeReportHistorySaveReqVO reqVO) {
        validateReportType(reportType);
        if (!reportType.equalsIgnoreCase(reqVO.getReportType())) {
            throw new IllegalArgumentException("报表类型与请求路径不一致");
        }
        TradeReportPageReqVO query = reqVO.getQuery() == null ? new TradeReportPageReqVO() : reqVO.getQuery();
        return success(tradeReportService.saveHistory(getLoginUserId(), reportType, query));
    }

    @GetMapping("/{reportType}/history/page")
    @Operation(summary = "获得交易报表历史分页")
    @PreAuthorize("@ss.hasPermission('trade:report:' + #reportType + ':query')")
    public CommonResult<PageResult<TradeReportHistoryRespVO>> getHistoryPage(@PathVariable String reportType,
                                                                               @Valid TradeReportHistoryPageReqVO reqVO) {
        validateReportType(reportType);
        reqVO.setReportType(reportType);
        return success(tradeReportService.getHistoryPage(reqVO));
    }

    @GetMapping("/{reportType}/history/get")
    @Operation(summary = "获得交易报表历史详情")
    @PreAuthorize("@ss.hasPermission('trade:report:' + #reportType + ':query')")
    public CommonResult<TradeReportPageRespVO> getHistory(@PathVariable String reportType,
                                                          @RequestParam("id") Long id) {
        validateReportType(reportType);
        return success(tradeReportService.getHistory(reportType, id));
    }

    @GetMapping("/{reportType}/history/{id}/export-excel")
    @Operation(summary = "导出交易报表历史快照")
    @PreAuthorize("@ss.hasPermission('trade:report:' + #reportType + ':export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportHistoryExcel(@PathVariable String reportType, @PathVariable Long id,
                                   HttpServletResponse response) throws IOException {
        validateReportType(reportType);
        TradeReportPageRespVO history = tradeReportService.getHistory(reportType, id);
        if (history == null) {
            throw new IllegalArgumentException("历史报表不存在");
        }
        ExcelUtils.write(response, reportType + "-history-" + id + ".xls", "历史快照", TradeReportExcelVO.class,
                convert(history.getList()));
    }

    @GetMapping("/{reportType}/export-excel")
    @Operation(summary = "导出交易报表汇总")
    @PreAuthorize("@ss.hasPermission('trade:report:' + #reportType + ':export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportExcel(@PathVariable String reportType, @Valid TradeReportPageReqVO reqVO,
                            HttpServletResponse response) throws IOException {
        validateReportType(reportType);
        List<TradeReportExcelVO> data = convert(tradeReportService.getRows(reportType, all(reqVO)).getList());
        ExcelUtils.write(response, reportType + "-summary.xls", "商品汇总", TradeReportExcelVO.class, data);
    }

    @GetMapping("/{reportType}/export-detail-excel")
    @Operation(summary = "导出交易报表 SKU 明细")
    @PreAuthorize("@ss.hasPermission('trade:report:' + #reportType + ':export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportDetailExcel(@PathVariable String reportType, @Valid TradeReportPageReqVO reqVO,
                                  HttpServletResponse response) throws IOException {
        validateReportType(reportType);
        List<TradeReportDetailExcelVO> data = convertDetail(tradeReportService.getRows(reportType, all(reqVO)).getList());
        ExcelUtils.write(response, reportType + "-detail.xls", "SKU明细", TradeReportDetailExcelVO.class, data);
    }

    private static TradeReportPageReqVO all(TradeReportPageReqVO source) {
        source.setPageNo(1);
        source.setPageSize(-1);
        return source;
    }

    private static void validateReportType(String reportType) {
        if (!"picking".equalsIgnoreCase(reportType) && !"reconciliation".equalsIgnoreCase(reportType)) {
            throw new IllegalArgumentException("不支持的报表类型");
        }
    }

    private static List<TradeReportExcelVO> convert(List<TradeReportRowRespVO> rows) {
        return rows.stream().map(row -> {
            TradeReportExcelVO vo = new TradeReportExcelVO();
            vo.setProductName(row.getProductName());
            vo.setProperties(row.getProperties());
            vo.setBarCode(row.getBarCode());
            vo.setQuantity(row.getQuantity());
            vo.setOrderCount(row.getOrderCount());
            vo.setAmount(row.getAmount());
            return vo;
        }).toList();
    }

    private static List<TradeReportDetailExcelVO> convertDetail(List<TradeReportRowRespVO> rows) {
        return rows.stream().map(row -> {
            TradeReportDetailExcelVO vo = new TradeReportDetailExcelVO();
            vo.setSpuId(row.getSpuId());
            vo.setSkuId(row.getSkuId());
            vo.setProductName(row.getProductName());
            vo.setProperties(row.getProperties());
            vo.setBarCode(row.getBarCode());
            vo.setQuantity(row.getQuantity());
            vo.setOrderCount(row.getOrderCount());
            vo.setAmount(row.getAmount());
            return vo;
        }).toList();
    }
}
