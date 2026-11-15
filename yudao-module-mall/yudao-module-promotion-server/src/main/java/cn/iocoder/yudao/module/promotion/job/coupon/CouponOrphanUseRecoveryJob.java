package cn.iocoder.yudao.module.promotion.job.coupon;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponMapper;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponStatusEnum;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponService;
import cn.iocoder.yudao.module.trade.api.order.TradeOrderApi;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
/** Coupon state is the durable retry source when the trade transaction rolls back after an RPC commit. */
@Component @Slf4j
public class CouponOrphanUseRecoveryJob {
 @Resource private CouponMapper mapper;
 @Resource private CouponService service;
 @Resource private TradeOrderApi orders;
 @Scheduled(cron="0 */10 * * * ?") @TenantJob
 public void execute() {
  Long lastId=0L;
  while(true) {
   List<CouponDO> batch=mapper.selectList(new LambdaQueryWrapperX<CouponDO>()
     .eq(CouponDO::getStatus,CouponStatusEnum.USED.getStatus()).lt(CouponDO::getUseTime,LocalDateTime.now().minusMinutes(30))
     .gt(CouponDO::getId,lastId).orderByAsc(CouponDO::getId).last("LIMIT 200"));
   if(batch.isEmpty()){return;}
   for(CouponDO coupon:batch){
    lastId=coupon.getId();
    try{
     Integer status=orders.getCouponOrderStatus(coupon.getUseOrderId()).getCheckedData();
     if(status==null || Objects.equals(status,TradeOrderStatusEnum.CANCELED.getStatus())){
      service.returnCoupons(Collections.singletonList(coupon.getId()),coupon.getUseOrderId());
     }
    }catch(Exception ex){log.error("[couponOrphanRecovery][coupon({}) 校验/补偿失败，下轮重试]",coupon.getId(),ex);}
   }
  }
 }
}
