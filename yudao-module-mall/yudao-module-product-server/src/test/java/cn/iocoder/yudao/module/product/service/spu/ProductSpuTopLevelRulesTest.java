package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.level.dto.MemberLevelRespDTO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuAccessRespDTO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductSpuTopLevelRulesTest {
    private ProductSpuServiceImpl service;
    private ProductSpuMapper mapper;
    private MemberUserApi users;
    private MemberLevelApi levels;

    @BeforeEach
    void setUp() {
        service = new ProductSpuServiceImpl();
        mapper = mock(ProductSpuMapper.class);
        users = mock(MemberUserApi.class);
        levels = mock(MemberLevelApi.class);
        ReflectionTestUtils.setField(service, "productSpuMapper", mapper);
        ReflectionTestUtils.setField(service, "memberUserApi", users);
        ReflectionTestUtils.setField(service, "memberLevelApi", levels);
    }

    @Test
    void validatesActualAdminPresaleAndRateFields() throws Exception {
        ProductSpuSaveReqVO valid = new ProductSpuSaveReqVO();
        valid.setIsPreSale(true); valid.setDeliveryDays(90); valid.setBuyLimit(0);
        valid.setBuyLimitLevel("ALL"); valid.setPointDiscountRate(new BigDecimal("0.75"));
        validate(valid);
        assertThatThrownBy(() -> validate(request(true, 91, null, null)))
                .isInstanceOf(InvocationTargetException.class);
        assertThatThrownBy(() -> validate(request(false, 5, null, null)))
                .isInstanceOf(InvocationTargetException.class);
        assertThatThrownBy(() -> validate(request(null, null, null, new BigDecimal("1.01"))))
                .isInstanceOf(InvocationTargetException.class);
        assertThatThrownBy(() -> validate(request(null, null, -1, null)))
                .isInstanceOf(InvocationTargetException.class);
    }

    @Test
    void matchedTagsUseTheSmallestPositiveLimitAndExactTierEligibility() {
        ProductSpuDO spu = new ProductSpuDO();
        spu.setId(7L); spu.setStatus(ProductSpuStatusEnum.ENABLE.getStatus()); spu.setBuyLimit(10);
        spu.setBuyLimitLevel("L2"); spu.setIsPreSale(true); spu.setDeliveryDays(5);
        spu.setMarketingConfig(new HashMap<String, Object>() {{
                    put("userGroups", Arrays.asList("8", "9"));
                    put("userGroupPurchaseLimits", new LinkedHashMap<String, Integer>() {{ put("8", 4); put("9", 2); }});
                }});
        when(mapper.selectById(7L)).thenReturn(spu);
        MemberUserRespDTO user = new MemberUserRespDTO(); user.setId(22L); user.setLevelId(2L); user.setTagIds(Arrays.asList(8L, 9L));
        when(users.getUser(22L)).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(user));
        MemberLevelRespDTO level = new MemberLevelRespDTO(); level.setId(2L); level.setLevel(2);
        when(levels.getMemberLevel(2L)).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(level));

        ProductSpuAccessRespDTO allowed = service.checkAccess(7L, 22L, 2);
        ProductSpuAccessRespDTO denied = service.checkAccess(7L, 22L, 3);
        assertThat(allowed.getVisible()).isTrue();
        assertThat(allowed.getPurchasable()).isTrue();
        assertThat(allowed.getEffectiveBuyLimit()).isEqualTo(2);
        assertThat(allowed.getDeliveryDays()).isEqualTo(5);
        assertThat(denied.getPurchasable()).isFalse();
    }

    @Test
    void missingTagSnapshotFailsClosed() {
        ProductSpuDO spu = new ProductSpuDO(); spu.setId(8L); spu.setStatus(ProductSpuStatusEnum.ENABLE.getStatus());
        spu.setMarketingConfig(Collections.<String, Object>singletonMap("userGroups", Collections.singletonList("8")));
        when(mapper.selectById(8L)).thenReturn(spu);
        MemberUserRespDTO user = new MemberUserRespDTO(); user.setId(22L); user.setTagIds(null);
        when(users.getUser(22L)).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(user));
        assertThat(service.checkAccess(8L, 22L, 1).getPurchasable()).isFalse();
    }

    @Test
    void numericAudienceIdsMatchLongMemberAndTagIds() {
        ProductSpuDO spu = new ProductSpuDO(); spu.setId(9L); spu.setStatus(ProductSpuStatusEnum.ENABLE.getStatus());
        spu.setMarketingConfig(new HashMap<String, Object>() {{
            put("memberLevels", Collections.singletonList(2));
            put("userGroups", Collections.singletonList(8));
        }});
        when(mapper.selectById(9L)).thenReturn(spu);
        MemberUserRespDTO user = new MemberUserRespDTO(); user.setId(22L); user.setLevelId(2L); user.setTagIds(Collections.singletonList(8L));
        when(users.getUser(22L)).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(user));
        MemberLevelRespDTO level = new MemberLevelRespDTO(); level.setId(2L); level.setLevel(2);
        when(levels.getMemberLevel(2L)).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(level));

        ProductSpuAccessRespDTO result = service.checkAccess(9L, 22L, 1);

        assertThat(result.getVisible()).isTrue();
        assertThat(result.getPurchasable()).isTrue();
    }

    private void validate(ProductSpuSaveReqVO req) throws Exception {
        Method method = ProductSpuServiceImpl.class.getDeclaredMethod("validateProductRules", ProductSpuSaveReqVO.class);
        method.setAccessible(true);
        method.invoke(service, req);
    }

    private ProductSpuSaveReqVO request(Boolean presale, Integer days, Integer limit, BigDecimal ratio) {
        ProductSpuSaveReqVO req = new ProductSpuSaveReqVO();
        req.setIsPreSale(presale); req.setDeliveryDays(days); req.setBuyLimit(limit); req.setPointDiscountRate(ratio);
        return req;
    }
}
