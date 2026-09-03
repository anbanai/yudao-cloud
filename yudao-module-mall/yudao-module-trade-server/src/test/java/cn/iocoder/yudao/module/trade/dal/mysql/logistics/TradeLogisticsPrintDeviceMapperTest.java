package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeLogisticsPrintDeviceMapperTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TradeLogisticsPrintDeviceDO.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void bindPendingDevice_matchesEnrollmentRecordInsteadOfAdoptedDeviceCode() {
        TradeLogisticsPrintDeviceMapper mapper = mock(TradeLogisticsPrintDeviceMapper.class, CALLS_REAL_METHODS);
        doReturn(1).when(mapper).update(any(), any(Wrapper.class));

        mapper.bindPendingDevice(9L, "existing-printbridge-id", "仓库电脑");

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(any(), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).doesNotContain("device_code");
    }
}
