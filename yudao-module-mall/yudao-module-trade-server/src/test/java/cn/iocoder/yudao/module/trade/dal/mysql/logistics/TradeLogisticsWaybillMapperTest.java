package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

class TradeLogisticsWaybillMapperTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TradeLogisticsWaybillDO.class);
    }

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

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void selectListWithLabelByOrderIdsAndStatuses_usesActiveOrderIndex() {
        TradeLogisticsWaybillMapper mapper = mock(TradeLogisticsWaybillMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectListWithLabelByOrderIdsAndStatuses(List.of(10L), List.of("CREATED"));

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("active_order_id");
    }
}
