package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsManagementService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 顺丰直连打单")
@RestController
@RequestMapping("/trade/logistics/sf")
public class SfPrintBridgeLogisticsController {

    @Resource private LogisticsManagementService managementService;
    @Resource private LogisticsWaybillService waybillService;

    @GetMapping("/accounts")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-account:query')")
    public CommonResult<List<SfLogisticsAccountRespVO>> getAccounts() {
        return success(managementService.getAccounts());
    }

    @PostMapping("/accounts")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-account:update')")
    public CommonResult<Long> saveAccount(@Valid @RequestBody SfLogisticsAccountSaveReqVO request) {
        return success(managementService.saveAccount(request));
    }

    @GetMapping("/devices")
    @PreAuthorize("@ss.hasPermission('trade:logistics:device:query')")
    public CommonResult<List<LogisticsPrintDeviceRespVO>> getDevices() {
        return success(managementService.getDevices());
    }

    @PostMapping("/devices")
    @PreAuthorize("@ss.hasPermission('trade:logistics:device:update')")
    public CommonResult<LogisticsPrintDeviceRespVO> saveDevice(
            @Valid @RequestBody LogisticsPrintDeviceSaveReqVO request) {
        return success(managementService.saveDevice(request));
    }

    @PostMapping("/devices/enroll")
    @PreAuthorize("@ss.hasPermission('trade:logistics:device:update')")
    public CommonResult<LogisticsPrintDeviceRespVO> enrollDevice(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return success(managementService.enrollDevice());
    }

    @PostMapping("/diagnostics/test-payload")
    @Operation(summary = "创建只用于浏览器 JSSDK 测试打印的短时效标签")
    @PreAuthorize("@ss.hasPermission('trade:logistics:diagnostics')")
    public CommonResult<String> createDiagnosticPayload(
            @Valid @RequestBody(required = false) LogisticsDiagnosticPayloadReqVO request) {
        LogisticsDiagnosticPayloadReqVO effective = request == null ? new LogisticsDiagnosticPayloadReqVO() : request;
        return success(managementService.createDiagnosticPayload(effective.getPaperWidthMm(),
                effective.getPaperHeightMm()));
    }

    @GetMapping("/pending")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-waybill:query')")
    public CommonResult<List<LogisticsPendingOrderRespVO>> getPendingOrders() {
        return success(waybillService.getPendingOrders());
    }

    @PostMapping("/waybills/create")
    @PreAuthorize("@ss.hasAnyPermissions('trade:logistics:sf-waybill:create', 'trade:order:update')")
    public CommonResult<LogisticsWaybillRespVO> createWaybill(
            @Valid @RequestBody LogisticsWaybillCreateReqVO request) {
        return success(waybillService.createWaybill(request));
    }

    @PostMapping("/waybills/batch-create")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-waybill:create')")
    public CommonResult<List<LogisticsWaybillRespVO>> batchCreateWaybill(
            @Valid @RequestBody LogisticsWaybillBatchCreateReqVO request) {
        List<LogisticsWaybillRespVO> result = new ArrayList<>();
        for (Long orderId : request.getOrderIds().stream().distinct().toList()) {
            try {
                result.add(waybillService.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(orderId)
                        .setAccountId(request.getAccountId()).setDeviceId(request.getDeviceId())));
            } catch (ServiceException exception) {
                result.add(new LogisticsWaybillRespVO().setOrderId(orderId).setStatus("FAILED")
                        .setErrorCode(String.valueOf(exception.getCode())).setErrorMessage(exception.getMessage()));
            }
        }
        return success(result);
    }

    @GetMapping("/waybills")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-waybill:query')")
    public CommonResult<List<LogisticsWaybillRespVO>> getWaybills() {
        return success(waybillService.getWaybills());
    }

    @GetMapping("/waybills/{id}")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-waybill:query')")
    public CommonResult<LogisticsWaybillRespVO> getWaybill(@PathVariable("id") Long id) {
        return success(waybillService.getWaybill(id));
    }

    @PostMapping("/waybills/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-waybill:cancel')")
    public CommonResult<Boolean> cancelWaybill(@PathVariable("id") Long id) {
        waybillService.cancelWaybill(id);
        return success(true);
    }

    @PostMapping("/waybills/{id}/reprint")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-waybill:reprint')")
    public CommonResult<LogisticsWaybillRespVO> reprint(@PathVariable("id") Long id,
                                                        @RequestParam(required = false) Long deviceId) {
        return success(waybillService.reprint(id, deviceId));
    }

    @GetMapping("/print-tasks")
    @PreAuthorize("@ss.hasPermission('trade:logistics:print-task:query')")
    public CommonResult<List<LogisticsPrintTaskRespVO>> getPrintTasks() {
        return success(waybillService.getPrintTasks());
    }

    @GetMapping("/waybills/{id}/trace")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-trace:query')")
    public CommonResult<List<LogisticsTraceRespVO>> getTrace(@PathVariable("id") Long id) {
        return success(waybillService.getTrace(id));
    }

    @PostMapping("/waybills/{id}/trace/sync")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-trace:sync')")
    public CommonResult<List<LogisticsTraceRespVO>> syncTrace(@PathVariable("id") Long id) {
        return success(waybillService.syncTrace(id));
    }
}
