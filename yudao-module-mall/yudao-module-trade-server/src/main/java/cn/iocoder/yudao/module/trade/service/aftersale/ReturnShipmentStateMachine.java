package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum.*;

public final class ReturnShipmentStateMachine {

    private static final Map<ReturnShipmentStatusEnum, Set<ReturnShipmentStatusEnum>> TRANSITIONS = Map.of(
            // Provider callbacks can arrive out of order. Keep forward jumps valid so a
            // delayed create response cannot make a later, authoritative callback unusable.
            CREATING, EnumSet.of(PICKUP_PENDING, PICKED_UP, IN_TRANSIT, DELIVERED, UNKNOWN,
                    CREATE_FAILED, CANCEL_REQUESTED, EXCEPTION),
            UNKNOWN, EnumSet.of(PICKUP_PENDING, PICKED_UP, IN_TRANSIT, DELIVERED, CREATE_FAILED, EXCEPTION),
            PICKUP_PENDING, EnumSet.of(PICKED_UP, IN_TRANSIT, DELIVERED, CANCEL_REQUESTED, EXCEPTION),
            PICKED_UP, EnumSet.of(IN_TRANSIT, DELIVERED, EXCEPTION),
            IN_TRANSIT, EnumSet.of(DELIVERED, EXCEPTION),
            CANCEL_REQUESTED, EnumSet.of(CANCELLED, UNKNOWN, EXCEPTION),
            CREATE_FAILED, EnumSet.of(CREATING),
            EXCEPTION, EnumSet.of(UNKNOWN, CREATING)
    );

    private ReturnShipmentStateMachine() {
    }

    public static boolean canTransition(ReturnShipmentStatusEnum from, ReturnShipmentStatusEnum to) {
        return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canCancel(ReturnShipmentStatusEnum status) {
        return status == CREATING || status == PICKUP_PENDING;
    }

    public static boolean canUseManualFallback(ReturnShipmentStatusEnum status) {
        return status == null || status == CREATE_FAILED || status == CANCELLED;
    }

    public static boolean canConfirmReceipt(ReturnShipmentStatusEnum status, boolean manualWaybillSubmitted) {
        return manualWaybillSubmitted || status == DELIVERED;
    }
}
