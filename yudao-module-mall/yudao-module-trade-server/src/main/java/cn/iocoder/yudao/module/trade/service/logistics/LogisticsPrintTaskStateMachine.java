package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 防止迟到回执回退已完成任务，或让失败任务被无条件自动重打。 */
public final class LogisticsPrintTaskStateMachine {

    private static final Map<LogisticsPrintTaskStatusEnum, Set<LogisticsPrintTaskStatusEnum>> TRANSITIONS = Map.of(
            LogisticsPrintTaskStatusEnum.PENDING, EnumSet.of(LogisticsPrintTaskStatusEnum.DISPATCHED,
                    LogisticsPrintTaskStatusEnum.CANCELLED),
            LogisticsPrintTaskStatusEnum.DISPATCHED, EnumSet.of(LogisticsPrintTaskStatusEnum.PENDING,
                    LogisticsPrintTaskStatusEnum.ACCEPTED, LogisticsPrintTaskStatusEnum.SUCCESS,
                    LogisticsPrintTaskStatusEnum.FAILED,
                    LogisticsPrintTaskStatusEnum.UNKNOWN, LogisticsPrintTaskStatusEnum.CANCELLED),
            LogisticsPrintTaskStatusEnum.ACCEPTED, EnumSet.of(LogisticsPrintTaskStatusEnum.SUCCESS,
                    LogisticsPrintTaskStatusEnum.FAILED, LogisticsPrintTaskStatusEnum.UNKNOWN),
            LogisticsPrintTaskStatusEnum.SUCCESS, EnumSet.of(LogisticsPrintTaskStatusEnum.SUCCESS),
            LogisticsPrintTaskStatusEnum.FAILED, EnumSet.of(LogisticsPrintTaskStatusEnum.FAILED),
            LogisticsPrintTaskStatusEnum.UNKNOWN, EnumSet.of(LogisticsPrintTaskStatusEnum.UNKNOWN,
                    LogisticsPrintTaskStatusEnum.SUCCESS, LogisticsPrintTaskStatusEnum.FAILED),
            LogisticsPrintTaskStatusEnum.CANCELLED, EnumSet.of(LogisticsPrintTaskStatusEnum.CANCELLED));

    private LogisticsPrintTaskStateMachine() {
    }

    public static boolean canTransition(LogisticsPrintTaskStatusEnum from, LogisticsPrintTaskStatusEnum to) {
        return from != null && to != null && (from == to || TRANSITIONS.getOrDefault(from, Set.of()).contains(to));
    }

}
