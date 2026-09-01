package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

class TradeLogisticsWaybillMapperTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void selectByOrderIdForUpdate_includesCancelledRecordForStableIdempotencyKey() {
        TradeLogisticsWaybillMapper mapper = mock(TradeLogisticsWaybillMapper.class, CALLS_REAL_METHODS);
        TradeLogisticsWaybillDO cancelled = new TradeLogisticsWaybillDO().setId(1L).setOrderId(10L)
                .setStatus("CANCELLED");
        doReturn(cancelled).when(mapper).selectOne(any(Wrapper.class));

        assertThat(mapper.selectByOrderIdForUpdate(10L)).isSameAs(cancelled);

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(wrapper.capture());
        assertThat(wrapper.getValue().getExpression().getNormal()).hasSize(3);
    }
}
