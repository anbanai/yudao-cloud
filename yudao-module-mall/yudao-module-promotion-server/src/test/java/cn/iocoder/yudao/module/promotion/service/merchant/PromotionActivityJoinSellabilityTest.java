package cn.iocoder.yudao.module.promotion.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.promotion.dal.dataobject.bargain.BargainActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.point.PointActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.point.PointProductDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillConfigDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillProductDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.bargain.BargainActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.point.PointActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.point.PointProductMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillProductMapper;
import cn.iocoder.yudao.module.promotion.service.bargain.BargainActivityServiceImpl;
import cn.iocoder.yudao.module.promotion.service.point.PointActivityServiceImpl;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillActivityServiceImpl;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionActivityJoinSellabilityTest {

    @Mock
    private ProductSkuApi productSkuApi;
    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private PointActivityMapper pointActivityMapper;
    @Mock
    private PointProductMapper pointProductMapper;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private SeckillProductMapper seckillProductMapper;
    @Mock
    private SeckillConfigService seckillConfigService;
    @Mock
    private BargainActivityMapper bargainActivityMapper;

    @InjectMocks
    private PointActivityServiceImpl pointActivityService;
    @InjectMocks
    private SeckillActivityServiceImpl seckillActivityService;
    @InjectMocks
    private BargainActivityServiceImpl bargainActivityService;

    @Test
    void validateJoinPointActivity_rejectsUnavailableSku() {
        when(pointActivityMapper.selectById(1L)).thenReturn(new PointActivityDO()
                .setId(1L).setSpuId(10L).setStatus(0));
        when(pointProductMapper.selectListByActivityIdAndSkuId(1L, 100L)).thenReturn(new PointProductDO()
                .setActivityId(1L).setSpuId(10L).setSkuId(100L).setCount(1).setStock(1));
        when(productSkuApi.getAvailableSkuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));

        assertThatThrownBy(() -> pointActivityService.validateJoinPointActivity(1L, 100L, 1))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void validateJoinSeckill_rejectsUnavailableSku() {
        LocalDateTime now = LocalDateTime.now();
        when(seckillActivityMapper.selectById(1L)).thenReturn(new SeckillActivityDO()
                .setId(1L).setSpuId(10L).setStatus(0)
                .setStartTime(now.minusMinutes(1)).setEndTime(now.plusMinutes(1))
                .setConfigIds(List.of(20L)).setSingleLimitCount(1));
        when(seckillConfigService.getCurrentSeckillConfig()).thenReturn(new SeckillConfigDO()
                .setId(20L).setStartTime("00:00:00").setEndTime("23:59:59"));
        when(seckillProductMapper.selectByActivityIdAndSkuId(1L, 100L)).thenReturn(new SeckillProductDO()
                .setActivityId(1L).setSpuId(10L).setSkuId(100L).setStock(1));
        when(productSkuApi.getAvailableSkuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));

        assertThatThrownBy(() -> seckillActivityService.validateJoinSeckill(1L, 100L, 1))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void validateBargainActivityCanJoin_rejectsUnavailableSpu() {
        when(bargainActivityMapper.selectById(1L)).thenReturn(new BargainActivityDO()
                .setId(1L).setSpuId(10L).setSkuId(100L).setStatus(0)
                .setStock(1).setStartTime(LocalDateTime.now().minusMinutes(1))
                .setEndTime(LocalDateTime.now().plusMinutes(1)));
        when(productSpuApi.getAvailableSpuList(List.of(10L))).thenReturn(CommonResult.success(List.of()));

        assertThatThrownBy(() -> bargainActivityService.validateBargainActivityCanJoin(1L))
                .isInstanceOf(ServiceException.class);
    }

}
