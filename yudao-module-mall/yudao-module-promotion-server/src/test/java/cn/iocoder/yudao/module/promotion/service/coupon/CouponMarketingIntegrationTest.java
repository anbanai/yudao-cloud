package cn.iocoder.yudao.module.promotion.service.coupon;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.*;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.*;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.*;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.annotation.Resource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Import({CouponServiceImpl.class,CouponBatchService.class,CouponBatchSubmitService.class,CouponPromotionService.class,cn.iocoder.yudao.module.promotion.job.coupon.CouponOrphanUseRecoveryJob.class,org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class})
class CouponMarketingIntegrationTest extends BaseDbUnitTest {
 @Resource private CouponService coupons;
 @Resource private CouponBatchService batches;
 @Resource private CouponPromotionService promotions;
 @Resource private CouponMapper mapper;
 @Resource private CouponTemplateMapper templates;
 @Resource private javax.sql.DataSource dataSource;
    private JdbcTemplate jdbc;
 @MockBean private CouponTemplateService templateService;
 @MockBean private CouponNewcomerService newcomer;
 @MockBean private MemberUserApi users;
 @MockBean private cn.iocoder.yudao.module.trade.api.order.TradeOrderApi orderApi;
 @Resource private cn.iocoder.yudao.module.promotion.job.coupon.CouponOrphanUseRecoveryJob recovery;
 @BeforeEach void init(){
  jdbc = new JdbcTemplate(dataSource);
  jdbc.update("INSERT INTO promotion_coupon_template(id,name,status,total_count,take_limit_count,take_type,use_price,product_scope,validity_type,fixed_start_term,fixed_end_term,discount_type,discount_price) " +
          "VALUES(1,'召回券',0,100,-1,?,0,1,2,0,30,1,100)",CouponTakeTypeEnum.ADMIN.getType());
  when(users.getUser(anyLong())).thenAnswer(i->CommonResult.success(new MemberUserRespDTO().setId(i.getArgument(0))));
  when(users.getUserByMobile("13800000000")).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(1L)));
  when(users.getUserListByTags(anyCollection())).thenReturn(CommonResult.success(Arrays.asList(new MemberUserRespDTO().setId(1L),new MemberUserRespDTO().setId(2L))));
  when(templateService.isTakeLimitCountUnlimited(-1)).thenReturn(true);
  when(templateService.isTotalCountUnlimited(-1)).thenReturn(true);
  doAnswer(i->{if(templates.updateTakeCount(i.getArgument(0),i.getArgument(1))==0){throw new IllegalArgumentException("库存不足");}return null;}).when(templateService).updateCouponTemplateTakeCount(anyLong(),anyInt());
 }
 CouponSendBatchReqVO req(String key){return new CouponSendBatchReqVO().setTemplateId(1L).setUserIds(Arrays.asList(1L,2L)).setIdempotencyKey(key);}
 @Test void previewDoesNotIssueAndSubmitIsIdempotent(){
  CouponSendBatchDO b=batches.preview(req("a").setMobiles(Arrays.asList("13800000000","13800000000")).setTagIds(Collections.singletonList(8L)),9L);
  assertEquals(2,b.getTotalCount());assertEquals(0L,mapper.selectCount());assertNotNull(b.getTemplateSnapshot());
  assertEquals(2,batches.submit(b.getId(),9L).getSuccessCount());batches.submit(b.getId(),9L);assertEquals(2L,mapper.selectCount());
  assertEquals(2,templates.selectById(1L).getTakeCount());
 }
 @Test void invalidListOrChangedTemplateNeverIssues(){
  CouponSendBatchDO invalid=batches.preview(req("bad").setMobiles(Collections.singletonList("invalid")),9L);
  assertEquals(1,invalid.getFailureCount());
  CouponSendBatchDO failed=batches.submit(invalid.getId(),9L);
  assertEquals(2,failed.getStatus());assertEquals(1,failed.getFailureCount());
  assertEquals(failed.getId(),batches.submit(invalid.getId(),9L).getId());
  CouponSendBatchDO valid=batches.preview(req("changed"),9L);jdbc.update("UPDATE promotion_coupon_template SET name='另一种券' WHERE id=1");
  assertEquals(2,batches.submit(valid.getId(),9L).getStatus());assertEquals(0L,mapper.selectCount());
 }
 @Test void insufficientStockRollsBackWholeBatch(){
  CouponSendBatchDO b=batches.preview(req("stock"),9L);jdbc.update("UPDATE promotion_coupon_template SET take_count=99 WHERE id=1");
  CouponSendBatchDO failed=batches.submit(b.getId(),9L);assertEquals(2,failed.getStatus());
  assertEquals(failed.getId(),batches.submit(b.getId(),9L).getId());
  assertEquals(0L,mapper.selectCount());assertEquals(99,templates.selectById(1L).getTakeCount());
 }
 @Test void capAndDisabledTemplateAreCheckedBeforeIssuance(){
  jdbc.update("UPDATE promotion_coupon_template SET total_count=1 WHERE id=1");
  assertThrows(RuntimeException.class,()->coupons.takeCouponByAdmin(1L,new HashSet<>(Arrays.asList(1L,2L))));
  assertEquals(0L,mapper.selectCount());jdbc.update("UPDATE promotion_coupon_template SET status=1 WHERE id=1");
  assertThrows(RuntimeException.class,()->coupons.takeCouponByAdmin(1L,Collections.singleton(1L)));
 }
 @Test void promotionRetryRetainsOriginalAttributionEvenAfterRevocation(){
  jdbc.update("UPDATE promotion_coupon_template SET take_type=? WHERE id=1",CouponTakeTypeEnum.USER.getType());
  CouponPromotionDO p=promotions.create(new CouponPromotionCreateReqVO().setTemplateId(1L).setSource("recall").setChannel("wechat").setIdempotencyKey("promo"));
  List<Long> first=promotions.claim(p.getShortCode(),1L);assertEquals(first,promotions.claim(p.getShortCode(),1L));
  coupons.deleteCoupon(first.get(0));assertEquals(first,promotions.claim(p.getShortCode(),1L));assertEquals(1,promotions.resolve(p.getShortCode()).getTakeCount());
 }
 @Test void multiUseIsAtomicAndOldRefundCannotReturnCouponFromNewOrder(){
  jdbc.update("UPDATE promotion_coupon_template SET take_limit_count=2 WHERE id=1");
  Long a=coupons.takeCouponByAdmin(1L,Collections.singleton(1L)).get(1L).get(0);
  Long b=coupons.takeCouponByAdmin(1L,Collections.singleton(1L)).get(1L).get(0);
  coupons.useCoupon(b,1L,11L);
  assertThrows(RuntimeException.class,()->coupons.useCoupons(Arrays.asList(a,b),1L,10L));
  assertEquals(1,mapper.selectById(a).getStatus());
  coupons.returnCoupons(Collections.singletonList(b),11L);coupons.returnCoupons(Collections.singletonList(b),11L);
  coupons.useCoupon(b,1L,12L);coupons.returnCoupons(Collections.singletonList(b),11L);
  assertEquals(2,mapper.selectById(b).getStatus());assertEquals(12L,mapper.selectById(b).getUseOrderId());
 }
 @Test void orphanUseRecoveryReturnsOnlyMissingOrCanceledOrderCoupons(){
  Long id=coupons.takeCouponByAdmin(1L,Collections.singleton(1L)).get(1L).get(0);
  coupons.useCoupon(id,1L,99L);jdbc.update("UPDATE promotion_coupon SET use_time=? WHERE id=?",LocalDateTime.now().minusHours(1),id);
  when(orderApi.getCouponOrderStatus(99L)).thenReturn(CommonResult.success(10));recovery.execute();assertEquals(2,mapper.selectById(id).getStatus());
  when(orderApi.getCouponOrderStatus(99L)).thenReturn(CommonResult.success(null));recovery.execute();assertEquals(1,mapper.selectById(id).getStatus());
 }
 @Test void exchangeTracksUseFulfillmentAndReturnWithoutExtendingValidity(){
  jdbc.update("UPDATE promotion_coupon_template SET coupon_type=2,exchange_spu_id=10,exchange_sku_id=20 WHERE id=1");
  Long id=coupons.takeCouponByAdmin(1L,Collections.singleton(1L)).get(1L).get(0);LocalDateTime end=mapper.selectById(id).getValidEndTime();
  coupons.useCoupon(id,1L,10L);coupons.useCoupon(id,1L,10L);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM promotion_coupon_exchange_record",Integer.class));
  coupons.fulfillExchange(10L);assertEquals(1,jdbc.queryForObject("SELECT status FROM promotion_coupon_exchange_record",Integer.class));
  coupons.returnCoupons(Collections.singletonList(id),10L);assertEquals(2,jdbc.queryForObject("SELECT status FROM promotion_coupon_exchange_record",Integer.class));assertEquals(end,mapper.selectById(id).getValidEndTime());
 }
}
