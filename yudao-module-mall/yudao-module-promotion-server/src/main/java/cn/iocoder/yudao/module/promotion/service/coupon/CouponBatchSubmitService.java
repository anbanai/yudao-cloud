package cn.iocoder.yudao.module.promotion.service.coupon;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.template.CouponTemplateBaseVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponSendBatchDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponSendBatchMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponSendBatchStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CouponBatchSubmitService {

    @Resource
    private CouponSendBatchMapper batchMapper;
    @Resource
    private CouponTemplateMapper templateMapper;
    @Resource
    private CouponService couponService;

    @Transactional(rollbackFor = Exception.class)
    public CouponSendBatchDO submit(Long id, Long operatorId) {
        CouponSendBatchDO batch = batchMapper.selectOne(new LambdaQueryWrapperX<CouponSendBatchDO>()
                .eq(CouponSendBatchDO::getId, id).last("FOR UPDATE"));
        require(batch != null && Objects.equals(batch.getOperatorId(), operatorId), "发放批次不存在");
        if (Integer.valueOf(CouponSendBatchStatusEnum.SUCCEEDED.getStatus()).equals(batch.getStatus())
                || Integer.valueOf(CouponSendBatchStatusEnum.FAILED.getStatus()).equals(batch.getStatus())) {
            return batch;
        }
        require(Integer.valueOf(CouponSendBatchStatusEnum.PENDING.getStatus()).equals(batch.getStatus())
                && batch.getTotalCount() > 0, "批次不能提交");
        require(batch.getFailureCount() == 0, "名单有错误，请修正后重新预检");

        CouponTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapperX<CouponTemplateDO>()
                .eq(CouponTemplateDO::getId, batch.getTemplateId()).last("FOR UPDATE"));
        require(template != null && Objects.equals(batch.getTemplateSnapshot(), snapshot(template)),
                "模板已变更，请重新预检确认");

        Set<Long> users = Arrays.stream(batch.getUserSnapshot().split(","))
                .map(Long::valueOf).collect(Collectors.toSet());
        Map<Long, java.util.List<Long>> issued = couponService.takeCouponByAdmin(batch.getTemplateId(), users);
        require(issued.size() == batch.getTotalCount(), "部分用户已达限额，批次已回滚，请重新预检");
        batch.setStatus(CouponSendBatchStatusEnum.SUCCEEDED.getStatus()).setSuccessCount(issued.size());
        batchMapper.updateById(batch);
        return batch;
    }

    private String snapshot(CouponTemplateDO template) {
        return cn.hutool.json.JSONUtil.toJsonStr(BeanUtils.toBean(template, CouponTemplateBaseVO.class));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
