package cn.iocoder.yudao.module.promotion.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.bargain.BargainActivityDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.bargain.BargainActivityMapper;
import cn.iocoder.yudao.module.promotion.service.bargain.BargainActivityServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import({BargainActivityServiceImpl.class, PromotionProductVisibilityService.class})
class PromotionMerchantPaginationTest extends BaseDbUnitTest {
    @Resource private BargainActivityServiceImpl service;
    @Resource private BargainActivityMapper mapper;
    @MockBean private ProductSpuApi spuApi;
    @MockBean private ProductSkuApi skuApi;

    @Test
    void pageCountsOnlyAvailableProductsAndFillsPages() {
        mapper.insert(activity(1L, 100L));
        mapper.insert(activity(2L, 101L));
        mapper.insert(activity(3L, 102L));
        when(spuApi.getAvailableSpuList(any())).thenReturn(CommonResult.success(List.of(
                new ProductSpuRespDTO().setId(100L), new ProductSpuRespDTO().setId(102L))));
        PageParam page = new PageParam().setPageSize(1);
        var first = service.getBargainActivityPage(page);
        assertThat(first.getTotal()).isEqualTo(2);
        assertThat(first.getList()).extracting(BargainActivityDO::getSpuId).containsExactly(102L);
        page.setPageNo(2);
        var second = service.getBargainActivityPage(page);
        assertThat(second.getTotal()).isEqualTo(2);
        assertThat(second.getList()).extracting(BargainActivityDO::getSpuId).containsExactly(100L);
    }

    @Test
    void allUnavailableReturnsEmptyPageAndZeroTotal() {
        mapper.insert(activity(1L, 100L));
        when(spuApi.getAvailableSpuList(any())).thenReturn(CommonResult.success(List.of()));
        var page = service.getBargainActivityPage(new PageParam());
        assertThat(page.getTotal()).isZero();
        assertThat(page.getList()).isEmpty();
    }

    private BargainActivityDO activity(Long id, Long spuId) {
        return new BargainActivityDO().setId(id).setSpuId(spuId).setSkuId(spuId).setName("活动")
                .setStatus(0).setStartTime(LocalDateTime.now().minusDays(1)).setEndTime(LocalDateTime.now().plusDays(1));
    }
}
