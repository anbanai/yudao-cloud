package cn.iocoder.yudao.module.product.api.sku;

import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSkuApiMerchantVisibilityTest {

    @InjectMocks
    private ProductSkuApiImpl api;
    @Mock
    private ProductSkuService skuService;

    @Test
    void getSku_readsOnlyTransactionVisibleSku() {
        ProductSkuDO sku = new ProductSkuDO().setId(100L).setSpuId(200L);
        when(skuService.getAvailableSkuList(anyList())).thenReturn(List.of(sku));

        assertThat(api.getSku(100L).getCheckedData()).extracting(item -> item.getId()).isEqualTo(100L);
        verify(skuService).getAvailableSkuList(List.of(100L));
    }
}
