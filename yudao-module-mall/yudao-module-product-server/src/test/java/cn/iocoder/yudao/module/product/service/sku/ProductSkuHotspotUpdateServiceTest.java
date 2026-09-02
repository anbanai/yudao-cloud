package cn.iocoder.yudao.module.product.service.sku;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductSkuHotspotUpdateServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSkuHotspotUpdateService hotspotUpdateService;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Test
    void testUpdateStock_increase_usesHotspotIncreaseSql() {
        when(productSkuMapper.updateStockIncrHotspot(1L, 2)).thenReturn(1);

        assertEquals(1, hotspotUpdateService.updateStock(1L, 2));

        verify(productSkuMapper).updateStockIncrHotspot(1L, 2);
        verify(productSkuMapper, never()).updateStockDecrHotspot(1L, 2);
    }

    @Test
    void testUpdateStock_decrease_usesHotspotDecreaseSql() {
        when(productSkuMapper.updateStockDecrHotspot(1L, 2)).thenReturn(1);

        assertEquals(1, hotspotUpdateService.updateStock(1L, -2));

        verify(productSkuMapper).updateStockDecrHotspot(1L, 2);
        verify(productSkuMapper, never()).updateStockIncrHotspot(1L, 2);
    }

    @Test
    void testUpdateStock_zero_doesNothing() {
        assertEquals(0, hotspotUpdateService.updateStock(1L, 0));

        verifyNoInteractions(productSkuMapper);
    }

}
