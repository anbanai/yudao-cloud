package cn.iocoder.yudao.module.pay.dal.mysql.payfund;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayDivideRequestDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayDivideRequestStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;

@Mapper
public interface PayDivideRequestMapper extends BaseMapperX<PayDivideRequestDO> {

    default PayDivideRequestDO selectByNo(String no) {
        return selectOne(PayDivideRequestDO::getNo, no);
    }

    default PayDivideRequestDO selectByRequestNo(String requestNo) {
        return selectOne(PayDivideRequestDO::getRequestNo, requestNo);
    }

    default PayDivideRequestDO selectByFundOrderId(Long fundOrderId) {
        return selectOne(PayDivideRequestDO::getFundOrderId, fundOrderId);
    }

    default PayDivideRequestDO selectByFundDetailId(Long fundDetailId) {
        return selectOne(PayDivideRequestDO::getFundDetailId, fundDetailId);
    }

    default PayDivideRequestDO selectByMerchantRequestNo(Long appId, String merchantRequestNo) {
        return selectOne(PayDivideRequestDO::getAppId, appId,
                PayDivideRequestDO::getMerchantRequestNo, merchantRequestNo);
    }

    default PayDivideRequestDO selectByChannelRequestNo(String channelRequestNo) {
        return selectOne(PayDivideRequestDO::getChannelRequestNo, channelRequestNo);
    }

    default java.util.List<PayDivideRequestDO> selectListByStatuses(java.util.Collection<Integer> statuses) {
        return selectList(new LambdaQueryWrapper<PayDivideRequestDO>()
                .in(PayDivideRequestDO::getStatus, statuses)
                .orderByAsc(PayDivideRequestDO::getId));
    }

    /**
     * 条件抢占分账任务，避免多个回调/定时任务同时提交同一个易宝幂等号。
     */
    default boolean claimForProcessing(Long id, LocalDateTime now) {
        int updated = update(null, new LambdaUpdateWrapper<PayDivideRequestDO>()
                .eq(PayDivideRequestDO::getId, id)
                .in(PayDivideRequestDO::getStatus,
                        PayDivideRequestStatusEnum.PENDING.getStatus(),
                        PayDivideRequestStatusEnum.RETRYING.getStatus(),
                        PayDivideRequestStatusEnum.FAILED.getStatus())
                .and(wrapper -> wrapper.isNull(PayDivideRequestDO::getNextRetryTime)
                        .or().le(PayDivideRequestDO::getNextRetryTime, now))
                .set(PayDivideRequestDO::getStatus, PayDivideRequestStatusEnum.PROCESSING.getStatus())
                .set(PayDivideRequestDO::getNextRetryTime, null));
        return updated == 1;
    }
}
