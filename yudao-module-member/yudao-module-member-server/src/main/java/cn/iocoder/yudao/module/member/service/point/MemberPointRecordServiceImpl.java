package cn.iocoder.yudao.module.member.service.point;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointRecordDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointRecordStatusEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.USER_POINT_NOT_ENOUGH;


/**
 * 积分记录 Service 实现类
 *
 * @author QingX
 */
@Slf4j
@Service
@Validated
public class MemberPointRecordServiceImpl implements MemberPointRecordService {

    @Resource
    private MemberPointRecordMapper memberPointRecordMapper;

    @Resource
    private MemberUserService memberUserService;

    @Override
    public PageResult<MemberPointRecordDO> getPointRecordPage(MemberPointRecordPageReqVO pageReqVO) {
        // 根据用户昵称查询出用户 ids
        Set<Long> userIds = null;
        if (StringUtils.isNotBlank(pageReqVO.getNickname())) {
            List<MemberUserDO> users = memberUserService.getUserListByNickname(pageReqVO.getNickname());
            // 如果查询用户结果为空直接返回无需继续查询
            if (CollectionUtils.isEmpty(users)) {
                return PageResult.empty();
            }
            userIds = convertSet(users, MemberUserDO::getId);
        }
        // 执行查询
        return memberPointRecordMapper.selectPage(pageReqVO, userIds);
    }

    @Override
    public PageResult<MemberPointRecordDO> getPointRecordPage(Long userId, AppMemberPointRecordPageReqVO pageReqVO) {
        return memberPointRecordMapper.selectPage(userId, pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPointRecord(Long userId, Integer point, MemberPointBizTypeEnum bizType, String bizId) {
        if (point == 0) {
            return;
        }
        MemberUserDO user = memberUserService.getUserForUpdate(userId);
        if (isOrderGiveBizType(bizType)
                && memberPointRecordMapper.selectByUserIdAndBizTypeAndBizId(userId, bizType.getType(), bizId) != null) {
            return;
        }
        // 1. 校验用户积分余额
        Integer userPoint = ObjectUtil.defaultIfNull(user.getPoint(), 0);
        int totalPoint = userPoint + point; // 用户变动后的积分
        // 订单奖励回滚必须完整扣回，即使用户已消费奖励导致余额不足；此时允许形成负余额，
        // 后续获得积分会优先抵消该欠账。普通积分消费仍不允许余额为负。
        if (totalPoint < 0 && !isRewardRollbackBizType(bizType)) {
            log.error("[createPointRecord][userId({}) point({}) bizType({}) bizId({}) {}]", userId, point, bizType, bizId,
                    USER_POINT_NOT_ENOUGH);
            return;
        }

        // 2. 更新用户积分
        boolean success = memberUserService.updateUserPoint(userId, point);
        if (!success) {
            throw exception(USER_POINT_NOT_ENOUGH);
        }

        // 3. 增加积分记录
        MemberPointRecordDO record = new MemberPointRecordDO()
                .setUserId(userId).setBizId(bizId).setBizType(bizType.getType())
                .setTitle(bizType.getName()).setDescription(StrUtil.format(bizType.getDescription(), point))
                .setPoint(point).setTotalPoint(totalPoint)
                .setStatus(MemberPointRecordStatusEnum.EFFECTIVE.getStatus())
                .setEffectiveTime(LocalDateTime.now());
        memberPointRecordMapper.insert(record);
    }

    private boolean isOrderGiveBizType(MemberPointBizTypeEnum bizType) {
        return bizType == MemberPointBizTypeEnum.ORDER_GIVE
                || bizType == MemberPointBizTypeEnum.ORDER_GIVE_CANCEL
                || bizType == MemberPointBizTypeEnum.ORDER_GIVE_CANCEL_ITEM;
    }

    private boolean isRewardRollbackBizType(MemberPointBizTypeEnum bizType) {
        return bizType == MemberPointBizTypeEnum.ORDER_GIVE_CANCEL
                || bizType == MemberPointBizTypeEnum.ORDER_GIVE_CANCEL_ITEM;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPendingPointRecord(Long userId, Integer point, MemberPointBizTypeEnum bizType, String bizId) {
        if (point == null || point <= 0) {
            return;
        }
        MemberUserDO user = memberUserService.getUserForUpdate(userId);
        // Payment callbacks are retried; an existing record, regardless of state, is the idempotency result.
        if (memberPointRecordMapper.selectByUserIdAndBizTypeAndBizId(userId, bizType.getType(), bizId) != null) {
            return;
        }
        int totalPoint = ObjectUtil.defaultIfNull(user.getPoint(), 0);
        MemberPointRecordDO record = new MemberPointRecordDO()
                .setUserId(userId).setBizId(bizId).setBizType(bizType.getType())
                .setTitle(bizType.getName()).setDescription(StrUtil.format(bizType.getDescription(), point))
                .setPoint(point).setTotalPoint(totalPoint)
                .setStatus(MemberPointRecordStatusEnum.PENDING.getStatus());
        memberPointRecordMapper.insert(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void effectPendingPointRecord(Long userId, MemberPointBizTypeEnum bizType, String bizId) {
        MemberUserDO user = memberUserService.getUserForUpdate(userId);
        MemberPointRecordDO record = memberPointRecordMapper.selectByUserIdAndBizTypeAndBizId(
                userId, bizType.getType(), bizId);
        if (record == null || !MemberPointRecordStatusEnum.PENDING.getStatus().equals(record.getStatus())) {
            return;
        }
        if (memberPointRecordMapper.updateStatus(record.getId(), MemberPointRecordStatusEnum.PENDING.getStatus(),
                MemberPointRecordStatusEnum.EFFECTIVE.getStatus()) == 0) {
            return;
        }
        int point = ObjectUtil.defaultIfNull(record.getPoint(), 0);
        int totalPoint = ObjectUtil.defaultIfNull(user.getPoint(), 0) + point;
        if (!memberUserService.updateUserPoint(userId, point)) {
            throw exception(USER_POINT_NOT_ENOUGH);
        }
        memberPointRecordMapper.updateById(new MemberPointRecordDO().setId(record.getId())
                .setTotalPoint(totalPoint).setEffectiveTime(LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPendingPointRecord(Long userId, MemberPointBizTypeEnum bizType, String bizId) {
        memberUserService.getUserForUpdate(userId);
        MemberPointRecordDO record = memberPointRecordMapper.selectByUserIdAndBizTypeAndBizId(
                userId, bizType.getType(), bizId);
        if (record == null || !MemberPointRecordStatusEnum.PENDING.getStatus().equals(record.getStatus())) {
            return;
        }
        memberPointRecordMapper.updateStatus(record.getId(), MemberPointRecordStatusEnum.PENDING.getStatus(),
                MemberPointRecordStatusEnum.CANCELLED.getStatus());
    }

}
