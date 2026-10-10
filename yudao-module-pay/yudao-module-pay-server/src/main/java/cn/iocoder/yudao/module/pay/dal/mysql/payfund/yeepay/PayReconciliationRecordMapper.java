package cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayReconciliationRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

@Mapper
public interface PayReconciliationRecordMapper extends BaseMapperX<PayReconciliationRecordDO> {

    default PayReconciliationRecordDO selectByBatchNo(String batchNo) {
        return selectOne(PayReconciliationRecordDO::getBatchNo, batchNo);
    }

    default PayReconciliationRecordDO selectByMerchantAndDate(String merchantNo, LocalDate reconciliationDate) {
        return selectOne(new LambdaQueryWrapper<PayReconciliationRecordDO>()
                .eq(PayReconciliationRecordDO::getMerchantNo, merchantNo)
                .eq(PayReconciliationRecordDO::getReconciliationDate, reconciliationDate));
    }
}
