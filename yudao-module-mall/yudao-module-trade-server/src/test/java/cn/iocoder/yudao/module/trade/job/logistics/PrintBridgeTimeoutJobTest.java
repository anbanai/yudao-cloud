package cn.iocoder.yudao.module.trade.job.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintBridgeTimeoutJobTest {

    @Test
    void execute_countsOnlyTasksStillAcceptedAtConditionalUpdate() {
        TradeLogisticsPrintTaskMapper mapper = mock(TradeLogisticsPrintTaskMapper.class);
        when(mapper.selectAcceptedExpired(any(LocalDateTime.class)))
                .thenReturn(List.of(new TradeLogisticsPrintTaskDO().setId(1L)));
        when(mapper.markAcceptedExpiredUnknown(eq(1L), any(LocalDateTime.class), anyString())).thenReturn(0);
        PrintBridgeTimeoutJob job = new PrintBridgeTimeoutJob();
        ReflectionTestUtils.setField(job, "taskMapper", mapper);

        assertThat(job.execute("")).contains("0 个");
        verify(mapper).markAcceptedExpiredUnknown(eq(1L), any(LocalDateTime.class), anyString());
    }
}
