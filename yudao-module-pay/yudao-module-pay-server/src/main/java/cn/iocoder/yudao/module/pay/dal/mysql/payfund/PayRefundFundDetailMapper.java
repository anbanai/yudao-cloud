package cn.iocoder.yudao.module.pay.dal.mysql.payfund;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayRefundFundDetailDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface PayRefundFundDetailMapper extends BaseMapperX<PayRefundFundDetailDO> {

    default PayRefundFundDetailDO selectByNo(String no) {
        return selectOne(PayRefundFundDetailDO::getNo, no);
    }

    default PayRefundFundDetailDO selectByRefundId(Long refundId) {
        return selectOne(PayRefundFundDetailDO::getRefundId, refundId);
    }

    default List<PayRefundFundDetailDO> selectListByFundOrderId(Long fundOrderId) {
        return selectList(PayRefundFundDetailDO::getFundOrderId, fundOrderId);
    }

    default List<PayRefundFundDetailDO> selectListByRefundNo(String refundNo) {
        return selectList(PayRefundFundDetailDO::getRefundNo, refundNo);
    }

    default List<PayRefundFundDetailDO> selectListByDivideBackRequestId(String requestId) {
        return selectList(PayRefundFundDetailDO::getDivideBackRequestId, requestId);
    }

    default PayRefundFundDetailDO selectByRefundIdAndFundDetailId(Long refundId, Long fundDetailId) {
        return selectOne(new LambdaQueryWrapper<PayRefundFundDetailDO>()
                .eq(PayRefundFundDetailDO::getRefundId, refundId)
                .eq(PayRefundFundDetailDO::getFundDetailId, fundDetailId));
    }

    default List<PayRefundFundDetailDO> selectListByFundDetailId(Long fundDetailId) {
        return selectList(PayRefundFundDetailDO::getFundDetailId, fundDetailId);
    }

    /**
     * 条件更新资金归还状态，成功状态一旦落库不可被重复回调降级。
     *
     * <p>返回影响行数供调用方决定是否累计原分账明细的已归还金额，避免并发成功通知重复累计。</p>
     */
    default int updateDivideBackStatusIfNotSuccess(PayRefundFundDetailDO detail) {
        if (detail == null || detail.getId() == null || detail.getDivideBackStatus() == null) {
            return 0;
        }
        boolean reservedReturn = StrUtil.isNotBlank(detail.getDivideDetailNo())
                && !PayRefundFundDetailStatusEnum.isPending(detail.getDivideBackStatus())
                && !PayRefundFundDetailStatusEnum.isFailed(detail.getDivideBackStatus())
                && !PayRefundFundDetailStatusEnum.isClosed(detail.getDivideBackStatus());
        String manualError = PayRefundFundDetailStatusEnum.isSuccess(detail.getDivideBackStatus())
                ? "商家资金已归还，但用户退款失败，需人工核实原退款请求"
                : "商家资金归还结果尚未确认，用户退款失败，需人工核实原归还和退款请求";
        return update(null, new LambdaUpdateWrapper<PayRefundFundDetailDO>()
                .eq(PayRefundFundDetailDO::getId, detail.getId())
                .set(PayRefundFundDetailDO::getDivideBackStatus, detail.getDivideBackStatus())
                .set(PayRefundFundDetailDO::getDivideBackSuccessTime, detail.getDivideBackSuccessTime())
                // 归还结果可能持有旧退款状态，必须以数据库当前值保住成功和人工处理状态。
                .setSql(reservedReturn, "status = CASE WHEN refund_status = {0} THEN {0} "
                                + "WHEN refund_status = {1} THEN {2} ELSE {3} END",
                        PayRefundFundDetailStatusEnum.SUCCESS.getStatus(), PayRefundFundDetailStatusEnum.FAILED.getStatus(),
                        PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus(), detail.getStatus())
                .setSql(!reservedReturn, "status = CASE WHEN refund_status = {0} THEN {0} ELSE {1} END",
                        PayRefundFundDetailStatusEnum.SUCCESS.getStatus(), detail.getStatus())
                .set(PayRefundFundDetailDO::getChannelNotifyData, detail.getChannelNotifyData())
                .setSql(reservedReturn, "last_error = CASE WHEN refund_status = {0} THEN NULL "
                                + "WHEN refund_status = {1} THEN {2} ELSE {3} END",
                        PayRefundFundDetailStatusEnum.SUCCESS.getStatus(), PayRefundFundDetailStatusEnum.FAILED.getStatus(),
                        manualError, detail.getLastError())
                .setSql(!reservedReturn, "last_error = CASE WHEN refund_status = {0} THEN NULL ELSE {1} END",
                        PayRefundFundDetailStatusEnum.SUCCESS.getStatus(), detail.getLastError())
                .and(wrapper -> wrapper.isNull(PayRefundFundDetailDO::getDivideBackStatus)
                        .or().ne(PayRefundFundDetailDO::getDivideBackStatus,
                                PayRefundFundDetailStatusEnum.SUCCESS.getStatus())));
    }

    /**
     * 条件更新退款状态，只有第一个成功回调可以领取累计退款金额。
     * 重复回调或并发查询不会再次把金额累加到原分账明细。
     */
    default int updateRefundStatusIfNotSuccess(PayRefundFundDetailDO detail) {
        if (detail == null || detail.getId() == null || detail.getRefundStatus() == null) {
            return 0;
        }
        boolean failure = PayRefundFundDetailStatusEnum.isFailed(detail.getRefundStatus());
        String reservedReturnSql = "NULLIF(TRIM(divide_detail_no), '') IS NOT NULL "
                + "AND (divide_back_status IS NULL OR divide_back_status NOT IN ({0}, {1}, {2}))";
        return update(null, new LambdaUpdateWrapper<PayRefundFundDetailDO>()
                .eq(PayRefundFundDetailDO::getId, detail.getId())
                .set(PayRefundFundDetailDO::getRefundStatus, detail.getRefundStatus())
                // 退款回调也可能先读到旧归还状态；失败时以当前数据库归还证据决定人工状态。
                .set(!failure, PayRefundFundDetailDO::getStatus, detail.getStatus())
                .setSql(failure, "status = CASE WHEN " + reservedReturnSql + " THEN {3} ELSE {4} END",
                        PayRefundFundDetailStatusEnum.PENDING.getStatus(), PayRefundFundDetailStatusEnum.FAILED.getStatus(),
                        PayRefundFundDetailStatusEnum.CLOSED.getStatus(), PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus(),
                        PayRefundFundDetailStatusEnum.FAILED.getStatus())
                .set(PayRefundFundDetailDO::getChannelRefundNo, detail.getChannelRefundNo())
                .set(PayRefundFundDetailDO::getChannelNotifyData, detail.getChannelNotifyData())
                .set(PayRefundFundDetailDO::getChannelErrorCode, detail.getChannelErrorCode())
                .set(PayRefundFundDetailDO::getChannelErrorMsg, detail.getChannelErrorMsg())
                .set(!failure, PayRefundFundDetailDO::getLastError, detail.getLastError())
                .setSql(failure, "last_error = CASE WHEN " + reservedReturnSql
                                + " THEN CASE WHEN divide_back_status = {3} THEN {4} ELSE {5} END ELSE NULL END",
                        PayRefundFundDetailStatusEnum.PENDING.getStatus(), PayRefundFundDetailStatusEnum.FAILED.getStatus(),
                        PayRefundFundDetailStatusEnum.CLOSED.getStatus(), PayRefundFundDetailStatusEnum.SUCCESS.getStatus(),
                        "商家资金已归还，但用户退款失败，需人工核实原退款请求",
                        "商家资金归还结果尚未确认，用户退款失败，需人工核实原归还和退款请求")
                .set(PayRefundFundDetailDO::getSuccessTime, detail.getSuccessTime())
                .and(wrapper -> wrapper.isNull(PayRefundFundDetailDO::getRefundStatus)
                        .or().ne(PayRefundFundDetailDO::getRefundStatus,
                                PayRefundFundDetailStatusEnum.SUCCESS.getStatus())));
    }
}
