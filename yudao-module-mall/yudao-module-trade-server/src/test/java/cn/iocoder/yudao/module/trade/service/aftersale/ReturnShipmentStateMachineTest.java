package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnShipmentStateMachineTest {

    @Test
    void shouldAllowOnlyForwardProviderTransitions() {
        assertTrue(ReturnShipmentStateMachine.canTransition(ReturnShipmentStatusEnum.CREATING,
                ReturnShipmentStatusEnum.PICKUP_PENDING));
        assertTrue(ReturnShipmentStateMachine.canTransition(ReturnShipmentStatusEnum.UNKNOWN,
                ReturnShipmentStatusEnum.PICKUP_PENDING));
        assertTrue(ReturnShipmentStateMachine.canTransition(ReturnShipmentStatusEnum.PICKED_UP,
                ReturnShipmentStatusEnum.IN_TRANSIT));
        assertTrue(ReturnShipmentStateMachine.canTransition(ReturnShipmentStatusEnum.IN_TRANSIT,
                ReturnShipmentStatusEnum.DELIVERED));
        assertFalse(ReturnShipmentStateMachine.canTransition(ReturnShipmentStatusEnum.DELIVERED,
                ReturnShipmentStatusEnum.IN_TRANSIT));
    }

    @Test
    void shouldAllowCancellationOnlyBeforePickup() {
        assertTrue(ReturnShipmentStateMachine.canCancel(ReturnShipmentStatusEnum.CREATING));
        assertTrue(ReturnShipmentStateMachine.canCancel(ReturnShipmentStatusEnum.PICKUP_PENDING));
        assertFalse(ReturnShipmentStateMachine.canCancel(ReturnShipmentStatusEnum.PICKED_UP));
        assertFalse(ReturnShipmentStateMachine.canCancel(ReturnShipmentStatusEnum.DELIVERED));
    }

    @Test
    void shouldRequireDeliveredOrManualWaybillBeforeReceipt() {
        assertTrue(ReturnShipmentStateMachine.canConfirmReceipt(ReturnShipmentStatusEnum.DELIVERED, false));
        assertTrue(ReturnShipmentStateMachine.canConfirmReceipt(null, true));
        assertFalse(ReturnShipmentStateMachine.canConfirmReceipt(ReturnShipmentStatusEnum.PICKUP_PENDING, false));
    }

    @Test
    void shouldOnlyAllowManualFallbackAfterProviderFailure() {
        assertTrue(ReturnShipmentStateMachine.canUseManualFallback(null));
        assertTrue(ReturnShipmentStateMachine.canUseManualFallback(ReturnShipmentStatusEnum.CREATE_FAILED));
        assertFalse(ReturnShipmentStateMachine.canUseManualFallback(ReturnShipmentStatusEnum.UNKNOWN));
        assertFalse(ReturnShipmentStateMachine.canUseManualFallback(ReturnShipmentStatusEnum.PICKUP_PENDING));
    }
}
