package cn.iocoder.yudao.module.trade.controller.merchant.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderSubRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.order.vo.MerchantOrderDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 订单履约")
@RestController
@RequestMapping("/trade/merchant/order")
@PreAuthorize("denyAll()")
@Validated
public class MerchantTradeOrderController {

    @Resource
    private TradeOrderSubMapper orderSubMapper;
    @Resource
    private TradeOrderItemMapper orderItemMapper;
    @Resource
    private TradeOrderQueryService orderQueryService;
    @Resource
    private LogisticsWaybillService logisticsWaybillService;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @GetMapping("/page")
    @Operation(summary = "获得我的履约订单分页")
    public CommonResult<PageResult<TradeOrderSubRespVO>> getPage(@Valid MerchantOrderPageReqVO reqVO) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        PageResult<TradeOrderSubDO> page = orderSubMapper.selectPageByMerchantId(reqVO, merchantId);
        return success(BeanUtils.toBean(page, TradeOrderSubRespVO.class));
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得我的履约订单详情")
    public CommonResult<MerchantOrderDetailRespVO> getDetail(@RequestParam("subOrderId") Long subOrderId) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        TradeOrderSubDO subOrder = requireOwnedSubOrder(subOrderId, merchantId);
        TradeOrderDO order = orderQueryService.getOrder(subOrder.getOrderId());
        if (order == null) {
            throw new AccessDeniedException("订单不存在");
        }
        List<TradeOrderItemDO> items = orderItemMapper.selectListBySubOrderId(subOrderId);
        MerchantOrderDetailRespVO result = new MerchantOrderDetailRespVO();
        result.setSubOrder(BeanUtils.toBean(subOrder, TradeOrderSubRespVO.class));
        result.setOrderNo(order.getNo());
        result.setOrderCreateTime(order.getCreateTime());
        result.setTerminal(order.getTerminal());
        result.setBuyerRemark(order.getUserRemark());
        result.setReceiver(BeanUtils.toBean(order, MerchantOrderDetailRespVO.Receiver.class));
        result.setItems(BeanUtils.toBean(items, MerchantOrderDetailRespVO.Item.class));
        return success(result);
    }

    @PutMapping("/delivery")
    @Operation(summary = "发货")
    public CommonResult<Boolean> delivery(@Valid @RequestBody TradeOrderDeliveryReqVO reqVO) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        if (reqVO.getSubOrderId() == null) {
            throw new IllegalArgumentException("商家履约订单编号不能为空");
        }
        TradeOrderSubDO subOrder = requireOwnedSubOrder(reqVO.getSubOrderId(), merchantId);
        if (!subOrder.getOrderId().equals(reqVO.getId())) {
            throw new AccessDeniedException("订单与商家履约订单不匹配");
        }
        logisticsWaybillService.deliverManually(reqVO);
        return success(true);
    }

    private TradeOrderSubDO requireOwnedSubOrder(Long subOrderId, Long merchantId) {
        TradeOrderSubDO subOrder = orderSubMapper.selectByIdAndMerchantId(subOrderId, merchantId);
        if (subOrder == null) {
            throw new AccessDeniedException("无权访问该商家履约订单");
        }
        return subOrder;
    }

}
