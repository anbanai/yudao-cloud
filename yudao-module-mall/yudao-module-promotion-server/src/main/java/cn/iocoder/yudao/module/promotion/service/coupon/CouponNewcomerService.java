package cn.iocoder.yudao.module.promotion.service.coupon;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.level.dto.MemberLevelRespDTO;
import cn.iocoder.yudao.module.trade.api.order.TradeOrderApi;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.controller.app.coupon.vo.template.AppNewcomerCouponRespVO;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import jakarta.annotation.Resource;
import java.util.*;
import static java.util.Collections.singletonList;
@Service
public class CouponNewcomerService {
    @Resource private CouponTemplateService couponTemplateService;
    @Resource @Lazy private CouponService couponService;
    @Resource private MemberUserApi memberUserApi;
    @Resource private MemberLevelApi memberLevelApi;
    @Resource
    @Lazy // 订单处理和计价依赖 CouponApi，查询历史订单时再解析此依赖。
    private TradeOrderApi tradeOrderApi;

    public AppNewcomerCouponRespVO qualification(Long id, Long userId) {
        CouponTemplateDO template = couponTemplateService.getCouponTemplate(id);
        if (template == null || !Objects.equals(template.getStatus(), 0) || !Boolean.TRUE.equals(template.getNewcomerOnly())) {
            return null;
        }
        AppNewcomerCouponRespVO result = BeanUtils.toBean(template, AppNewcomerCouponRespVO.class)
                .setTemplateId(template.getId()).setShowEntry(false);
        if (userId == null) {
            return result.setCanTake(false).setReasonCode("LOGIN_REQUIRED").setReason("登录后可领取");
        }
        if (!hasNoOrders(userId)) {
            return result.setCanTake(false).setReasonCode("ORDER_HISTORY_EXISTS").setReason("已有历史订单");
        }
        MemberUserRespDTO user = memberUserApi.getUser(userId).getCheckedData();
        if (user == null) {
            return result.setCanTake(false).setReasonCode("USER_NOT_FOUND").setReason("会员信息不存在");
        }
        result.setShowEntry(true);
        if (!Objects.equals(template.getTakeType(), CouponTakeTypeEnum.USER.getType())) {
            return result.setCanTake(false).setReasonCode("REGISTER_AUTO_ISSUE")
                    .setReason("该模板用于注册自动发放，请使用已发放的优惠券");
        }
        MemberLevelRespDTO level = user.getLevelId() == null || user.getLevelId() == 0
                ? null : memberLevelApi.getMemberLevel(user.getLevelId()).getCheckedData();
        if (user.getLevelId() != null && user.getLevelId() != 0 && (level == null || !Integer.valueOf(0).equals(level.getLevel()))) {
            return result.setCanTake(false).setReasonCode("LEVEL_UPGRADED")
                    .setReason("会员等级已升级");
        }
        Integer takeCount = couponService.getTakeCount(id, userId);
        if (takeCount > 0) {
            return result.setCanTake(false).setReasonCode("ALREADY_TAKEN").setReason("已领取过新人券");
        }
        Map<Long, Boolean> canTakeMap = couponService.getUserCanCanTakeMap(userId, singletonList(template));
        if (!Boolean.TRUE.equals(canTakeMap.get(id))) {
            return result.setCanTake(false).setReasonCode("TAKE_LIMIT_REACHED").setReason("已达到领取次数上限");
        }
        if ((template.getTotalCount() != -1 && template.getTakeCount() >= template.getTotalCount())
                || (Integer.valueOf(1).equals(template.getValidityType()) && template.getValidEndTime().isBefore(java.time.LocalDateTime.now()))) {
            return result.setCanTake(false).setReasonCode("TEMPLATE_EXHAUSTED").setReason("模板已领完或过期");
        }
        return result.setCanTake(true).setReasonCode(null).setReason(null);
    }

    public boolean canShowVisitorEntry(Long userId) {
        MemberUserRespDTO member = memberUserApi.getUser(userId).getCheckedData();
        return member != null && Objects.equals(member.getId(), userId) && hasNoOrders(userId);
    }

    private boolean hasNoOrders(Long userId) {
        return Boolean.FALSE.equals(tradeOrderApi.hasOrderHistory(userId).getCheckedData());
    }
}
