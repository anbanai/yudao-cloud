package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsTraceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsTraceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsWaybillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SfRoutePushServiceImplTest {

    @Mock private TradeLogisticsWaybillMapper waybillMapper;
    @Mock private TradeLogisticsTraceMapper traceMapper;
    private SfRoutePushServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SfRoutePushServiceImpl();
        ReflectionTestUtils.setField(service, "waybillMapper", waybillMapper);
        ReflectionTestUtils.setField(service, "traceMapper", traceMapper);
    }

    @Test
    void processPersistsRouteInMatchedTenant() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(11L)
                .setWaybillNo("SF7444400031887")
                .setProviderOrderNo("YD-22-33").setStatus("CREATED");
        waybill.setTenantId(22L);
        when(waybillMapper.selectForRoutePushIgnoreTenant("SF7444400031887", "YD-22-33"))
                .thenReturn(List.of(waybill));
        when(traceMapper.selectByWaybillIdAndProviderEventId(11L, "158918741444476"))
                .thenAnswer(invocation -> {
                    assertThat(TenantContextHolder.getTenantId()).isEqualTo(22L);
                    return null;
                });

        service.process(request(route("158918741444476")));

        ArgumentCaptor<TradeLogisticsTraceDO> traceCaptor = ArgumentCaptor.forClass(TradeLogisticsTraceDO.class);
        verify(traceMapper).insert(traceCaptor.capture());
        TradeLogisticsTraceDO trace = traceCaptor.getValue();
        assertThat(trace.getWaybillId()).isEqualTo(11L);
        assertThat(trace.getProviderEventId()).isEqualTo("158918741444476");
        assertThat(trace.getStatus()).isEqualTo("50");
        assertThat(trace.getContent()).isEqualTo("顺丰速运 已收取快件");
        assertThat(trace.getLocation()).isEqualTo("深圳市");
        assertThat(trace.getOperateTime()).isNotNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void processIgnoresDuplicateProviderEvent() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(11L)
                .setWaybillNo("SF7444400031887")
                .setProviderOrderNo("YD-22-33").setStatus("CREATED");
        waybill.setTenantId(22L);
        when(waybillMapper.selectForRoutePushIgnoreTenant("SF7444400031887", "YD-22-33"))
                .thenReturn(List.of(waybill));
        when(traceMapper.selectByWaybillIdAndProviderEventId(11L, "158918741444476"))
                .thenReturn(new TradeLogisticsTraceDO().setId(99L));

        service.process(request(route("158918741444476")));

        verify(traceMapper, never()).insert(org.mockito.ArgumentMatchers.any(TradeLogisticsTraceDO.class));
    }

    @Test
    void processRejectsUnknownWaybillSoSfCanRetry() {
        when(waybillMapper.selectForRoutePushIgnoreTenant("SF7444400031887", "YD-22-33"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.process(request(route("158918741444476"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("运单");
    }

    private static SfRoutePushReqVO request(SfRoutePushReqVO.Route route) {
        return new SfRoutePushReqVO().setBody(new SfRoutePushReqVO.Body().setWaybillRoute(List.of(route)));
    }

    private static SfRoutePushReqVO.Route route(String id) {
        return new SfRoutePushReqVO.Route().setMailno("SF7444400031887").setOrderid("YD-22-33")
                .setAcceptAddress("深圳市").setAcceptTime("2020-05-11 16:56:54")
                .setRemark("顺丰速运 已收取快件").setOpCode("50").setId(id);
    }
}
