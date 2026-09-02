package cn.iocoder.yudao.module.product.service.sku;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.yudao.module.product.framework.stock.config.ProductStockProperties;
import cn.iocoder.yudao.module.product.service.property.ProductPropertyService;
import cn.iocoder.yudao.module.product.service.property.ProductPropertyValueService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSkuServiceImplStockSwitchTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSkuServiceImpl skuService;

    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private ProductSkuStockUpdateService productSkuStockUpdateService;
    @Mock
    private ProductStockProperties productStockProperties;
    @Mock
    private ProductPropertyService productPropertyService;
    @Mock
    private ProductPropertyValueService productPropertyValueService;

    @Test
    void testUpdateSkuStock_hotspotEnabledAndSingleSku_usesHotspotPath() {
        ProductSkuUpdateStockReqDTO request = request(1L, -1);
        when(productStockProperties.isHotspotEnabled()).thenReturn(true);

        skuService.updateSkuStock(request);

        verify(productSkuStockUpdateService).updateHotspot(request);
    }

    @Test
    void testUpdateSkuStock_hotspotDisabled_usesLegacyPath() {
        ProductSkuUpdateStockReqDTO request = request(1L, -1);
        when(productStockProperties.isHotspotEnabled()).thenReturn(false);

        skuService.updateSkuStock(request);

        verify(productSkuStockUpdateService).updateLegacy(request);
    }

    @Test
    void testUpdateSkuStock_hotspotEnabledAndMultipleSku_usesLegacyPath() {
        ProductSkuUpdateStockReqDTO request = new ProductSkuUpdateStockReqDTO(List.of(
                new ProductSkuUpdateStockReqDTO.Item().setId(1L).setIncrCount(-1),
                new ProductSkuUpdateStockReqDTO.Item().setId(2L).setIncrCount(-1)));
        when(productStockProperties.isHotspotEnabled()).thenReturn(true);

        skuService.updateSkuStock(request);

        verify(productSkuStockUpdateService).updateLegacy(request);
    }

    private static ProductSkuUpdateStockReqDTO request(Long skuId, int incrCount) {
        return new ProductSkuUpdateStockReqDTO(List.of(new ProductSkuUpdateStockReqDTO.Item()
                .setId(skuId).setIncrCount(incrCount)));
    }

}
