package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundRetryReqDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOperationDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOperationMapper;
import cn.iocoder.yudao.module.pay.service.refund.PayRefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayFundOperationsServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private PayFundOperationsService service;
    @Mock private PayFundOrderMapper orderMapper;
    @Mock private PayFundOperationMapper operationMapper;
    @Mock private PayFundService fundService;
    @Mock private PayRefundService refundService;

    @BeforeEach
    void initTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        when(orderMapper.selectById(8L)).thenReturn(new PayFundOrderDO().setId(8L).setDivideStatus(50));
    }

    @AfterEach
    void cleanTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void auditIsWrittenBeforeNetworkAndCommittedFailureDoesNotMisreportRollback() {
        doThrow(new IllegalStateException("synthetic-sensitive-response")).when(fundService).processDivide(8L);
        service.retryDivide(command());
        verify(fundService).prepareDivideRetry(8L);
        verify(fundService, never()).processDivide(any());
        ArgumentCaptor<PayFundOperationDO> captor = ArgumentCaptor.forClass(PayFundOperationDO.class);
        verify(operationMapper).insert(captor.capture());
        assertEquals("DIVIDE_RETRY", captor.getValue().getAction());
        assertEquals("bank-evidence-42", captor.getValue().getExternalEvidence());
        assertEquals(9L, captor.getValue().getOperatorId());
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCommit()));
        verify(fundService).processDivide(8L);
    }

    @Test
    void manualOperationWithoutEvidenceIsRejectedWithoutScheduling() {
        PayFundRetryReqDTO command = command().setExternalEvidence(" ");
        assertThrows(IllegalArgumentException.class, () -> service.retryDivide(command));
        verify(operationMapper, never()).insert(any(PayFundOperationDO.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    private PayFundRetryReqDTO command() {
        return new PayFundRetryReqDTO().setTargetId(8L).setOperatorId(9L)
                .setReason("已核实原请求状态").setExternalEvidence("bank-evidence-42");
    }
}
