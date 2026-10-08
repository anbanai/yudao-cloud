package cn.iocoder.yudao.module.trade.enums.aftersale;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReturnShipmentFeePayerEnum {

    MERCHANT("商家承担"),
    BUYER_PAY_ON_DELIVERY("用户到付"),
    BUYER_DEDUCT_REFUND("从退款扣除");

    private final String name;
}
