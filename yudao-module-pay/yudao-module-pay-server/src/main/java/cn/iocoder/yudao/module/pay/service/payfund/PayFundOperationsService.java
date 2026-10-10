package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.api.fund.dto.*;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.*;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.*;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.service.refund.PayRefundService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.Objects;

/** 状态读取与带审计的人工操作，不拥有外部资金请求。 */
@Service
@Slf4j
public class PayFundOperationsService {
    @Resource private PayFundOrderMapper orderMapper;
    @Resource private PayFundDetailMapper detailMapper;
    @Resource private PayRefundFundDetailMapper refundDetailMapper;
    @Resource private PayRefundMapper refundMapper;
    @Resource private PayFundOperationMapper operationMapper;
    @Resource private PayFundService fundService;
    @Resource private PayRefundService refundService;

    public PayFundStatusRespDTO getStatus(Long payOrderId) {
        PayFundOrderDO order = orderMapper.selectByPayOrderId(payOrderId);
        if (order == null) return null;
        PayFundStatusRespDTO result = BeanUtils.toBean(order, PayFundStatusRespDTO.class);
        result.setDetails(BeanUtils.toBean(detailMapper.selectListByFundOrderId(order.getId()),
                PayFundStatusRespDTO.Detail.class));
        return result;
    }

    public PageResult<PayFundStatusRespDTO> getPage(PageParam page) {
        PageResult<PayFundOrderDO> rows = orderMapper.selectPage(page,
                new LambdaQueryWrapper<PayFundOrderDO>().orderByDesc(PayFundOrderDO::getId));
        return new PageResult<>(BeanUtils.toBean(rows.getList(), PayFundStatusRespDTO.class), rows.getTotal());
    }

    public PayRefundFundStatusRespDTO getRefundStatus(Long refundId) {
        var refund = refundMapper.selectById(refundId);
        if (refund == null) return null;
        PayRefundFundStatusRespDTO result = new PayRefundFundStatusRespDTO();
        result.setRefundId(refundId);
        result.setDetails(BeanUtils.toBean(refundDetailMapper.selectListByRefundNo(refund.getNo()),
                PayRefundFundStatusRespDTO.Detail.class));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryDivide(PayFundRetryReqDTO command) {
        orderMapper.lockById(command.getTargetId());
        PayFundOrderDO order = orderMapper.selectById(command.getTargetId());
        if (order == null) throw new IllegalArgumentException("资金主单不存在");
        record("DIVIDE_RETRY", command, String.valueOf(order.getDivideStatus()));
        fundService.prepareDivideRetry(command.getTargetId());
        afterCommit(() -> fundService.processDivide(command.getTargetId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryRefund(PayFundRetryReqDTO command) {
        var refund = refundMapper.selectById(command.getTargetId());
        if (refund == null) throw new IllegalArgumentException("退款单不存在");
        var rows = refundDetailMapper.selectListByRefundNo(refund.getNo());
        if (rows.isEmpty()) throw new IllegalArgumentException("退款资金任务不存在");
        orderMapper.lockById(rows.get(0).getFundOrderId());
        refund = refundMapper.selectById(command.getTargetId());
        if (refund == null) throw new IllegalArgumentException("退款单不存在");
        rows = refundDetailMapper.selectListByRefundNo(refund.getNo());
        record("REFUND_RETRY", command, JsonUtils.toJsonString(rows.stream()
                .map(row -> java.util.Map.of("id", row.getId(), "returnStatus",
                        Objects.toString(row.getDivideBackStatus(), "NONE"), "refundStatus",
                        Objects.toString(row.getRefundStatus(), "NONE"))).toList()));
        if (PayRefundStatusEnum.isSuccess(refund.getStatus())) return;
        if (PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(refund.getChannelCode())
                && PayRefundStatusEnum.isFailure(refund.getStatus())) {
            if (refund.getRequestSubmittedTime() == null && StrUtil.isBlank(refund.getChannelRefundNo())) {
                for (var row : rows) {
                    refundDetailMapper.update(null, new LambdaUpdateWrapper<PayRefundFundDetailDO>()
                            .eq(PayRefundFundDetailDO::getId, row.getId())
                            .and(wrapper -> wrapper.isNull(PayRefundFundDetailDO::getRefundStatus)
                                    .or().ne(PayRefundFundDetailDO::getRefundStatus,
                                            PayRefundFundDetailStatusEnum.SUCCESS.getStatus()))
                            .set(PayRefundFundDetailDO::getStatus, PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus())
                            .set(PayRefundFundDetailDO::getLastError, "退款缺少原请求提交凭证，需人工核实，禁止重新提交"));
                }
                return;
            }
            // 已提交的终态退款只恢复查询资格。保留原提交凭证与归还状态，绝不再次发起资金请求。
            int updated = refundMapper.updateByIdAndStatus(refund.getId(), PayRefundStatusEnum.FAILURE.getStatus(),
                    new PayRefundDO().setStatus(PayRefundStatusEnum.WAITING.getStatus()));
            if (updated == 1) afterCommit(() -> refundService.retryRefund(command.getTargetId()));
            return;
        }
        // 恢复为 PROCESSING 强制查询原归还号，绝不重新归还已成功资金。
        for (var row : rows) {
            if (Objects.equals(row.getDivideBackStatus(), PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus())) {
                refundDetailMapper.update(null, new LambdaUpdateWrapper<PayRefundFundDetailDO>()
                        .eq(PayRefundFundDetailDO::getId, row.getId())
                        .eq(PayRefundFundDetailDO::getDivideBackStatus, PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus())
                        .set(PayRefundFundDetailDO::getDivideBackStatus, PayRefundFundDetailStatusEnum.PROCESSING.getStatus())
                        .set(PayRefundFundDetailDO::getRetryCount, 0)
                        .set(PayRefundFundDetailDO::getNextRetryTime, LocalDateTime.now().minusSeconds(1)));
            }
        }
        afterCommit(() -> refundService.retryRefund(command.getTargetId()));
    }

    private void record(String action, PayFundRetryReqDTO command, String previousState) {
        if (command.getOperatorId() == null || command.getOperatorId() <= 0
                || command.getTargetId() == null || command.getTargetId() <= 0
                || command.getReason() == null || command.getReason().isBlank()
                || command.getReason().length() > 512
                || command.getExternalEvidence() == null || command.getExternalEvidence().isBlank()
                || command.getExternalEvidence().length() > 1024) {
            throw new IllegalArgumentException("人工资金操作必须记录操作人、原因和外部凭证");
        }
        PayFundOperationDO operation = BeanUtils.toBean(command, PayFundOperationDO.class);
        operation.setAction(action);
        operation.setPreviousState(previousState);
        operationMapper.insert(operation);
    }

    private static void afterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    task.run();
                } catch (Exception error) {
                    // 审计及任务已提交，定时任务继续推进。不得将提交后的网络错误误报为事务回滚。
                    log.warn("人工资金任务即时执行失败，等待定时补偿: {}", error.getClass().getSimpleName());
                }
            }
        });
    }
}
