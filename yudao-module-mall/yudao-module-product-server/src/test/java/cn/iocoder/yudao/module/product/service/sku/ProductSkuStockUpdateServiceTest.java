package cn.iocoder.yudao.module.product.service.sku;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuHotspotUpdateService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSkuStockUpdateServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSkuStockUpdateService stockUpdateService;

    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private ProductSkuHotspotUpdateService productSkuHotspotUpdateService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private ProductSpuHotspotUpdateService productSpuHotspotUpdateService;

    @Test
    void testUpdateLegacy_updatesSkuAndSpu() {
        ProductSkuUpdateStockReqDTO request = request(1L, -2);
        when(productSkuMapper.updateStockDecr(1L, -2)).thenReturn(1);
        when(productSkuMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(new ProductSkuDO().setId(1L).setSpuId(10L)));

        stockUpdateService.updateLegacy(request);

        verify(productSkuMapper).updateStockDecr(1L, -2);
        verify(productSkuHotspotUpdateService, never()).updateStock(any(), any());
        verify(productSkuMapper, never()).updateStockDecrHotspot(any(), any());
        verify(productSpuService).updateSpuStock(any());
        verify(productSpuHotspotUpdateService, never()).updateStock(any(), any());
    }

    @Test
    void testUpdateHotspot_usesHotspotSqlAndUpdatesSpu() {
        ProductSkuUpdateStockReqDTO request = request(1L, -2);
        when(productSkuHotspotUpdateService.updateStock(1L, -2)).thenReturn(1);
        when(productSkuMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(new ProductSkuDO().setId(1L).setSpuId(10L)));

        stockUpdateService.updateHotspot(request);

        InOrder order = inOrder(productSkuHotspotUpdateService, productSkuMapper,
                productSpuHotspotUpdateService);
        order.verify(productSkuHotspotUpdateService).updateStock(1L, -2);
        order.verify(productSkuMapper).selectByIds(anyCollection());
        order.verify(productSpuHotspotUpdateService).updateStock(10L, -2);
        verify(productSkuMapper, never()).updateStockDecr(any(), any());
        verify(productSpuService, never()).updateSpuStock(any());
    }

    @Test
    void testUpdateHotspot_stockNotEnough_doesNotUpdateSpu() {
        ProductSkuUpdateStockReqDTO request = request(1L, -2);
        when(productSkuHotspotUpdateService.updateStock(1L, -2)).thenReturn(0);

        assertThrows(ServiceException.class, () -> stockUpdateService.updateHotspot(request));

        verify(productSpuService, never()).updateSpuStock(any());
        verify(productSpuHotspotUpdateService, never()).updateStock(any(), any());
    }

    private static ProductSkuUpdateStockReqDTO request(Long skuId, int incrCount) {
        return new ProductSkuUpdateStockReqDTO(List.of(new ProductSkuUpdateStockReqDTO.Item()
                .setId(skuId).setIncrCount(incrCount)));
    }

}
