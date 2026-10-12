package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class ProductSpuHotspotUpdateServiceTest extends BaseMockitoUnitTest {

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(0L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @InjectMocks
    private ProductSpuHotspotUpdateService hotspotUpdateService;

    @Mock
    private ProductSpuMapper productSpuMapper;

    @Test
    void testUpdateStock_increase_usesHotspotIncreaseSql() {
        when(productSpuMapper.updateStockIncrHotspot(eq(1L), eq(2), anyLong())).thenReturn(1);

        assertEquals(1, hotspotUpdateService.updateStock(1L, 2));

        verify(productSpuMapper).updateStockIncrHotspot(eq(1L), eq(2), anyLong());
        verify(productSpuMapper, never()).updateStockDecrHotspot(eq(1L), eq(2), anyLong());
    }

    @Test
    void testUpdateStock_decrease_usesHotspotDecreaseSql() {
        when(productSpuMapper.updateStockDecrHotspot(eq(1L), eq(2), anyLong())).thenReturn(1);

        assertEquals(1, hotspotUpdateService.updateStock(1L, -2));

        verify(productSpuMapper).updateStockDecrHotspot(eq(1L), eq(2), anyLong());
        verify(productSpuMapper, never()).updateStockIncrHotspot(eq(1L), eq(2), anyLong());
    }

    @Test
    void testUpdateStock_zero_doesNothing() {
        assertEquals(0, hotspotUpdateService.updateStock(1L, 0));

        verifyNoInteractions(productSpuMapper);
    }

}
