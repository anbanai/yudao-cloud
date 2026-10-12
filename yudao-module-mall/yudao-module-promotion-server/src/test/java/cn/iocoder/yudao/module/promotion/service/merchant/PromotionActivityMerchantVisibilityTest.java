package cn.iocoder.yudao.module.promotion.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.promotion.dal.mysql.bargain.BargainActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.combination.CombinationActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillActivityMapper;
import cn.iocoder.yudao.module.promotion.service.bargain.BargainActivityServiceImpl;
import cn.iocoder.yudao.module.promotion.service.combination.CombinationActivityServiceImpl;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillActivityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionActivityMerchantVisibilityTest {

    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private CombinationActivityMapper combinationActivityMapper;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private BargainActivityMapper bargainActivityMapper;

    @InjectMocks
    private CombinationActivityServiceImpl combinationService;
    @InjectMocks
    private SeckillActivityServiceImpl seckillService;
    @InjectMocks
    private BargainActivityServiceImpl bargainService;

    @Test
    void combinationMatch_doesNotQueryActivityForUnavailableSpu() {
        givenUnavailableSpu();

        assertThat(combinationService.getMatchCombinationActivityBySpuId(100L)).isNull();

        verify(combinationActivityMapper, never()).selectBySpuIdAndStatusAndNow(100L, 0);
    }

    @Test
    void seckillMatch_doesNotQueryActivityForUnavailableSpu() {
        givenUnavailableSpu();

        assertThat(seckillService.getMatchSeckillActivityBySpuId(100L)).isNull();

        verify(seckillActivityMapper, never()).selectBySpuIdAndStatusAndNow(100L, 0);
    }

    @Test
    void bargainMatch_doesNotQueryActivityForUnavailableSpu() {
        givenUnavailableSpu();

        assertThat(bargainService.getMatchBargainActivityBySpuId(100L)).isNull();

        verify(bargainActivityMapper, never()).selectBySpuIdAndStatusAndNow(100L, 0);
    }

    private void givenUnavailableSpu() {
        when(productSpuApi.getAvailableSpuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));
    }

}
