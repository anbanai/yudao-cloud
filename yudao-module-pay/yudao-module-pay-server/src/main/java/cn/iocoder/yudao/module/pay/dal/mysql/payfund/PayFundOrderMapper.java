package cn.iocoder.yudao.module.pay.dal.mysql.payfund;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PayFundOrderMapper extends BaseMapperX<PayFundOrderDO> {

    default List<String> selectHistoricalParentMerchantNos(Long channelId) {
        return selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayFundOrderDO>()
                .select("DISTINCT parent_merchant_no").eq("channel_id", channelId).isNotNull("parent_merchant_no"))
                .stream().map(row -> String.valueOf(row.values().iterator().next())).toList();
    }

    /** 在事务中获取跨进程资金主单行锁，兼容项目数据库的 UPDATE 语法。 */
    default int lockById(Long id) {
        return update(null, new LambdaUpdateWrapper<PayFundOrderDO>()
                .eq(PayFundOrderDO::getId, id).setSql("id = id"));
    }

    default PayFundOrderDO selectByNo(String no) {
        return selectOne(PayFundOrderDO::getNo, no);
    }

    default PayFundOrderDO selectByPayOrderId(Long payOrderId) {
        return selectOne(PayFundOrderDO::getPayOrderId, payOrderId);
    }

    default PayFundOrderDO selectByPayOrderNo(String payOrderNo) {
        return selectOne(PayFundOrderDO::getPayOrderNo, payOrderNo);
    }

    default PayFundOrderDO selectByMerchantOrderId(Long appId, String merchantOrderId) {
        return selectOne(PayFundOrderDO::getAppId, appId,
                PayFundOrderDO::getMerchantOrderId, merchantOrderId);
    }

    default PayFundOrderDO selectByChannelOrderNo(String channelOrderNo) {
        return selectOne(PayFundOrderDO::getChannelOrderNo, channelOrderNo);
    }

    default List<PayFundOrderDO> selectListPendingDivide() {
        return selectList(new LambdaQueryWrapper<PayFundOrderDO>()
                .in(PayFundOrderDO::getStatus,
                        PayFundOrderStatusEnum.PENDING.getStatus(),
                        PayFundOrderStatusEnum.PROCESSING.getStatus(),
                        PayFundOrderStatusEnum.RETRYING.getStatus())
                .orderByAsc(PayFundOrderDO::getId));
    }

    /**
     * 原子累计已退款金额，避免多个商家售后回调并发时发生先读后写覆盖。
     */
    default int incrementRefundPrice(Long id, Integer amount) {
        if (id == null || amount == null || amount <= 0) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<PayFundOrderDO>()
                .eq(PayFundOrderDO::getId, id)
                .setSql("refund_price = COALESCE(refund_price, 0) + " + amount));
    }
}
