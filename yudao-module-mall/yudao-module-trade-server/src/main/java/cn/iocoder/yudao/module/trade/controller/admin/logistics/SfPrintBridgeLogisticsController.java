package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.*;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsManagementService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    @Resource private TradeOrderSubMapper orderSubMapper;

    @GetMapping("/accounts")
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-account:query')")
    public CommonResult<List<SfLogisticsAccountRespVO>> getAccounts() {
        return success(managementService.getAccounts());
    }

    @PostMapping("/accounts")
    @ApiAccessLog(requestEnable = false)
    @PreAuthorize("@ss.hasPermission('trade:logistics:sf-account:update')")
    public CommonResult<Long> saveAccount(@Valid @RequestBody SfLogisticsAccountSaveReqVO request) {
        return success(managementService.saveAccount(request));
    }

    @GetMapping("/devices")
    @PreAuthorize("@ss.hasPermission('trade:logistics:device:query')")
    public CommonResult<List<LogisticsPrintDeviceRespVO>> getDevices() {
        return success(managementService.getDevices());
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
            List<TradeOrderSubDO> subOrders = orderSubMapper.selectListByOrderId(orderId);
            if (subOrders.isEmpty()) {
                result.add(new LogisticsWaybillRespVO().setOrderId(orderId).setStatus("FAILED")
                        .setErrorCode("SUB_ORDER_NOT_EXISTS").setErrorMessage("订单没有可发货的商家子订单"));
                continue;
            }
            List<TradeOrderSubDO> undeliveredSubOrders = subOrders.stream()
                    .filter(item -> TradeOrderStatusEnum.isUndelivered(item.getStatus())).toList();
            if (undeliveredSubOrders.isEmpty()) {
                result.add(new LogisticsWaybillRespVO().setOrderId(orderId).setStatus("FAILED")
                        .setErrorCode("NO_UNDELIVERED_SUB_ORDER").setErrorMessage("订单没有待发货的商家履约单"));
                continue;
            }
            for (TradeOrderSubDO subOrder : undeliveredSubOrders) {
                try {
                    result.add(waybillService.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(orderId)
                            .setSubOrderId(subOrder.getId()).setAccountId(request.getAccountId())));
                } catch (ServiceException exception) {
                    result.add(new LogisticsWaybillRespVO().setOrderId(orderId).setSubOrderId(subOrder.getId())
                            .setMerchantId(subOrder.getMerchantId()).setMerchantName(subOrder.getMerchantName())
                            .setStatus("FAILED")
                            .setErrorCode(String.valueOf(exception.getCode())).setErrorMessage(exception.getMessage()));
                }
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
