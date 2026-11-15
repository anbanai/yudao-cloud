package cn.iocoder.yudao.module.trade.api.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.api.order.dto.TradeOrderRespDTO;
import cn.iocoder.yudao.module.trade.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 订单")
public interface TradeOrderApi {

    String PREFIX = ApiConstants.PREFIX + "/order";

    @GetMapping(PREFIX + "/list")
    @Operation(summary = "获得订单列表")
    @Parameter(name = "ids", description = "订单编号数组", required = true)
    CommonResult<List<TradeOrderRespDTO>> getOrderList(@RequestParam("ids") Collection<Long> ids);

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "获得订单")
    @Parameter(name = "id", description = "订单编号", required = true)
    CommonResult<TradeOrderRespDTO> getOrder(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/coupon-order-status")
    @Operation(summary = "优惠券补偿使用的订单状态，包含逻辑删除订单；null表示订单不存在")
    CommonResult<Integer> getCouponOrderStatus(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/has-history")
    @Operation(summary = "查询用户是否存在历史订单（包含未支付及已取消订单）")
    @Parameter(name = "userId", description = "用户编号", required = true)
    CommonResult<Boolean> hasOrderHistory(@RequestParam("userId") Long userId);

    @PutMapping(PREFIX + "/cancel-paid")
    @Parameters({
            @Parameter(name = "userId", description = "用户编号", required = true, example = "1024"),
            @Parameter(name = "orderId", description = "订单编号", required = true, example = "2048"),
    })
    CommonResult<Boolean> cancelPaidOrder(@RequestParam("userId") Long userId,
                                          @RequestParam("orderId") Long orderId,
                                          @RequestParam("cancelType") Integer cancelType);

}
