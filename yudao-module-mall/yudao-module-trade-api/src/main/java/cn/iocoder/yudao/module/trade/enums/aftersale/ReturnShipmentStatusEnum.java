package cn.iocoder.yudao.module.trade.enums.aftersale;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReturnShipmentStatusEnum {

    CREATING("创建中"),
    UNKNOWN("状态未知"),
    PICKUP_PENDING("待取件"),
    PICKED_UP("已揽收"),
    IN_TRANSIT("运输中"),
    DELIVERED("已签收"),
    CREATE_FAILED("创建失败"),
    CANCEL_REQUESTED("取消中"),
    CANCELLED("已取消"),
    EXCEPTION("异常");

    private final String name;
}
