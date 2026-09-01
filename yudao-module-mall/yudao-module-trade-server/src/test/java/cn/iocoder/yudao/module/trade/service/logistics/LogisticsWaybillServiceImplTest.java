package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.*;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfApiException;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfLogisticsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogisticsWaybillServiceImplTest {

    @Test
    void createWaybill_doesNotKeepSfSideEffectInsideLocalTransaction() throws Exception {
        Transactional transactional = LogisticsWaybillServiceImpl.class
                .getMethod("createWaybill", LogisticsWaybillCreateReqVO.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeOrderItemMapper itemMapper;
    @Mock private TradeLogisticsAccountMapper accountMapper;
    @Mock private TradeLogisticsWaybillMapper waybillMapper;
    @Mock private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Mock private TradeLogisticsPrintTaskMapper taskMapper;
    @Mock private TradeLogisticsTraceMapper traceMapper;
    @Mock private SfLogisticsClient sfClient;
    @Mock private LogisticsLabelNormalizer labelNormalizer;
    @Mock private FileApi fileApi;
    @Mock private ProductSkuApi productSkuApi;
    private LogisticsWaybillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogisticsWaybillServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "waybillMapper", waybillMapper);
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "traceMapper", traceMapper);
        ReflectionTestUtils.setField(service, "sfClient", sfClient);
        ReflectionTestUtils.setField(service, "labelNormalizer", labelNormalizer);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "productSkuApi", productSkuApi);
    }

    @Test
    void createWaybill_timeoutPersistsUnknownAndDoesNotCreatePrintTask() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsAccountDO account = account();
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO().setId(2L).setStatus(0);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(accountMapper.selectDefaultEnabled()).thenReturn(account);
        when(deviceMapper.selectDefaultEnabled()).thenReturn(device);
        when(waybillMapper.selectByOrderIdForUpdate(10L)).thenReturn(null);
        doAnswer(invocation -> {
            ((TradeLogisticsWaybillDO) invocation.getArgument(0)).setId(3L);
            return 1;
        }).when(waybillMapper).insert(any(TradeLogisticsWaybillDO.class));
        when(sfClient.createWaybill(eq(account), anyString(), eq(order), anyList(), anyMap()))
                .thenThrow(new SfApiException("TIMEOUT", "timeout", true, null));

        var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L));

        assertThat(result.getStatus()).isEqualTo(LogisticsWaybillStatusEnum.UNKNOWN.name());
        assertThat(result.getErrorCode()).isEqualTo("TIMEOUT");
        verify(taskMapper, never()).insert(any(TradeLogisticsPrintTaskDO.class));
        verify(sfClient, never()).getLabel(any(), anyString());
    }

    @Test
    void createWaybill_refundingOrderIsRejectedBeforeCallingSf() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.PART.getStatus());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);

        assertThatThrownBy(() -> service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L)))
                .isInstanceOf(cn.iocoder.yudao.framework.common.exception.ServiceException.class)
                .extracting("code")
                .isEqualTo(cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants
                        .ORDER_DELIVERY_FAIL_REFUND_STATUS_NOT_NONE.getCode());
        verifyNoInteractions(sfClient);
    }

    @Test
    void createWaybill_existingTaskReturnsSameJobWithoutCallingSfAgain() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(10L)
                .setOrderNo("T10").setStatus(LogisticsWaybillStatusEnum.CREATED.name());
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setJobId("JOB-1")
                .setStatus(LogisticsPrintTaskStatusEnum.PENDING.name());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(accountMapper.selectDefaultEnabled()).thenReturn(account());
        when(deviceMapper.selectDefaultEnabled()).thenReturn(new TradeLogisticsPrintDeviceDO().setId(2L).setStatus(0));
        when(waybillMapper.selectByOrderIdForUpdate(10L)).thenReturn(waybill);
        when(taskMapper.selectLatestByWaybillId(3L)).thenReturn(task);

        var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L));

        assertThat(result.getJobId()).isEqualTo("JOB-1");
        verifyNoInteractions(sfClient);
    }

    @Test
    void validateManualDelivery_failedWaybillDoesNotBlockFallback() {
        when(waybillMapper.selectActiveByOrderId(10L)).thenReturn(new TradeLogisticsWaybillDO()
                .setStatus(LogisticsWaybillStatusEnum.FAILED.name()));

        assertThatCode(() -> service.validateManualDelivery(10L, 8L, "SF-MANUAL"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateManualDelivery_unknownWaybillStillBlocksFallback() {
        when(waybillMapper.selectActiveByOrderId(10L)).thenReturn(new TradeLogisticsWaybillDO()
                .setStatus(LogisticsWaybillStatusEnum.UNKNOWN.name()));

        assertThatThrownBy(() -> service.validateManualDelivery(10L, 8L, "SF-MANUAL"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateManualDelivery_locksOrderBeforeCheckingActiveWaybill() {
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(new TradeOrderDO().setId(10L));
        when(waybillMapper.selectActiveByOrderId(10L)).thenReturn(null);

        service.validateManualDelivery(10L, 8L, "SF-MANUAL");

        var inOrder = inOrder(orderMapper, waybillMapper);
        inOrder.verify(orderMapper).selectByIdForUpdate(10L);
        inOrder.verify(waybillMapper).selectActiveByOrderId(10L);
    }

    @Test
    void cancelWaybill_knownFailureKeepsTaskCancelledForManualReconciliation() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setAccountId(1L)
                .setProviderOrderNo("YD-9-10").setStatus(LogisticsWaybillStatusEnum.CREATED.name())
                .setDeliveryStatus("PENDING");
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setId(4L)
                .setStatus(LogisticsPrintTaskStatusEnum.PENDING.name());
        when(waybillMapper.selectByIdForUpdate(3L)).thenReturn(waybill);
        when(taskMapper.selectLatestByWaybillId(3L)).thenReturn(task);
        when(accountMapper.selectById(1L)).thenReturn(account());
        doThrow(new SfApiException("CANCEL_REJECTED", "cancel rejected"))
                .when(sfClient).cancelWaybill(any(), eq("YD-9-10"));

        assertThatThrownBy(() -> service.cancelWaybill(3L)).isInstanceOf(SfApiException.class);

        assertThat(waybill.getStatus()).isEqualTo(LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name());
        assertThat(task.getStatus()).isEqualTo(LogisticsPrintTaskStatusEnum.CANCELLED.name());
    }

    private TradeLogisticsAccountDO account() {
        return new TradeLogisticsAccountDO().setId(1L).setLogisticsId(8L).setStatus(0)
                .setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203);
    }
}
