package cn.iocoder.yudao.module.pay.service.refund;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import cn.iocoder.yudao.module.pay.controller.admin.refund.vo.PayRefundExportReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.refund.vo.PayRefundPageReqVO;
import cn.iocoder.yudao.module.pay.convert.refund.PayRefundConvert;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.enums.notify.PayNotifyTypeEnum;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.config.PayProperties;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.notify.PayNotifyService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;
import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray;
import static cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants.*;

/**
 * 退款订单 Service 实现类
 *
 * @author jason
 */
@Service
@Slf4j
@Validated
public class PayRefundServiceImpl implements PayRefundService {

    @Resource
    private PayProperties payProperties;

    @Resource
    private PayRefundMapper refundMapper;
    @Resource
    private PayNoRedisDAO noRedisDAO;

    @Resource
    private PayOrderService orderService;
    @Resource
    private PayAppService appService;
    @Resource
    private PayChannelService channelService;
    @Resource
    private PayNotifyService notifyService;

    /** 资金域可选注入，保证普通渠道退款和独立测试不受影响。 */
    @Autowired(required = false)
    private ObjectProvider<PayFundService> payFundServiceProvider;

    @Override
    public PayRefundDO getRefund(Long id) {
        return refundMapper.selectById(id);
    }

    @Override
    public PayRefundDO getRefundByNo(String no) {
        return refundMapper.selectByNo(no);
    }

    @Override
    public Long getRefundCountByAppId(Long appId) {
        return refundMapper.selectCountByAppId(appId);
    }

    @Override
    public PageResult<PayRefundDO> getRefundPage(PayRefundPageReqVO pageReqVO) {
        return refundMapper.selectPage(pageReqVO);
    }

