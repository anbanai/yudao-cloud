package cn.iocoder.yudao.module.trade.controller.merchant.logistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 顺丰打单")
@RestController
@RequestMapping("/trade/merchant/logistics/sf/waybill")
@PreAuthorize("isAuthenticated()")
@Validated
public class MerchantSfWaybillController {

    @Resource private TradeOrderSubMapper orderSubMapper;
    @Resource private MerchantSecurityService merchantSecurityService;
    @Resource private LogisticsWaybillService logisticsWaybillService;

    @PostMapping("/create")
    @Operation(summary = "顺丰打单发货")
    public CommonResult<LogisticsWaybillRespVO> create(@RequestBody @Validated CreateRequest request) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        TradeOrderSubDO subOrder = orderSubMapper.selectByIdAndMerchantId(request.getSubOrderId(), merchantId);
        if (subOrder == null) {
            throw new org.springframework.security.access.AccessDeniedException("无权访问该商家履约订单");
        }
        return success(logisticsWaybillService.createWaybill(new LogisticsWaybillCreateReqVO()
                .setOrderId(subOrder.getOrderId()).setSubOrderId(subOrder.getId())));
    }

    @lombok.Data
    public static class CreateRequest {
        @NotNull
        private Long subOrderId;
    }
}
