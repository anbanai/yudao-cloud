package cn.iocoder.yudao.module.promotion.service.coupon;
import cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.CouponPromotionCreateReqVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.*;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.*;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.*;

@Service
public class CouponPromotionService {
    @Resource private CouponPromotionMapper promotionMapper;
    @Resource private CouponTemplateMapper templateMapper;
    @Resource private CouponMapper couponMapper;
    @Resource private CouponPromotionTakeMapper takeMapper;
    @Resource private CouponService couponService;

    @Transactional(rollbackFor = Exception.class)
    public CouponPromotionDO create(CouponPromotionCreateReqVO req) {
        CouponPromotionDO existing = promotionMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (existing != null) {
            if (!Objects.equals(existing.getTemplateId(), req.getTemplateId()) || !Objects.equals(existing.getSource(), req.getSource())
                    || !Objects.equals(existing.getChannel(), req.getChannel())) { throw new IllegalArgumentException("幂等键冲突"); }
            return existing;
        }
        CouponTemplateDO template = templateMapper.selectById(req.getTemplateId());
        if (template == null || !Integer.valueOf(0).equals(template.getStatus()) || !Objects.equals(template.getTakeType(), CouponTakeTypeEnum.USER.getType())) {
            throw new IllegalArgumentException("模板不可推广领取");
        }
        String code = UUID.randomUUID().toString().replace("-", "");
        CouponPromotionDO promotion = new CouponPromotionDO().setTemplateId(req.getTemplateId()).setSource(req.getSource())
                .setChannel(req.getChannel()).setShortCode(code).setIdempotencyKey(req.getIdempotencyKey())
                .setMiniProgramPath("/pages/coupon/detail?id=" + req.getTemplateId() + "&promotionCode=" + code)
                .setVisitCount(0).setTakeCount(0);
        promotionMapper.insert(promotion);
        return promotion;
    }

    public CouponPromotionDO resolve(String code) {
        return promotionMapper.selectByShortCode(code);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> claim(String code, Long userId) {
        CouponPromotionDO promotion = promotionMapper.selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CouponPromotionDO>()
                .eq(CouponPromotionDO::getShortCode, code).last("FOR UPDATE"));
        if (promotion == null) { throw new IllegalArgumentException("推广码不存在"); }
        CouponPromotionTakeDO existing = takeMapper.selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CouponPromotionTakeDO>()
                .eq(CouponPromotionTakeDO::getPromotionId, promotion.getId()).eq(CouponPromotionTakeDO::getUserId, userId));
        if (existing != null) { return Collections.singletonList(existing.getCouponId()); }
        List<Long> ids = couponService.takeCoupon(promotion.getTemplateId(), new HashSet<>(Collections.singleton(userId)), CouponTakeTypeEnum.USER).get(userId);
        for (Long id : ids) {
            couponMapper.updateById(new CouponDO().setId(id).setPromotionId(promotion.getId()));
            takeMapper.insert(new CouponPromotionTakeDO().setPromotionId(promotion.getId()).setUserId(userId).setCouponId(id));
        }
        promotion.setTakeCount(promotion.getTakeCount() + ids.size());
        promotionMapper.updateById(promotion);
        return ids;
    }
}
