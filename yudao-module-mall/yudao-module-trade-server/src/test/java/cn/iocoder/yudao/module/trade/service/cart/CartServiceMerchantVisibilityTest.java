package cn.iocoder.yudao.module.trade.service.cart;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.cart.CartDO;
import cn.iocoder.yudao.module.trade.dal.mysql.cart.CartMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceMerchantVisibilityTest {

    @Test
    void getCartList_usesAvailableSpuLookup() {
        CartMapper cartMapper = Mockito.mock(CartMapper.class);
        ProductSpuApi spuApi = Mockito.mock(ProductSpuApi.class);
        ProductSkuApi skuApi = Mockito.mock(ProductSkuApi.class);
        CartServiceImpl service = new CartServiceImpl();
        ReflectionTestUtils.setField(service, "cartMapper", cartMapper);
        ReflectionTestUtils.setField(service, "productSpuApi", spuApi);
        ReflectionTestUtils.setField(service, "productSkuApi", skuApi);

        CartDO cart = new CartDO().setId(1L).setSpuId(10L).setSkuId(20L).setCount(1);
        when(cartMapper.selectListByUserId(1L)).thenReturn(new ArrayList<>(List.of(cart)));
        when(spuApi.getAvailableSpuList(anyCollection()))
                .thenReturn(CommonResult.success(List.of(new ProductSpuRespDTO().setId(10L))));
        when(skuApi.getAvailableSkuList(anyCollection()))
                .thenReturn(CommonResult.success(List.of(new ProductSkuRespDTO().setId(20L).setSpuId(10L))));

        service.getCartList(1L);

        verify(spuApi).getAvailableSpuList(anyCollection());
        Mockito.verify(spuApi, Mockito.never()).getSpuList(anyCollection());
    }
}
