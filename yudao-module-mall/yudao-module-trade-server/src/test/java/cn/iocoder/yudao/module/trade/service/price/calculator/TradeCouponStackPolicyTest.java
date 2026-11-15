package cn.iocoder.yudao.module.trade.service.price.calculator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TradeCouponStackPolicyTest {
 @Test void eitherCouponCanVetoStacking(){ assertNotNull(TradeCouponStackPolicy.exclusion(false,3,true,1,true,false)); assertNotNull(TradeCouponStackPolicy.exclusion(true,3,true,1,false,false)); }
 @Test void limitAndOtherMarketingAreIndependent(){ assertNotNull(TradeCouponStackPolicy.exclusion(true,1,true,1,true,false));assertNotNull(TradeCouponStackPolicy.exclusion(true,3,false,0,true,true));assertNull(TradeCouponStackPolicy.exclusion(true,3,true,1,true,true)); }
}
