package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductSpuHotspotUpdateServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSpuHotspotUpdateService hotspotUpdateService;

    @Mock
    private ProductSpuMapper productSpuMapper;

    @Test
    void testUpdateStock_increase_usesHotspotIncreaseSql() {
        when(productSpuMapper.updateStockIncrHotspot(1L, 2)).thenReturn(1);

        assertEquals(1, hotspotUpdateService.updateStock(1L, 2));

        verify(productSpuMapper).updateStockIncrHotspot(1L, 2);
        verify(productSpuMapper, never()).updateStockDecrHotspot(1L, 2);
    }

    @Test
    void testUpdateStock_decrease_usesHotspotDecreaseSql() {
        when(productSpuMapper.updateStockDecrHotspot(1L, 2)).thenReturn(1);

        assertEquals(1, hotspotUpdateService.updateStock(1L, -2));

        verify(productSpuMapper).updateStockDecrHotspot(1L, 2);
        verify(productSpuMapper, never()).updateStockIncrHotspot(1L, 2);
    }

    @Test
    void testUpdateStock_zero_doesNothing() {
        assertEquals(0, hotspotUpdateService.updateStock(1L, 0));

        verifyNoInteractions(productSpuMapper);
    }

}
