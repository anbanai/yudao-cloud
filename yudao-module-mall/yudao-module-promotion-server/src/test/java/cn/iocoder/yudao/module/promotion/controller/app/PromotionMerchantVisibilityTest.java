package cn.iocoder.yudao.module.promotion.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.promotion.controller.app.bargain.AppBargainActivityController;
import cn.iocoder.yudao.module.promotion.controller.app.combination.AppCombinationActivityController;
import cn.iocoder.yudao.module.promotion.controller.app.seckill.AppSeckillActivityController;
import cn.iocoder.yudao.module.promotion.controller.app.point.AppPointActivityController;
import cn.iocoder.yudao.module.promotion.dal.dataobject.bargain.BargainActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.combination.CombinationActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.point.PointActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.iocoder.yudao.module.promotion.service.bargain.BargainActivityService;
import cn.iocoder.yudao.module.promotion.service.bargain.BargainRecordService;
import cn.iocoder.yudao.module.promotion.service.combination.CombinationActivityService;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillActivityService;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillConfigService;
import cn.iocoder.yudao.module.promotion.service.point.PointActivityService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PromotionMerchantVisibilityTest extends BaseMockitoUnitTest {
    @InjectMocks private AppBargainActivityController bargainController;
    @InjectMocks private AppCombinationActivityController combinationController;
    @InjectMocks private AppSeckillActivityController seckillController;
    @InjectMocks private AppPointActivityController pointController;
    @Mock private ProductSpuApi spuApi;
    @Mock private BargainActivityService bargainActivityService;
    @Mock private BargainRecordService bargainRecordService;
    @Mock private CombinationActivityService combinationActivityService;
    @Mock private SeckillActivityService seckillActivityService;
    @Mock private SeckillConfigService seckillConfigService;
    @Mock private PointActivityService pointActivityService;

    @Test
    void activityDetailsHideNonOperatingMerchantProducts() {
        when(spuApi.getAvailableSpuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));
        when(bargainActivityService.getBargainActivity(1L)).thenReturn(new BargainActivityDO().setId(1L).setSpuId(100L));
        when(combinationActivityService.getCombinationActivity(2L)).thenReturn(new CombinationActivityDO().setId(2L).setSpuId(100L).setStatus(0));
        when(seckillActivityService.getSeckillActivity(3L)).thenReturn(new SeckillActivityDO().setId(3L).setSpuId(100L).setStatus(0));
        assertThat(bargainController.getBargainActivityDetail(1L).getCheckedData()).isNull();
        assertThat(combinationController.getCombinationActivityDetail(2L).getCheckedData()).isNull();
        assertThat(seckillController.getSeckillActivity(3L).getCheckedData()).isNull();
    }

    @Test
    void bargainListsRemoveUnavailableActivitiesInsteadOfReturningIncompleteCards() {
        var activity = new BargainActivityDO().setId(1L).setSpuId(100L);
        when(spuApi.getAvailableSpuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));
        when(bargainActivityService.getBargainActivityListByCount(6)).thenReturn(List.of(activity));
        when(bargainActivityService.getBargainActivityPage(any(PageParam.class))).thenReturn(new PageResult<>(List.of(activity), 1L));
        assertThat(bargainController.getBargainActivityList(6).getCheckedData()).isEmpty();
        assertThat(bargainController.getBargainActivityPage(new PageParam()).getCheckedData().getList()).isEmpty();
    }

    @Test
    void pointActivityDetailsHideUnavailableSpu() {
        when(pointActivityService.getPointActivity(1L)).thenReturn(new PointActivityDO()
                .setId(1L).setSpuId(100L).setStatus(0));
        when(spuApi.getAvailableSpuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));

        assertThat(pointController.getPointActivity(1L).getCheckedData()).isNull();
    }
}
