package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;
import cn.iocoder.yudao.module.trade.service.logistics.WechatLogisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 微信物流助手")
@RestController
@RequestMapping("/trade/logistics/wechat")
@Validated
public class WechatLogisticsController {

    @Resource
    private WechatLogisticsService wechatLogisticsService;

    @PostMapping("/config/save")
    @Operation(summary = "保存微信物流助手配置")
    @PreAuthorize("@ss.hasPermission('trade:logistics:config:update')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody WechatLogisticsConfigSaveReqVO reqVO) {
        wechatLogisticsService.saveConfig(reqVO);
        return success(true);
    }

    @GetMapping("/account-status")
    @Operation(summary = "检查微信物流助手账号状态")
    @PreAuthorize("@ss.hasPermission('trade:logistics:account:query')")
    public CommonResult<WechatLogisticsAccountStatusRespVO> getAccountStatus() {
        return success(wechatLogisticsService.getAccountStatus());
    }

    @GetMapping("/pending")
    @Operation(summary = "查询待确认打印运单")
    @PreAuthorize("@ss.hasPermission('trade:logistics:waybill:query')")
    public CommonResult<List<WechatLogisticsWaybillRespVO>> getPendingWaybills() {
        return success(wechatLogisticsService.getPendingWaybills());
    }

    @PostMapping("/waybills/create")
    @Operation(summary = "创建微信物流运单")
    @PreAuthorize("@ss.hasPermission('trade:logistics:waybill:create')")
    public CommonResult<WechatLogisticsWaybillRespVO> createWaybill(
            @Valid @RequestBody WechatLogisticsWaybillCreateReqVO reqVO) {
        WechatLogisticsWaybillRespVO result = wechatLogisticsService.createWaybill(reqVO.getOrderId());
        if (result.getErrorCode() != null) {
            return CommonResult.error(result.getErrorCode(), result.getErrorMessage());
        }
        return success(result);
    }

    @PostMapping("/waybills/batch-create")
    @Operation(summary = "批量创建微信物流运单")
    @PreAuthorize("@ss.hasPermission('trade:logistics:waybill:create')")
    public CommonResult<List<WechatLogisticsWaybillRespVO>> batchCreateWaybills(
            @Valid @RequestBody WechatLogisticsWaybillBatchCreateReqVO reqVO) {
        return success(wechatLogisticsService.batchCreateWaybills(reqVO.getOrderIds()));
    }

    @PostMapping("/waybills/{id}/confirm-print")
    @Operation(summary = "确认已打印并发货")
    @Parameter(name = "id", description = "微信物流运单编号", required = true)
    @PreAuthorize("@ss.hasPermission('trade:logistics:waybill:confirm-print')")
    public CommonResult<WechatLogisticsWaybillRespVO> confirmPrint(@PathVariable("id") Long id) {
        return success(wechatLogisticsService.confirmPrint(id));
    }

    @PostMapping("/waybills/{id}/cancel")
    @Operation(summary = "取消微信物流运单")
    @PreAuthorize("@ss.hasPermission('trade:logistics:waybill:cancel')")
    public CommonResult<Boolean> cancelWaybill(@PathVariable("id") Long id) {
        wechatLogisticsService.cancelWaybill(id);
        return success(true);
    }

    @GetMapping("/waybills/{id}")
    @Operation(summary = "查询微信物流运单")
    @PreAuthorize("@ss.hasPermission('trade:logistics:waybill:query')")
    public CommonResult<WechatLogisticsWaybillRespVO> getWaybill(@PathVariable("id") Long id) {
        return success(wechatLogisticsService.getWaybill(id));
    }

    @GetMapping("/waybills/{id}/trace")
    @Operation(summary = "查询微信物流轨迹")
    @PreAuthorize("@ss.hasPermission('trade:logistics:trace:query')")
    public CommonResult<List<WechatLogisticsTraceRespVO>> getTrace(@PathVariable("id") Long id) {
        return success(wechatLogisticsService.getTrace(id));
    }

    @PostMapping("/waybills/{id}/trace/sync")
    @Operation(summary = "同步微信物流轨迹")
    @PreAuthorize("@ss.hasPermission('trade:logistics:trace:sync')")
    public CommonResult<Boolean> syncTrace(@PathVariable("id") Long id) {
        wechatLogisticsService.syncTrace(id);
        return success(true);
    }

    @PostMapping("/printers/bind")
    @Operation(summary = "绑定微信物流打印员")
    @PreAuthorize("@ss.hasPermission('trade:logistics:printer:update')")
    public CommonResult<WechatLogisticsPrinterRespVO> bindPrinter(
            @Valid @RequestBody WechatLogisticsPrinterBindReqVO reqVO) {
        return success(wechatLogisticsService.bindPrinter(reqVO));
    }

    @GetMapping("/printers")
    @Operation(summary = "查询微信物流打印员")
    @PreAuthorize("@ss.hasPermission('trade:logistics:printer:query')")
    public CommonResult<WechatLogisticsPrinterRespVO> getPrinter() {
        return success(wechatLogisticsService.getPrinter());
    }
}
