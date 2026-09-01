package cn.iocoder.yudao.module.promotion.dal.mysql.coupon;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTemplateValidityTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CouponTemplateMapperTest extends BaseDbUnitTest {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;

    @Test
    void testSelectListByTakeType_excludesExpiredFixedDateTemplate() {
        CouponTemplateDO expiredTemplate = buildTemplate("expired", CouponTemplateValidityTypeEnum.DATE.getType())
                .setValidStartTime(LocalDateTime.now().minusDays(2))
                .setValidEndTime(LocalDateTime.now().minusDays(1));
        couponTemplateMapper.insert(expiredTemplate);
        CouponTemplateDO termTemplate = buildTemplate("term", CouponTemplateValidityTypeEnum.TERM.getType())
                .setFixedStartTerm(0)
                .setFixedEndTerm(7);
        couponTemplateMapper.insert(termTemplate);
        CouponTemplateDO futureTemplate = buildTemplate("future", CouponTemplateValidityTypeEnum.DATE.getType())
                .setValidStartTime(LocalDateTime.now().plusDays(1))
                .setValidEndTime(LocalDateTime.now().plusDays(2));
        couponTemplateMapper.insert(futureTemplate);

        List<CouponTemplateDO> templates = couponTemplateMapper.selectListByTakeType(
                CouponTakeTypeEnum.REGISTER.getType());

        assertEquals(Set.of(termTemplate.getId(), futureTemplate.getId()),
                templates.stream().map(CouponTemplateDO::getId).collect(Collectors.toSet()));
    }

    private CouponTemplateDO buildTemplate(String name, Integer validityType) {
        return new CouponTemplateDO()
                .setName(name)
                .setStatus(ENABLE.getStatus())
                .setTotalCount(CouponTemplateDO.TOTAL_COUNT_MAX)
                .setTakeLimitCount(CouponTemplateDO.TAKE_LIMIT_COUNT_MAX)
                .setTakeType(CouponTakeTypeEnum.REGISTER.getType())
                .setUsePrice(0)
                .setProductScope(1)
                .setValidityType(validityType)
                .setDiscountType(1)
                .setDiscountPrice(1000)
                .setTakeCount(0)
                .setUseCount(0);
    }

}
