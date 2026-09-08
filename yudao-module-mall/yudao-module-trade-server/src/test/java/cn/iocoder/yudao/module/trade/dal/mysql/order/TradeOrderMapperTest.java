package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeOrderMapperTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TradeOrderDO.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void selectListUndeliveredExpress_excludesRefundingOrders() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any(Wrapper.class));

        mapper.selectListUndeliveredExpress();

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
                .contains("status", "delivery_type", "refund_status");
    }
}
