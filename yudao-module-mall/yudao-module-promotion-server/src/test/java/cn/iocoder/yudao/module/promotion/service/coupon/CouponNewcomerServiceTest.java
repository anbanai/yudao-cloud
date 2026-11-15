package cn.iocoder.yudao.module.promotion.service.coupon;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.user.*;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.member.api.level.*;
import cn.iocoder.yudao.module.member.api.level.dto.MemberLevelRespDTO;
import cn.iocoder.yudao.module.trade.api.order.TradeOrderApi;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CouponNewcomerServiceTest {
 CouponNewcomerService service;CouponTemplateService templates;CouponService coupons;MemberUserApi users;MemberLevelApi levels;TradeOrderApi orders;CouponTemplateDO template;
 @BeforeEach void init(){service=new CouponNewcomerService();templates=mock(CouponTemplateService.class);coupons=mock(CouponService.class);users=mock(MemberUserApi.class);levels=mock(MemberLevelApi.class);orders=mock(TradeOrderApi.class);
 ReflectionTestUtils.setField(service,"couponTemplateService",templates);ReflectionTestUtils.setField(service,"couponService",coupons);ReflectionTestUtils.setField(service,"memberUserApi",users);ReflectionTestUtils.setField(service,"memberLevelApi",levels);ReflectionTestUtils.setField(service,"tradeOrderApi",orders);
 template=new CouponTemplateDO().setId(1L).setStatus(0).setNewcomerOnly(true).setTakeType(CouponTakeTypeEnum.USER.getType()).setTotalCount(100).setTakeCount(0).setValidityType(2);
 when(templates.getCouponTemplate(1L)).thenReturn(template);when(users.getUser(9L)).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(9L).setLevelId(10L)));when(levels.getMemberLevel(10L)).thenReturn(CommonResult.success(new MemberLevelRespDTO().setLevel(0)));when(orders.hasOrderHistory(9L)).thenReturn(CommonResult.success(false));when(coupons.getTakeCount(1L,9L)).thenReturn(0);when(coupons.getUserCanCanTakeMap(eq(9L),anyList())).thenReturn(Collections.singletonMap(1L,true));}
 @Test void visitorCanSeeButMustLogin(){assertTrue(service.qualification(1L,null).getShowEntry());assertEquals("LOGIN_REQUIRED",service.qualification(1L,null).getReasonCode());}
 @Test void onlyL0WithNoHistoricalOrdersCanTake(){assertTrue(service.qualification(1L,9L).getCanTake());when(orders.hasOrderHistory(9L)).thenReturn(CommonResult.success(true));assertEquals("ORDER_HISTORY_EXISTS",service.qualification(1L,9L).getReasonCode());}
 @Test void upgradeHidesEntryAndUnknownLevelFailsClosed(){when(levels.getMemberLevel(10L)).thenReturn(CommonResult.success(new MemberLevelRespDTO().setLevel(1)));assertFalse(service.qualification(1L,9L).getShowEntry());when(levels.getMemberLevel(10L)).thenReturn(CommonResult.success(null));assertFalse(service.qualification(1L,9L).getCanTake());}
 @Test void alreadyTakenAndUnavailableTemplatesCannotTake(){when(coupons.getTakeCount(1L,9L)).thenReturn(1);assertEquals("ALREADY_TAKEN",service.qualification(1L,9L).getReasonCode());template.setStatus(1);assertNull(service.qualification(1L,9L));}
 @Test void ordinaryTemplatesCannotBeClaimedAsNewcomer(){template.setNewcomerOnly(false);assertNull(service.qualification(1L,9L));}
}
