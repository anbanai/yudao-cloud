package cn.iocoder.yudao.module.promotion.service.discount;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.discount.DiscountProductDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.discount.DiscountActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.discount.DiscountProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountActivityMerchantVisibilityTest {

    @Mock
    private DiscountActivityMapper discountActivityMapper;
    @Mock
    private DiscountProductMapper discountProductMapper;
    @Mock
    private ProductSkuApi productSkuApi;

    @InjectMocks
    private DiscountActivityServiceImpl discountActivityService;

    @Test
    void getMatchDiscountProductListBySkuIds_doesNotMatchUnavailableSku() {
        when(productSkuApi.getAvailableSkuList(List.of(10L)))
                .thenReturn(CommonResult.success(List.of()));

        assertThat(discountActivityService.getMatchDiscountProductListBySkuIds(List.of(10L))).isEmpty();

        verify(discountProductMapper, never()).selectListBySkuIdsAndStatusAndNow(anyCollection(), eq(0));
    }

    @Test
    void getMatchDiscountProductListBySkuIds_queriesOnlyAvailableSkus() {
        ProductSkuRespDTO availableSku = new ProductSkuRespDTO().setId(10L);
        DiscountProductDO expected = new DiscountProductDO().setSkuId(10L);
        when(productSkuApi.getAvailableSkuList(List.of(10L, 20L)))
                .thenReturn(CommonResult.success(List.of(availableSku)));
        when(discountProductMapper.selectListBySkuIdsAndStatusAndNow(anyCollection(), eq(0)))
                .thenReturn(List.of(expected));

        assertThat(discountActivityService.getMatchDiscountProductListBySkuIds(List.of(10L, 20L)))
                .containsExactly(expected);
        verify(discountProductMapper).selectListBySkuIdsAndStatusAndNow(
                org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 1 && ids.contains(10L)), eq(0));
    }

}