    @Override
    public List<PayRefundDO> getRefundList(PayRefundExportReqVO exportReqVO) {
        return refundMapper.selectList(exportReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRefund(PayRefundCreateReqDTO reqDTO) {
        // 1.1 校验 App
        PayAppDO app = appService.validPayApp(reqDTO.getAppKey());
        // 1.2 校验支付订单
        PayOrderDO order = validatePayOrderCanRefund(reqDTO, app.getId());
        // 1.3 校验支付渠道是否有效
        PayChannelDO channel = channelService.validExistingPayChannel(order.getChannelId());
        PayClient<?> client = channelService.getPayClient(channel.getId());
        if (client == null) {
            log.error("[refund][渠道编号({}) 找不到对应的支付客户端]", channel.getId());
            throw exception(CHANNEL_NOT_FOUND);
        }
        // 1.4 校验退款订单是否已经存在
        PayRefundDO refund = refundMapper.selectByAppIdAndMerchantRefundId(
                app.getId(), reqDTO.getMerchantRefundId());
        if (refund != null) {
            throw exception(REFUND_EXISTS);
        }

        // 2.1 插入退款单
        String no = noRedisDAO.generate(payProperties.getRefundNoPrefix());
        refund = PayRefundConvert.INSTANCE.convert(reqDTO)
                .setNo(no).setAppId(app.getId()).setOrderId(order.getId()).setOrderNo(order.getNo())
                .setChannelId(order.getChannelId()).setChannelCode(order.getChannelCode())
                // 商户相关的字段
                .setNotifyUrl(app.getRefundNotifyUrl())
                // 渠道相关字段
                .setChannelOrderNo(order.getChannelOrderNo())
                // 退款相关字段
                .setStatus(PayRefundStatusEnum.WAITING.getStatus())
                .setPayPrice(order.getPrice()).setRefundPrice(reqDTO.getPrice());
        refundMapper.insert(refund);

        PayFundService payFundService = getPayFundService();
        if (payFundService != null && PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(channel.getCode())) {
            List<PayRefundFundAllocationDTO> resolvedFundAllocation =
                    payFundService.prepareRefund(refund, order, channel, reqDTO.getFundAllocation());
            refundMapper.updateById(new PayRefundDO().setId(refund.getId())
                    .setFundAllocationData(toJsonString(resolvedFundAllocation)));
        }
        Long refundId = refund.getId();
        // 外层业务可能还有事务；必须等待最终提交，回滚时不产生渠道资金操作。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                TenantUtils.execute(channel.getTenantId(), () -> getSelf().submitRefundAfterCommit(refundId));
            }
        });
        return refundId;
    }

    /** 提交后暂停原事务资源，资金归还和退款申请均在稳定记录提交后执行。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submitRefundAfterCommit(Long refundId) {
        PayRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null || !PayRefundStatusEnum.WAITING.getStatus().equals(refund.getStatus())) {
            return;
        }
        if (PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(refund.getChannelCode())) {
            syncRefund(refund);
            return;
        }
        try {
            PayChannelDO channel = channelService.validExistingPayChannel(refund.getChannelId());
            PayClient<?> client = channelService.getPayClient(channel.getId());
            getSelf().notifyRefund(channel, client.unifiedRefund(buildRefundRequest(refund, channel)));
        } catch (Throwable e) {
            log.error("[submitRefundAfterCommit][退款 id({}) 请求结果待查询，异常类型({})]",
                    refundId, e.getClass().getSimpleName());
        }
    }

    /** 原退款号只能获得一次自动提交许可，许可提交后发生崩溃也不能换号重发。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markRefundSubmitted(Long refundId) {
        return refundMapper.markRequestSubmitted(refundId, PayRefundStatusEnum.WAITING.getStatus(),
                LocalDateTime.now()) == 1;
    }

    private PayRefundUnifiedReqDTO buildRefundRequest(PayRefundDO refund, PayChannelDO channel) {
        PayRefundUnifiedReqDTO request = new PayRefundUnifiedReqDTO().setPayPrice(refund.getPayPrice()).setRefundPrice(refund.getRefundPrice())
                .setOutTradeNo(refund.getOrderNo()).setUniqueOrderNo(refund.getChannelOrderNo())
                .setOutRefundNo(refund.getNo()).setNotifyUrl(genChannelRefundNotifyUrl(channel))
                .setReason(refund.getReason()).setFundAllocation(parseArray(refund.getFundAllocationData(),
                        PayRefundFundAllocationDTO.class));
        if (PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(refund.getChannelCode())) {
            PayFundOrderDO fundOrder = requireRefundFundOrder(refund);
            request.setParentMerchantNo(fundOrder.getParentMerchantNo())
                    .setCollectionMerchantNo(fundOrder.getCollectionMerchantNo());
        }
        return request;
    }

    private PayFundOrderDO requireRefundFundOrder(PayRefundDO refund) {
        PayFundService service = getPayFundService();
        PayFundOrderDO fundOrder = service == null ? null : service.getFundOrderByPayOrderId(refund.getOrderId());
        if (fundOrder == null || !Objects.equals(fundOrder.getChannelId(), refund.getChannelId())
                || StrUtil.isBlank(fundOrder.getParentMerchantNo()) || StrUtil.isBlank(fundOrder.getCollectionMerchantNo())) {
            throw new IllegalStateException("易宝退款缺少原支付渠道及商编快照，需人工核实");
        }
        return fundOrder;
    }

    /**
     * 校验支付订单是否可以退款
     *
     * @param reqDTO 退款申请信息
     * @return 支付订单
     */
    private PayOrderDO validatePayOrderCanRefund(PayRefundCreateReqDTO reqDTO, Long appId) {
        PayOrderDO order = orderService.getOrder(appId, reqDTO.getMerchantOrderId());
        if (order == null) {
            throw exception(PAY_ORDER_NOT_FOUND);
        }
        // 校验状态，必须是已支付、或者已退款
        if (!PayOrderStatusEnum.isSuccessOrRefund(order.getStatus())) {
            throw exception(PAY_ORDER_REFUND_FAIL_STATUS_ERROR);
        }

        // 校验金额，退款金额不能大于原定的金额
        if ((long) reqDTO.getPrice() + order.getRefundPrice() > order.getPrice()) {
            throw exception(REFUND_PRICE_EXCEED);
        }
        // 是否有退款中的订单
        if (refundMapper.selectCountByAppIdAndOrderId(appId, order.getId(),
                PayRefundStatusEnum.WAITING.getStatus()) > 0) {
            throw exception(REFUND_HAS_REFUNDING);
        }
        return order;
    }

    /**
     * 根据支付渠道的编码，生成支付渠道的回调地址
     *
     * @param channel 支付渠道
     * @return 支付渠道的回调地址  配置地址 + "/" + channel id
     */
    private String genChannelRefundNotifyUrl(PayChannelDO channel) {
        return payProperties.getRefundNotifyUrl() + "/" + channel.getId();
    }

    @Override
    public void notifyRefund(Long channelId, PayRefundRespDTO notify) {
        // 校验支付渠道是否有效
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        // 更新退款订单
        TenantUtils.execute(channel.getTenantId(), () -> getSelf().notifyRefund(channel, notify));
    }

    /**
     * 通知并更新订单的退款结果
     *
     * @param channel 支付渠道
     * @param notify  通知
     */
    // 注意，如果是方法内调用该方法，需要通过 getSelf().notifyRefund(channel, notify) 调用，否则事务不生效
    @Transactional(rollbackFor = Exception.class)
    public void notifyRefund(PayChannelDO channel, PayRefundRespDTO notify) {
        if (PayRefundStatusEnum.WAITING.getStatus().equals(notify.getStatus())) {
            PayRefundDO refund = refundMapper.selectByAppIdAndNo(channel.getAppId(), notify.getOutRefundNo());
            if (refund == null || !Objects.equals(channel.getId(), refund.getChannelId())) {
                throw exception(REFUND_NOT_FOUND);
            }
            if (StrUtil.isNotBlank(notify.getChannelRefundNo())) {
                refundMapper.updateByIdAndStatus(refund.getId(), PayRefundStatusEnum.WAITING.getStatus(),
                        new PayRefundDO().setChannelRefundNo(notify.getChannelRefundNo()));
            }
            return;
        }
        // 情况一：退款成功
        if (PayRefundStatusEnum.isSuccess(notify.getStatus())) {
            notifyRefundSuccess(channel, notify);
            return;
        }
        // 情况二：退款失败
        if (PayRefundStatusEnum.isFailure(notify.getStatus())) {
            notifyRefundFailure(channel, notify);
        }
    }

    private void notifyRefundSuccess(PayChannelDO channel, PayRefundRespDTO notify) {
        // 1.1 查询 PayRefundDO
        PayRefundDO refund = refundMapper.selectByAppIdAndNo(
                channel.getAppId(), notify.getOutRefundNo());
        if (refund == null || !Objects.equals(channel.getId(), refund.getChannelId())) {
            throw exception(REFUND_NOT_FOUND);
        }
        if (PayRefundStatusEnum.isSuccess(refund.getStatus())) { // 如果已经是成功，直接返回，不用重复更新
            log.info("[notifyRefundSuccess][退款订单({}) 已经是退款成功，无需更新]", refund.getId());
            return;
        }
        if (!PayRefundStatusEnum.WAITING.getStatus().equals(refund.getStatus())) {
            throw exception(REFUND_STATUS_IS_NOT_WAITING);
        }
        // 1.2 更新 PayRefundDO
        PayRefundDO updateRefundObj = new PayRefundDO()
                .setSuccessTime(notify.getSuccessTime())
                .setChannelRefundNo(notify.getChannelRefundNo())
                .setStatus(PayRefundStatusEnum.SUCCESS.getStatus())
                .setChannelNotifyData(toJsonString(notify));
        int updateCounts = refundMapper.updateByIdAndStatus(refund.getId(), refund.getStatus(), updateRefundObj);
        if (updateCounts == 0) { // 校验状态，必须是等待状态
            throw exception(REFUND_STATUS_IS_NOT_WAITING);
        }
        log.info("[notifyRefundSuccess][退款订单({}) 更新为退款成功]", refund.getId());

        // 2. 更新订单
        orderService.updateOrderRefundPrice(refund.getOrderId(), refund.getRefundPrice());

        PayFundService payFundService = getPayFundService();
        if (payFundService != null) {
            payFundService.onRefundResult(refund, notify);
        }

        // 3. 插入退款通知记录
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.REFUND.getType(),
                refund.getId());
    }

    private void notifyRefundFailure(PayChannelDO channel, PayRefundRespDTO notify) {
        // 1.1 查询 PayRefundDO
        PayRefundDO refund = refundMapper.selectByAppIdAndNo(
                channel.getAppId(), notify.getOutRefundNo());
        if (refund == null || !Objects.equals(channel.getId(), refund.getChannelId())) {
            throw exception(REFUND_NOT_FOUND);
        }
        if (PayRefundStatusEnum.isFailure(refund.getStatus())) { // 如果已经是成功，直接返回，不用重复更新
            log.info("[notifyRefundSuccess][退款订单({}) 已经是退款关闭，无需更新]", refund.getId());
            return;
        }
        if (!PayRefundStatusEnum.WAITING.getStatus().equals(refund.getStatus())) {
            throw exception(REFUND_STATUS_IS_NOT_WAITING);
        }
        // 1.2 更新 PayRefundDO
        PayRefundDO updateRefundObj = new PayRefundDO()
                .setChannelRefundNo(notify.getChannelRefundNo())
                .setStatus(PayRefundStatusEnum.FAILURE.getStatus())
                .setChannelNotifyData(toJsonString(notify))
                .setChannelErrorCode(notify.getChannelErrorCode()).setChannelErrorMsg(notify.getChannelErrorMsg());
        int updateCounts = refundMapper.updateByIdAndStatus(refund.getId(), refund.getStatus(), updateRefundObj);
        if (updateCounts == 0) { // 校验状态，必须是等待状态
            throw exception(REFUND_STATUS_IS_NOT_WAITING);
        }
        log.info("[notifyRefundFailure][退款订单({}) 更新为退款失败]", refund.getId());

        PayFundService payFundService = getPayFundService();
        if (payFundService != null) {
            payFundService.onRefundResult(refund, notify);
        }

        // 2. 插入退款通知记录
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.REFUND.getType(),
                refund.getId());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int syncRefund() {
        // 1. 查询指定创建时间内的待退款订单
        List<PayRefundDO> refunds = refundMapper.selectListByStatus(PayRefundStatusEnum.WAITING.getStatus());
        if (CollUtil.isEmpty(refunds)) {
            return 0;
        }
        // 2. 遍历执行
        int count = 0;
        for (PayRefundDO refund : refunds) {
            count += syncRefund(refund) ? 1 : 0;
        }
        return count;
    }

    /**
     * 同步单个退款订单
     *
     * @param refund 退款订单
     * @return 是否同步到
     */
    private boolean syncRefund(PayRefundDO refund) {
        try {
            PayChannelDO channel = channelService.validExistingPayChannel(refund.getChannelId());
            PayClient<?> payClient = channelService.getPayClient(refund.getChannelId());
            if (payClient == null) {
                log.error("[syncRefund][渠道编号({}) 找不到对应的支付客户端]", refund.getChannelId());
                return false;
            }
            PayFundService payFundService = getPayFundService();
            if (PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(refund.getChannelCode())
                    && refund.getRequestSubmittedTime() == null && StrUtil.isBlank(refund.getChannelRefundNo())) {
                // 归还资金也必须使用原支付快照，缺少快照时不能先向当前渠道商编发起资金操作。
                requireRefundFundOrder(refund);
                if (payFundService != null && (!payFundService.executeRefundPreparation(refund.getId())
                        || !payFundService.isRefundReady(refund.getId()))) {
                    return false;
                }
                refund = refundMapper.selectById(refund.getId());
                PayRefundUnifiedReqDTO request = buildRefundRequest(refund, channel);
                if (!getSelf().markRefundSubmitted(refund.getId())) {
                    return false;
                }
                PayRefundRespDTO response = payClient.unifiedRefund(request);
                getSelf().notifyRefund(channel, response);
                return PayRefundStatusEnum.isSuccess(response.getStatus())
                        || PayRefundStatusEnum.isFailure(response.getStatus());
            }
            // 1.1 查询退款订单信息
            PayRefundRespDTO respDTO;
            if (PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(refund.getChannelCode())) {
                if (!(payClient instanceof YeepayPayClient yeepayClient)) {
                    throw new IllegalStateException("易宝退款渠道客户端不匹配");
                }
                PayFundOrderDO fundOrder = requireRefundFundOrder(refund);
                respDTO = yeepayClient.getRefund(refund.getOrderNo(), refund.getNo(),
                        fundOrder.getParentMerchantNo(), fundOrder.getCollectionMerchantNo(), refund.getRefundPrice());
            } else {
                respDTO = payClient.getRefund(refund.getOrderNo(), refund.getNo());
            }
            // 1.2 回调退款结果
            notifyRefund(refund.getChannelId(), respDTO);

            // 2. 如果同步到，则返回 true
            return PayRefundStatusEnum.isSuccess(respDTO.getStatus())
                    || PayRefundStatusEnum.isFailure(respDTO.getStatus());
        } catch (Throwable e) {
            log.error("[syncRefund][refund({}) 同步退款状态异常，异常类型({})]", refund.getId(), e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean retryRefund(Long refundId) {
        PayRefundDO refund = refundMapper.selectById(refundId);
        return refund != null && syncRefund(refund);
    }

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     *
     * @return 自己
     */
    private PayRefundServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

    private PayFundService getPayFundService() {
        return payFundServiceProvider == null ? null : payFundServiceProvider.getIfAvailable();
    }

}
