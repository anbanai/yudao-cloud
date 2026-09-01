package cn.iocoder.yudao.module.promotion.service.coupon;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.product.api.category.ProductCategoryApi;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTemplateValidityTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({CouponServiceImpl.class, CouponTemplateServiceImpl.class})
class CouponServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CouponServiceImpl couponService;
    @Resource
    private CouponMapper couponMapper;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private MemberUserApi memberUserApi;
    @MockitoBean
    private ProductCategoryApi productCategoryApi;
    @MockitoBean
    private ProductSpuApi productSpuApi;

    @Test
    void testTakeCouponByRegister_rollsBackOnlyFailedTemplate() {
        CouponTemplateDO firstTemplate = insertTermTemplate("first", 0, 7);
        CouponTemplateDO exhaustedTemplate = insertTermTemplate("exhausted", 0, 7)
                .setTotalCount(0);
        couponTemplateMapper.updateById(exhaustedTemplate);
        CouponTemplateDO lastTemplate = insertTermTemplate("last", 0, 30);
        Long userId = 330L;

        assertDoesNotThrow(() -> couponService.takeCouponByRegister(userId));

        List<CouponDO> coupons = couponMapper.selectList();
        assertEquals(2, coupons.size());
        assertEquals(Set.of(firstTemplate.getId(), lastTemplate.getId()),
                coupons.stream().map(CouponDO::getTemplateId).collect(Collectors.toSet()));
        assertEquals(0, couponTemplateMapper.selectById(exhaustedTemplate.getId()).getTakeCount());
    }

    @Test
    void testTakeCouponByRegister_isolatesTemplateFailureFromOuterTransaction() {
        CouponTemplateDO firstTemplate = insertTermTemplate("first", 0, 7);
        CouponTemplateDO exhaustedTemplate = insertTermTemplate("exhausted", 0, 7)
                .setTotalCount(0);
        couponTemplateMapper.updateById(exhaustedTemplate);
        CouponTemplateDO lastTemplate = insertTermTemplate("last", 0, 30);
        Long userId = 330L;

        assertDoesNotThrow(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> couponService.takeCouponByRegister(userId)));

        List<CouponDO> coupons = couponMapper.selectList();
        assertEquals(2, coupons.size());
        assertEquals(Set.of(firstTemplate.getId(), lastTemplate.getId()),
                coupons.stream().map(CouponDO::getTemplateId).collect(Collectors.toSet()));
    }

    private CouponTemplateDO insertTermTemplate(String name, Integer fixedStartTerm, Integer fixedEndTerm) {
        CouponTemplateDO template = new CouponTemplateDO()
                .setName(name)
                .setStatus(ENABLE.getStatus())
                .setTotalCount(CouponTemplateDO.TOTAL_COUNT_MAX)
                .setTakeLimitCount(CouponTemplateDO.TAKE_LIMIT_COUNT_MAX)
                .setTakeType(CouponTakeTypeEnum.REGISTER.getType())
                .setUsePrice(0)
                .setProductScope(1)
                .setValidityType(CouponTemplateValidityTypeEnum.TERM.getType())
                .setFixedStartTerm(fixedStartTerm)
                .setFixedEndTerm(fixedEndTerm)
                .setDiscountType(1)
                .setDiscountPrice(1000)
                .setTakeCount(0)
                .setUseCount(0);
        couponTemplateMapper.insert(template);
        return template;
    }

}
