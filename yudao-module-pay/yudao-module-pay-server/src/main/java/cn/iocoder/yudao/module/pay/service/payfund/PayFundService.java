package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderDividePlanDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantAccountRespDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantRegisterReqDTO;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;

import java.util.List;
import java.util.Map;

/** 易宝支付资金域服务。 */
public interface PayFundService {

    /** 支付前校验并持久化不可变分账快照。 */
    void preparePayment(PayOrderDO order, PayChannelDO channel);

    /**
     * 支付前校验并持久化不可变分账快照，同时明确保存本次渠道请求使用的商户订单号。
     * 易宝的 orderId 对应 PayOrderExtensionDO.no，而不是支付主单在支付成功后才回填的 PayOrderDO.no。
     */
    void preparePayment(PayOrderDO order, PayChannelDO channel, String payOrderNo);

    /**
     * 从支付前已经持久化的资金快照生成易宝聚合预支付所需的 divideDetail JSON。
     * 该方法不得重新读取交易商品或当前商家资料计算金额。
     */
    String buildPrePayDivideDetail(PayOrderDO order);

    /** 支付成功后更新资金主单，分账由异步任务推进。 */
    void onPaymentSuccess(PayOrderDO order, PayOrderRespDTO notify);

    /** 执行指定资金单的分账。 */
    void processDivide(Long fundOrderId);

    /** 扫描并处理可重试的分账请求。 */
    int processPendingDivides();

    /** 扫描回调业务处理失败的资金事件并异步重放。 */
    int processFailedYeepayEvents();

    /** 在事务内校验并保存退款资金快照和稳定归还任务，不调用外部接口。 */
    List<PayRefundFundAllocationDTO> prepareRefund(PayRefundDO refund, PayOrderDO order,
                                                    PayChannelDO channel,
                                                    List<PayRefundFundAllocationDTO> allocations);

    /** 在准备事务提交后执行或查询原资金归还任务；所有归还成功时返回 true。 */
    boolean executeRefundPreparation(Long refundId);

    /**
     * 资金归还完成后才允许真正发起渠道退款；未完成时退款单保持等待状态。
     */
    boolean isRefundReady(Long refundId);

    /** 退款结果同步到资金明细。 */
    void onRefundResult(PayRefundDO refund, PayRefundRespDTO notify);

    /** 处理易宝清算、分账、资金归还回调。 */
    /**
     * 校验支付回调的资金主单；返回 false 表示只保留事件审计，不更新本地业务状态。
     */
    boolean validatePaymentNotify(Map<String, Object> payload);

    /**
     * 校验退款回调的退款资金明细；返回 false 表示只保留事件审计，不更新本地业务状态。
     */
    boolean validateRefundNotify(Map<String, Object> payload);

    void handleClearing(Map<String, Object> payload);

    void handleDivide(Map<String, Object> payload);

    void handleDivideBack(Map<String, Object> payload);

    /** 处理易宝商户入网结果回调。 */
    void handleMerchantOnboarding(Map<String, Object> payload);

    /** 保存并去重易宝事件；返回 false 表示此前已持久化，可直接 ACK。 */
    boolean acceptYeepayEvent(String eventType, Map<String, Object> payload);

    /** 验签后同步持久化加密原文和事件，再允许回调入口 ACK。 */
    boolean acceptYeepayEvent(String eventType, Map<String, Object> payload, String originalEnvelope);

    /** 标记异步事件已完成，供回调入口在业务状态机成功后写入审计状态。 */
    void markYeepayEventProcessed(String eventType, Map<String, Object> payload);

    /** 标记异步事件处理失败；失败原因留在事件表供重试/人工排查。 */
    void markYeepayEventFailed(String eventType, Map<String, Object> payload, Throwable error);

    /** 人工重试入口。 */
    void retryDivide(Long fundOrderId);

    /** 在审计事务内保存人工重试意图；执行阶段只查询原分账号。 */
    void prepareDivideRetry(Long fundOrderId);

    /** 供单元测试和管理入口使用的纯金额/商家数校验。 */
    List<PayOrderDividePlanDTO> validateDividePlan(Integer orderPrice,
                                                   List<PayOrderDividePlanDTO> plans,
                                                   int maxRecipients);

    PayFundOrderDO getFundOrderByPayOrderId(Long payOrderId);

    PayFundOrderDO getFundOrder(Long fundOrderId);

    PayFundMerchantAccountRespDTO registerMerchant(PayFundMerchantRegisterReqDTO reqDTO);

    PayFundMerchantAccountRespDTO getMerchant(String appKey, Long merchantId);

}
