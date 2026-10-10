package cn.iocoder.yudao.module.pay.dal.mysql.payfund;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundDetailDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PayFundDetailMapper extends BaseMapperX<PayFundDetailDO> {

    default PayFundDetailDO selectByNo(String no) {
        return selectOne(PayFundDetailDO::getNo, no);
    }

    default List<PayFundDetailDO> selectListByFundOrderId(Long fundOrderId) {
        return selectList(PayFundDetailDO::getFundOrderId, fundOrderId);
    }

    default List<PayFundDetailDO> selectListByPayOrderId(Long payOrderId) {
        return selectList(PayFundDetailDO::getPayOrderId, payOrderId);
    }

    default PayFundDetailDO selectByFundOrderIdAndAccountNo(Long fundOrderId, String accountNo) {
        return selectOne(new LambdaQueryWrapper<PayFundDetailDO>()
                .eq(PayFundDetailDO::getFundOrderId, fundOrderId)
                .eq(PayFundDetailDO::getAccountNo, accountNo));
    }

    default PayFundDetailDO selectByFundOrderIdAndTradeOrderSubId(Long fundOrderId, Long tradeOrderSubId) {
        return selectOne(new LambdaQueryWrapper<PayFundDetailDO>()
                .eq(PayFundDetailDO::getFundOrderId, fundOrderId)
                .eq(PayFundDetailDO::getTradeOrderSubId, tradeOrderSubId));
    }

    default PayFundDetailDO selectByFundOrderIdAndTradeOrderItemId(Long fundOrderId, Long tradeOrderItemId) {
        return selectOne(new LambdaQueryWrapper<PayFundDetailDO>()
                .eq(PayFundDetailDO::getFundOrderId, fundOrderId)
                .eq(PayFundDetailDO::getTradeOrderItemId, tradeOrderItemId));
    }

    default int incrementReturnedAmount(Long id, Integer amount) {
        if (id == null || amount == null || amount <= 0) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<PayFundDetailDO>()
                .eq(PayFundDetailDO::getId, id)
                .setSql("returned_amount = COALESCE(returned_amount, 0) + " + amount));
    }

    default int incrementRefundedAmount(Long id, Integer amount) {
        if (id == null || amount == null || amount <= 0) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<PayFundDetailDO>()
                .eq(PayFundDetailDO::getId, id)
                .setSql("refunded_amount = COALESCE(refunded_amount, 0) + " + amount));
    }
}
