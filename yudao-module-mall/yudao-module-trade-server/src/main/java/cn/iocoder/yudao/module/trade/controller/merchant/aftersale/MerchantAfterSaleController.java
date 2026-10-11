package cn.iocoder.yudao.module.trade.controller.merchant.aftersale;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleDisagreeReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSalePageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleRefuseReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleRespPageItemVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderSubRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.aftersale.vo.MerchantAfterSaleDetailRespVO;
import cn.iocoder.yudao.module.trade.convert.aftersale.AfterSaleConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleLogService;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 售后")
@RestController
@RequestMapping("/trade/merchant/after-sale")
@Validated
public class MerchantAfterSaleController {

    @Resource
    private AfterSaleService afterSaleService;
    @Resource
    private AfterSaleLogService afterSaleLogService;
    @Resource
    private TradeOrderQueryService orderQueryService;
    @Resource
    private TradeOrderSubMapper orderSubMapper;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @GetMapping("/page")
    @Operation(summary = "获得我的售后分页")
    public CommonResult<PageResult<AfterSaleRespPageItemVO>> getPage(@Valid AfterSalePageReqVO reqVO) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        PageResult<AfterSaleDO> page = afterSaleService.getAfterSalePageForMerchant(merchantId, reqVO);
        return success(AfterSaleConvert.INSTANCE.convertPage(page));
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得我的售后详情")
    public CommonResult<MerchantAfterSaleDetailRespVO> getDetail(@RequestParam("id") Long id) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        AfterSaleDO afterSale = requireOwnedAfterSale(id, merchantId);
        TradeOrderItemDO orderItem = orderQueryService.getOrderItem(afterSale.getOrderItemId());
        TradeOrderSubDO subOrder = orderSubMapper.selectByOrderIdAndMerchantId(afterSale.getOrderId(), merchantId);
        MerchantAfterSaleDetailRespVO result = new MerchantAfterSaleDetailRespVO();
        result.setId(afterSale.getId());
        result.setAfterSale(AfterSaleConvert.INSTANCE.convert02(afterSale));
        result.setSubOrder(BeanUtils.toBean(subOrder, TradeOrderSubRespVO.class));
        result.setOrderItem(AfterSaleConvert.INSTANCE.convert02(orderItem));
        result.setLogs(AfterSaleConvert.INSTANCE.convertList1(afterSaleLogService.getAfterSaleLogList(id)));
        return success(result);
    }

    @PutMapping("/agree")
    @Operation(summary = "同意售后申请")
    public CommonResult<Boolean> agree(@RequestParam("id") Long id) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        requireOwnedAfterSale(id, merchantId);
        afterSaleService.agreeAfterSale(merchantSecurityService.requireMerchantAccountId(), id, merchantId);
        return success(true);
    }

    @PutMapping("/disagree")
    @Operation(summary = "拒绝售后申请")
    public CommonResult<Boolean> disagree(@Valid @RequestBody AfterSaleDisagreeReqVO reqVO) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        requireOwnedAfterSale(reqVO.getId(), merchantId);
        afterSaleService.disagreeAfterSale(merchantSecurityService.requireMerchantAccountId(), reqVO, merchantId);
        return success(true);
    }

    @PutMapping("/receive")
    @Operation(summary = "确认收到退货")
    public CommonResult<Boolean> receive(@RequestParam("id") Long id) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        requireOwnedAfterSale(id, merchantId);
        afterSaleService.receiveAfterSale(merchantSecurityService.requireMerchantAccountId(), id, merchantId);
        return success(true);
    }

    @PutMapping("/refuse")
    @Operation(summary = "拒绝退货签收")
    public CommonResult<Boolean> refuse(@Valid @RequestBody AfterSaleRefuseReqVO reqVO) {
        Long merchantId = merchantSecurityService.requireOperatingMerchantId();
        requireOwnedAfterSale(reqVO.getId(), merchantId);
        afterSaleService.refuseAfterSale(merchantSecurityService.requireMerchantAccountId(), reqVO, merchantId);
        return success(true);
    }

    private AfterSaleDO requireOwnedAfterSale(Long id, Long merchantId) {
        AfterSaleDO afterSale = afterSaleService.getAfterSale(id);
        if (afterSale == null) {
            throw new AccessDeniedException("无权访问该售后单");
        }
        TradeOrderItemDO orderItem = orderQueryService.getOrderItem(afterSale.getOrderItemId());
        if (orderItem == null || !merchantId.equals(orderItem.getMerchantId())) {
            throw new AccessDeniedException("无权访问该售后单");
        }
        return afterSale;
    }

}
