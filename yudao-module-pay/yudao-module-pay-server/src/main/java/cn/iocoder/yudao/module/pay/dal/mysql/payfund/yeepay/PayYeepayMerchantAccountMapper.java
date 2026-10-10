package cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayMerchantAccountDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface PayYeepayMerchantAccountMapper extends BaseMapperX<PayYeepayMerchantAccountDO> {

    default PayYeepayMerchantAccountDO selectByMerchantNo(String merchantNo) {
        return selectOne(PayYeepayMerchantAccountDO::getMerchantNo, merchantNo);
    }

    default PayYeepayMerchantAccountDO selectByAppIdAndMerchantNo(Long appId, String merchantNo) {
        return selectOne(PayYeepayMerchantAccountDO::getAppId, appId,
                PayYeepayMerchantAccountDO::getMerchantNo, merchantNo);
    }

    default PayYeepayMerchantAccountDO selectByAccountNo(String accountNo) {
        return selectOne(PayYeepayMerchantAccountDO::getAccountNo, accountNo);
    }

    default PayYeepayMerchantAccountDO selectByAppIdAndMerchantId(Long appId, Long merchantId) {
        return selectOne(new LambdaQueryWrapper<PayYeepayMerchantAccountDO>()
                .eq(PayYeepayMerchantAccountDO::getAppId, appId)
                .eq(PayYeepayMerchantAccountDO::getMerchantId, merchantId)
                .orderByDesc(PayYeepayMerchantAccountDO::getId)
                .last("limit 1"));
    }

    default PayYeepayMerchantAccountDO selectByAppIdAndMerchantIdForUpdate(Long appId, Long merchantId) {
        return selectOne(new LambdaQueryWrapper<PayYeepayMerchantAccountDO>()
                .eq(PayYeepayMerchantAccountDO::getAppId, appId)
                .eq(PayYeepayMerchantAccountDO::getMerchantId, merchantId).last("LIMIT 1 FOR UPDATE"));
    }

    default PayYeepayMerchantAccountDO selectByRequestNo(String requestNo) {
        return selectOne(PayYeepayMerchantAccountDO::getRequestNo, requestNo);
    }

    default PayYeepayMerchantAccountDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapper<PayYeepayMerchantAccountDO>()
                .eq(PayYeepayMerchantAccountDO::getId, id).last("FOR UPDATE"));
    }

    default void clearSubmission(Long id, String token) {
        update(null, new LambdaUpdateWrapper<PayYeepayMerchantAccountDO>()
                .eq(PayYeepayMerchantAccountDO::getId, id)
                .eq(PayYeepayMerchantAccountDO::getSubmissionToken, token)
                .set(PayYeepayMerchantAccountDO::getSubmissionToken, null)
                .set(PayYeepayMerchantAccountDO::getSubmissionExpireTime, null));
    }
}
