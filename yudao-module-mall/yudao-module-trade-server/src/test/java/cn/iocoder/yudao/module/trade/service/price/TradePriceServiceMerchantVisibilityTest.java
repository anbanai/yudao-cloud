package cn.iocoder.yudao.module.trade.service.price;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.calculator.TradeDiscountActivityPriceCalculator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SKU_NOT_EXISTS;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradePriceServiceMerchantVisibilityTest {

    @Test
    void calculateOrderPrice_usesTenantScopedSkuLookupAndRejectsInvisibleSku() {
        ProductSkuApi skuApi = Mockito.mock(ProductSkuApi.class);
        ProductSpuApi spuApi = Mockito.mock(ProductSpuApi.class);
        TradePriceServiceImpl service = new TradePriceServiceImpl();
        ReflectionTestUtils.setField(service, "productSkuApi", skuApi);
        ReflectionTestUtils.setField(service, "productSpuApi", spuApi);
        ReflectionTestUtils.setField(service, "priceCalculators", List.of());
        ReflectionTestUtils.setField(service, "discountActivityPriceCalculator",
                Mockito.mock(TradeDiscountActivityPriceCalculator.class));

        when(skuApi.getAvailableSkuList(anyCollection())).thenReturn(CommonResult.success(List.of()));
        TradePriceCalculateReqBO.Item item = new TradePriceCalculateReqBO.Item();
        item.setSkuId(100L);
        item.setCount(1);
        item.setSelected(true);
        TradePriceCalculateReqBO req = new TradePriceCalculateReqBO();
        req.setItems(List.of(item));
        req.setPointStatus(false);

        assertServiceException(() -> service.calculateOrderPrice(req), SKU_NOT_EXISTS);
        verify(skuApi).getAvailableSkuList(anyCollection());
        verify(skuApi, never()).getSkuList(anyCollection());
        verifyNoSpuLookup(spuApi);
    }

    private static void verifyNoSpuLookup(ProductSpuApi spuApi) {
        verify(spuApi, never()).validateSpuList(anyCollection());
    }
}
