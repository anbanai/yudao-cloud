package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.api.config.MemberConfigApi;
import cn.iocoder.yudao.module.member.api.config.dto.MemberConfigRespDTO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.promotion.enums.common.PromotionTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.filterList;

/**
 * 使用积分的 {@link TradePriceCalculator} 实现类
 *
 * @author owen
 */
@Component
@Order(TradePriceCalculator.ORDER_POINT_USE)
@Slf4j
public class TradePointUsePriceCalculator implements TradePriceCalculator {

    @Resource
    private MemberConfigApi memberConfigApi;
    @Resource
    private MemberUserApi memberUserApi;

    @Override
    public void calculate(TradePriceCalculateReqBO param, TradePriceCalculateRespBO result) {
        result.setMaxUsePoint(0).setMaxPointPrice(0);
        // 判断订单类型是否不为积分商城活动
        if (ObjectUtil.equal(result.getType(), TradeOrderTypeEnum.POINT.getType())) {
            return;
        }
        // 0. 初始化积分
        MemberUserRespDTO user = memberUserApi.getUser(param.getUserId()).getCheckedData();
        result.setTotalPoint(user.getPoint()).setUsePoint(0);

        // 1.1 校验积分抵扣是否开启
        MemberConfigRespDTO config = memberConfigApi.getConfig().getCheckedData();
        if (!isDeductPointEnable(config)) {
            return;
        }
        // 1.3 校验用户积分余额
        if (user.getPoint() == null || user.getPoint() <= 0) {
            return;
        }

        // 2.1 计算本单积分上限，只包含优惠后的商品金额，不抵扣运费。
        int productPayPrice = result.getPrice().getPayPrice()
                - ObjectUtil.defaultIfNull(result.getPrice().getDeliveryPrice(), 0);
        int maxUsePoint = calculateMaxUsePoint(config, user.getPoint(), productPayPrice);
        int maxPointPrice = maxUsePoint * config.getPointTradeDeductUnitPrice();
        result.setMaxUsePoint(maxUsePoint).setMaxPointPrice(maxPointPrice);
        if (!BooleanUtil.isTrue(param.getPointStatus()) || maxUsePoint == 0) {
            return;
        }
        result.setUsePoint(maxUsePoint);

        // 2.2 计算分摊的积分、抵扣金额
        List<TradePriceCalculateRespBO.OrderItem> orderItems = filterList(result.getItems(), TradePriceCalculateRespBO.OrderItem::getSelected);
        List<Integer> divideUsePoints = TradePriceCalculatorHelper.dividePointValue(orderItems, maxUsePoint);
        List<Integer> dividePointPrices = divideUsePoints.stream()
                .map(point -> point * config.getPointTradeDeductUnitPrice()).toList();

        // 3.1 记录优惠明细
        TradePriceCalculatorHelper.addPromotion(result, orderItems,
                param.getUserId(), "积分抵扣", PromotionTypeEnum.POINT.getType(),
                StrUtil.format("积分抵扣：省 {} 元", TradePriceCalculatorHelper.formatPrice(maxPointPrice)),
                dividePointPrices);
        // 3.2 更新 SKU 优惠金额
        for (int i = 0; i < orderItems.size(); i++) {
            TradePriceCalculateRespBO.OrderItem orderItem = orderItems.get(i);
            orderItem.setPointPrice(dividePointPrices.get(i));
            orderItem.setUsePoint(divideUsePoints.get(i));
            TradePriceCalculatorHelper.recountPayPrice(orderItem);
            if (orderItem.getPayPrice() < 0) {
                throw new IllegalStateException("积分抵扣后订单项金额不能小于 0");
            }
        }
        TradePriceCalculatorHelper.recountAllPrice(result);
    }

    private boolean isDeductPointEnable(MemberConfigRespDTO config) {
        return config != null &&
                BooleanUtil.isTrue(config.getPointTradeDeductEnable()) &&  // 积分功能是否启用
                config.getPointTradeDeductUnitPrice() != null && config.getPointTradeDeductUnitPrice() > 0; // 有没有配置：1 积分抵扣多少分
    }

    private int calculateMaxUsePoint(MemberConfigRespDTO config, int userPoint, int productPayPrice) {
        // 每个订单最多可以使用的积分数量
        if (config.getPointTradeDeductMaxPrice() != null && config.getPointTradeDeductMaxPrice() > 0) {
            userPoint = Math.min(userPoint, config.getPointTradeDeductMaxPrice());
        }
        // 普通订单至少保留 0.01 元。先限制积分数量，再计算抵扣金额，避免乘法溢出。
        int maxPointByPayPrice = Math.max((productPayPrice - 1)
                / config.getPointTradeDeductUnitPrice(), 0);
        return Math.min(userPoint, maxPointByPayPrice);
    }

}
