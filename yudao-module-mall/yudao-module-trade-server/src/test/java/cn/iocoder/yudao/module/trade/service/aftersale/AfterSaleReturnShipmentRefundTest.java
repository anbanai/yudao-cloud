package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleReturnShipmentMapper;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentFeePayerEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AfterSaleReturnShipmentRefundTest {

    @Mock
    private AfterSaleMapper afterSaleMapper;
    @Mock
    private AfterSaleReturnShipmentMapper shipmentMapper;

    @Test
    void applyRefundDeduction_deductsActualFeeOnlyOnce() {
        AfterSaleReturnShipmentServiceImpl service = new AfterSaleReturnShipmentServiceImpl();
        ReflectionTestUtils.setField(service, "afterSaleMapper", afterSaleMapper);
        ReflectionTestUtils.setField(service, "shipmentMapper", shipmentMapper);

        AfterSaleDO afterSale = new AfterSaleDO().setId(10L).setRefundPrice(1_000);
        AfterSaleReturnShipmentDO shipment = new AfterSaleReturnShipmentDO().setId(20L)
                .setAfterSaleId(10L).setFeePayer(ReturnShipmentFeePayerEnum.BUYER_DEDUCT_REFUND.name())
                .setActualFee(120);
        when(shipmentMapper.selectByAfterSaleId(10L)).thenReturn(shipment);
        when(shipmentMapper.selectByIdForUpdate(20L)).thenReturn(shipment);

        service.applyRefundDeduction(afterSale);

        assertThat(afterSale.getRefundPrice()).isEqualTo(880);
        assertThat(shipment.getRefundDeductedFee()).isEqualTo(120);
        verify(shipmentMapper).updateById((AfterSaleReturnShipmentDO) same(shipment));
        verify(afterSaleMapper).updateById((AfterSaleDO) argThat((AfterSaleDO value) -> value.getId().equals(10L)
                && value.getRefundPrice().equals(880)));

        clearInvocations(shipmentMapper, afterSaleMapper);
        service.applyRefundDeduction(afterSale);
        verify(shipmentMapper, never()).updateById(any(AfterSaleReturnShipmentDO.class));
        verify(afterSaleMapper, never()).updateById(any(AfterSaleDO.class));
    }
}
