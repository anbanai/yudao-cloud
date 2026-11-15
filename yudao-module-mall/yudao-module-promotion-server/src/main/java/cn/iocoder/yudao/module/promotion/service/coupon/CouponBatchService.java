package cn.iocoder.yudao.module.promotion.service.coupon;

import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.CouponSendBatchReqVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.*;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.*;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponSendBatchStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/** Preview freezes the audience; only submit issues coupons. A batch is all-or-nothing. */
@Service
public class CouponBatchService {
    @Resource private CouponSendBatchMapper batchMapper;
    @Resource private CouponTemplateMapper templateMapper;
    @Resource private CouponService couponService;
    @Resource private CouponBatchSubmitService submitService;
    @Resource private MemberUserApi userApi;

    @Transactional(rollbackFor = Exception.class)
    public CouponSendBatchDO preview(CouponSendBatchReqVO req, Long operatorId) {
        CouponSendBatchDO existing = batchMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (existing != null) {
            require(Objects.equals(existing.getOperatorId(), operatorId)
                    && Objects.equals(existing.getTemplateId(), req.getTemplateId()), "幂等键已被其它请求使用");
            return existing;
        }
        CouponTemplateDO template = templateMapper.selectById(req.getTemplateId());
        require(template != null && Integer.valueOf(0).equals(template.getStatus())
                && Objects.equals(template.getTakeType(), CouponTakeTypeEnum.ADMIN.getType()), "模板不能定向发放");
        Set<Long> ids = new TreeSet<>();
        List<String> failures = new ArrayList<>();
        Set<String> mobiles = new TreeSet<>();
        if (req.getUserIds() != null) {
            require(req.getUserIds().size() <= 5000, "每批最多5000人");
            for (Long id : req.getUserIds()) {
                MemberUserRespDTO user = id == null ? null : userApi.getUser(id).getCheckedData();
                if (user == null) { failures.add("USER_NOT_FOUND:" + id); } else { ids.add(id); }
            }
        }
        if (req.getMobiles() != null) {
            require(req.getMobiles().size() <= 5000, "每批最多5000手机号");
            for (String raw : req.getMobiles()) {
                String mobile = raw == null ? "" : raw.trim();
                if (!mobiles.add(mobile)) { continue; }
                if (!mobile.matches("^1[3-9][0-9]{9}$")) { failures.add("INVALID_MOBILE:" + mobile); continue; }
                MemberUserRespDTO user = userApi.getUserByMobile(mobile).getCheckedData();
                if (user == null) { failures.add("MOBILE_NOT_FOUND:" + mobile); } else { ids.add(user.getId()); }
            }
        }
        if (req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            require(req.getTagIds().size() <= 100, "标签过多");
            List<MemberUserRespDTO> users = userApi.getUserListByTags(req.getTagIds()).getCheckedData();
            for (MemberUserRespDTO user : users) { ids.add(user.getId()); }
        }
        require(ids.size() <= 5000, "每批最多5000人，请缩小圈选范围");
        if (Integer.valueOf(1).equals(template.getValidityType()) && template.getValidEndTime().isBefore(java.time.LocalDateTime.now())) {
            failures.add("TEMPLATE_EXPIRED");
        }
        for (Long id : ids) {
            if (template.getTakeLimitCount() != -1 && couponService.getTakeCount(template.getId(), id) >= template.getTakeLimitCount()) {
                failures.add("TAKE_LIMIT_REACHED:" + id);
            }
        }
        if (template.getTotalCount() != -1 && (long) template.getTakeCount() + ids.size() > template.getTotalCount()) { failures.add("TEMPLATE_NOT_ENOUGH"); }
        CouponSendBatchDO batch = new CouponSendBatchDO().setTemplateId(req.getTemplateId())
                .setOperatorId(operatorId).setStatus(0).setIdempotencyKey(req.getIdempotencyKey())
                .setTotalCount(ids.size()).setSuccessCount(0).setFailureCount(failures.size())
                .setMobileSnapshot(String.join(",", mobiles))
                .setUserSnapshot(ids.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .setFailureDetails(String.join("\n", failures)).setTemplateSnapshot(snapshot(template));
        batchMapper.insert(batch);
        return batch;
    }

    public CouponSendBatchDO submit(Long id, Long operatorId) {
        try {
            return submitService.submit(id, operatorId);
        } catch (RuntimeException exception) {
            CouponSendBatchDO batch = batchMapper.selectById(id);
            require(batch != null && Objects.equals(batch.getOperatorId(), operatorId), "发放批次不存在");
            if (Integer.valueOf(CouponSendBatchStatusEnum.PENDING.getStatus()).equals(batch.getStatus())) {
                batchMapper.markFailedIfPending(id, operatorId,
                        Math.max(1, batch.getFailureCount() == null ? 0 : batch.getFailureCount()),
                        exception.getMessage() == null ? "发放失败" : exception.getMessage());
                return batchMapper.selectById(id);
            }
            if (Integer.valueOf(CouponSendBatchStatusEnum.FAILED.getStatus()).equals(batch.getStatus())) {
                return batch;
            }
            throw exception;
        }
    }

    private String snapshot(CouponTemplateDO template) {
        return cn.hutool.json.JSONUtil.toJsonStr(cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(template,
                cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.template.CouponTemplateBaseVO.class));
    }

    public CouponSendBatchDO get(Long id) { return batchMapper.selectById(id); }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalArgumentException(message); }
    }
}
