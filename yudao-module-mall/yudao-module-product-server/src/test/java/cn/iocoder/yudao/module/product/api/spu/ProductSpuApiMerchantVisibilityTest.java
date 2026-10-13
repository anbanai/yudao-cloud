package cn.iocoder.yudao.module.product.api.spu;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ProductSpuApiMerchantVisibilityTest extends BaseMockitoUnitTest {
    @InjectMocks
    private ProductSpuApiImpl api;
    @Mock
    private ProductSpuService spuService;
    @Mock
    private ProductMerchantService merchantService;

    @Test
    void internalLookupRetainsFrozenMerchantProductWhileCustomerLookupFiltersIt() {
        ProductSpuDO spu = new ProductSpuDO().setId(100L).setMerchantId(200L).setName("历史订单商品");
        when(spuService.getSpuList(List.of(100L))).thenReturn(List.of(spu));
        when(spuService.getAvailableSpuList(List.of(100L))).thenReturn(List.of());
        assertThat(api.getSpuList(List.of(100L)).getCheckedData()).singleElement()
                .satisfies(item -> assertThat(item.getId()).isEqualTo(100L));
        assertThat(api.getAvailableSpuList(List.of(100L)).getCheckedData()).isEmpty();
    }

    @Test
    void internalLookupUsesPlatformMerchantNameAcrossTenantScope() {
        ProductSpuDO spu = new ProductSpuDO().setId(101L).setMerchantId(201L).setName("平台商品");
        when(spuService.getSpuList(List.of(101L))).thenReturn(List.of(spu));
        when(merchantService.getMerchantForTrade(201L))
                .thenReturn(new ProductMerchantDO().setId(201L).setStoreName("平台自营"));

        assertThat(api.getSpuList(List.of(101L)).getCheckedData()).singleElement()
                .satisfies(item -> assertThat(item.getMerchantName()).isEqualTo("平台自营"));
    }
}
