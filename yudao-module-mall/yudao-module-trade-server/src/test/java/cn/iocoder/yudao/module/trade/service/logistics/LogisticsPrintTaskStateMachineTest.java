package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsPrintTaskStateMachineTest {

    @Test
    void canTransition_acceptsPrintBridgeCallbackFlow() {
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.PENDING, LogisticsPrintTaskStatusEnum.DISPATCHED)).isTrue();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.DISPATCHED, LogisticsPrintTaskStatusEnum.ACCEPTED)).isTrue();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.DISPATCHED, LogisticsPrintTaskStatusEnum.SUCCESS)).isTrue();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.ACCEPTED, LogisticsPrintTaskStatusEnum.SUCCESS)).isTrue();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.ACCEPTED, LogisticsPrintTaskStatusEnum.FAILED)).isTrue();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.ACCEPTED, LogisticsPrintTaskStatusEnum.UNKNOWN)).isTrue();
    }

    @Test
    void canTransition_rejectsAutomaticReprintAndTerminalRegression() {
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.FAILED, LogisticsPrintTaskStatusEnum.PENDING)).isFalse();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.UNKNOWN, LogisticsPrintTaskStatusEnum.PENDING)).isFalse();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.SUCCESS, LogisticsPrintTaskStatusEnum.FAILED)).isFalse();
        assertThat(LogisticsPrintTaskStateMachine.canTransition(
                LogisticsPrintTaskStatusEnum.CANCELLED, LogisticsPrintTaskStatusEnum.PENDING)).isFalse();
    }

}
