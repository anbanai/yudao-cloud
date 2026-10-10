package cn.iocoder.yudao.module.pay.service.payfund;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderDividePlanDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantAccountRespDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantRegisterReqDTO;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayDivideRequestDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayRefundFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayMerchantAccountDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayEventDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayDivideRequestMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayRefundFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayYeepayMerchantAccountMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayYeepayEventMapper;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayDivideRequestStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayMerchantAccountStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayOnboardingStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayEventStatusEnum;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;
import static cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants.PAY_ORDER_DIVIDE_PLAN_INVALID;
import static cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants.PAY_ORDER_DIVIDE_PLAN_MERCHANT_NOT_READY;

/** 易宝多商家收款、分账和退款资金服务。 */
@Service
@Validated
@Slf4j
public class PayFundServiceImpl implements PayFundService {

    private static final int DEFAULT_MAX_RECIPIENTS = 5;
    private static final int MAX_AUTO_RETRY = 3;
    private static final int MERCHANT_SUBMISSION_LEASE_SECONDS = 300;
    private static final String FUND_NO_PREFIX = "F";
    private static final String DIVIDE_NO_PREFIX = "D";
    private static final String REFUND_FUND_NO_PREFIX = "RF";
    private static final Set<String> EVENT_IDENTIFIER_KEYS = Set.of(
            "orderId", "outTradeNo", "requestId", "tradeNo", "tradeOrderNo",
            "refundRequestId", "refundOrderId", "uniqueRefundNo", "refundNo",
            "divideRequestId", "divideBackRequestId", "divideNo", "divideBackNo",
            "divideDetailNo", "divideBackDetailNo", "channelOrderId", "channelRefundNo",
            "requestNo", "applicationNo");

    @Resource
    private PayFundOrderMapper fundOrderMapper;
    @Resource
    private PayFundDetailMapper fundDetailMapper;
    @Resource
    private PayDivideRequestMapper divideRequestMapper;
    @Resource
    private PayRefundFundDetailMapper refundFundDetailMapper;
    @Resource
    private PayYeepayMerchantAccountMapper merchantAccountMapper;
    @Resource
    private PayYeepayEventMapper yeepayEventMapper;
    @Resource
    private PayRefundMapper refundMapper;
    @Resource
    private PayOrderMapper payOrderMapper;
    @Resource
    private PayAppService appService;
    @Resource
    private PayNoRedisDAO noRedisDAO;
    @Resource
    private PayChannelService channelService;
    @Resource
    @Lazy
    private PayFundServiceImpl self;

    @Autowired
    private ObjectProvider<YeepayCallbackArchive> callbackArchiveProvider;

    @Override
    public PayFundOrderDO getFundOrderByPayOrderId(Long payOrderId) {
        return payOrderId == null ? null : fundOrderMapper.selectByPayOrderId(payOrderId);
    }

    @Override
    public PayFundOrderDO getFundOrder(Long fundOrderId) {
        return fundOrderId == null ? null : fundOrderMapper.selectById(fundOrderId);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PayFundMerchantAccountRespDTO registerMerchant(PayFundMerchantRegisterReqDTO reqDTO) {
        Long appId = appService.validPayApp(reqDTO.getAppKey()).getId();
        PayChannelDO channel = channelService.validPayChannel(appId, PayChannelEnum.YEEPAY_WX_LITE.getCode());
        YeepayPayClient client = getYeepayClient(channel);
        MerchantSubmission submission = self.prepareMerchantRegistration(appId, reqDTO);
        PayYeepayMerchantAccountDO account = submission.account();
        if (submission.token() == null) {
            PayFundMerchantAccountRespDTO current = convertMerchantAccount(account);
            if (!PayYeepayOnboardingStatusEnum.isCompleted(account.getOnboardingStatus())) {
                current.setLastError("PAY_ONBOARDING_IN_PROGRESS");
            }
            return current;
        }
        Map<String, Object> response = null;
        String failure = null;
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if (reqDTO.getMaterials() != null) params.putAll(reqDTO.getMaterials());
            boolean microMerchant = client.isMicroMerchantMaterials(params);
            params = microMerchant
                    ? client.buildMicroMerchantRegisterParams(params, account.getRequestNo(), reqDTO.getBusinessRole())
                    : client.buildMerchantRegisterParams(params, account.getRequestNo(), reqDTO.getBusinessRole());
            response = microMerchant ? client.registerMicroMerchant(params) : client.registerMerchant(params);
            if (response == null) {
                failure = "PAY_ONBOARDING_REQUEST_FAILED";
            }
        } catch (Exception e) {
            // SDK 异常可能带请求原文；只保存固定错误标记，不输出原始异常。
            failure = "PAY_ONBOARDING_REQUEST_FAILED";
            log.warn("[registerMerchant][merchant({}) 易宝入网请求失败，保留申请记录等待重试]",
                    reqDTO.getMerchantId());
        }
        return self.finishMerchantRegistration(account.getId(), submission.token(), response, failure);
    }

    /** 同一商家唯一行和稳定 requestNo 在返回前已经提交。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MerchantSubmission prepareMerchantRegistration(Long appId, PayFundMerchantRegisterReqDTO reqDTO) {
        PayYeepayMerchantAccountDO account = merchantAccountMapper.selectByAppIdAndMerchantId(appId, reqDTO.getMerchantId());
        if (account == null) {
            account = new PayYeepayMerchantAccountDO().setAppId(appId).setMerchantId(reqDTO.getMerchantId())
                    .setMerchantName(reqDTO.getMerchantName()).setRequestNo(StrUtil.blankToDefault(reqDTO.getRequestNo(),
                            "MER-" + appId + "-" + reqDTO.getMerchantId()))
                    .setOnboardingStatus(PayYeepayOnboardingStatusEnum.REVIEWING.getStatus())
                    .setStatus(PayYeepayMerchantAccountStatusEnum.DISABLED.getStatus());
            try {
                // 未分配渠道商编使用 NULL，允许多个申请共存于唯一索引。
                merchantAccountMapper.insert(account);
            } catch (DuplicateKeyException ex) {
                // 使用当前读；MySQL REPEATABLE READ 下普通读取可能仍看不到并发插入的已提交行。
                account = merchantAccountMapper.selectByAppIdAndMerchantIdForUpdate(appId, reqDTO.getMerchantId());
                if (account == null) throw ex;
            }
        }
        account = merchantAccountMapper.selectByIdForUpdate(account.getId());
        LocalDateTime now = LocalDateTime.now();
        if (PayYeepayOnboardingStatusEnum.isCompleted(account.getOnboardingStatus())
                || (StrUtil.isNotBlank(account.getSubmissionToken()) && account.getSubmissionExpireTime() != null
                && account.getSubmissionExpireTime().isAfter(now))) {
            return new MerchantSubmission(account, null);
        }
        Map<String, Object> materials = reqDTO.getMaterials() == null ? Map.of() : reqDTO.getMaterials();
        String token = UUID.randomUUID().toString();
        merchantAccountMapper.updateById(account.setSubmissionToken(token)
                .setSubmissionExpireTime(now.plusSeconds(MERCHANT_SUBMISSION_LEASE_SECONDS)).setLastRequestTime(now)
                .setMaterialsRef(StrUtil.sub(text(materials, "materialsRef", "materials_ref"), 0, 255))
                .setMaterialsVersion(number(materials.get("materialsVersion"), materials.get("materials_version"))));
        return new MerchantSubmission(account, token);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PayFundMerchantAccountRespDTO finishMerchantRegistration(Long accountId, String token,
                                                                    Map<String, Object> response, String failure) {
        PayYeepayMerchantAccountDO account = merchantAccountMapper.selectByIdForUpdate(accountId);
        if (Objects.equals(token, account.getSubmissionToken())) {
            if (failure == null) {
                account = updateMerchantAccount(account, response);
            } else if (!PayYeepayOnboardingStatusEnum.isCompleted(account.getOnboardingStatus())) {
                merchantAccountMapper.updateById(new PayYeepayMerchantAccountDO().setId(accountId)
                        .setLastError(failure).setRemark(failure));
                account.setLastError(failure).setRemark(failure);
            }
            merchantAccountMapper.clearSubmission(accountId, token);
        }
        return convertMerchantAccount(account);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PayFundMerchantAccountRespDTO getMerchant(String appKey, Long merchantId) {
        Long appId = appService.validPayApp(appKey).getId();
        PayChannelDO channel = channelService.validPayChannel(appId, PayChannelEnum.YEEPAY_WX_LITE.getCode());
        PayYeepayMerchantAccountDO account = merchantAccountMapper.selectByAppIdAndMerchantId(appId, merchantId);
        if (account == null) return null;
        if (StrUtil.isNotBlank(account.getSubmissionToken()) && account.getSubmissionExpireTime() != null
                && account.getSubmissionExpireTime().isAfter(LocalDateTime.now())) {
            return convertMerchantAccount(account).setLastError("PAY_ONBOARDING_IN_PROGRESS");
        }
        Map<String, Object> response = getYeepayClient(channel).queryMerchant(account.getRequestNo());
        if (response == null) {
            return convertMerchantAccount(account).setLastError("PAY_ONBOARDING_QUERY_FAILED");
        }
        return self.finishMerchantQuery(account.getId(), account.getLastRequestTime(), response);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PayFundMerchantAccountRespDTO finishMerchantQuery(Long accountId, LocalDateTime requestTime,
                                                             Map<String, Object> response) {
        PayYeepayMerchantAccountDO current = merchantAccountMapper.selectByIdForUpdate(accountId);
        if (Objects.equals(requestTime, current.getLastRequestTime())) {
            current = updateMerchantAccount(current, response);
        }
        return convertMerchantAccount(current);
    }

    public record MerchantSubmission(PayYeepayMerchantAccountDO account, String token) { }

    private PayYeepayMerchantAccountDO updateMerchantAccount(PayYeepayMerchantAccountDO account, Map<String, Object> response) {
        account = merchantAccountMapper.selectByIdForUpdate(account.getId());
        String merchantNo = text(response, "merchantNo", "subMerchantNo");
        String applicationNo = text(response, "applicationNo", "applicationId");
        String statusText = text(response, "applicationStatus", "status", "registerStatus", "auditStatus", "result");
        PayYeepayOnboardingStatusEnum onboarding = PayYeepayOnboardingStatusEnum.from(statusText);
        if (PayYeepayOnboardingStatusEnum.isCompleted(account.getOnboardingStatus())
                && onboarding != PayYeepayOnboardingStatusEnum.COMPLETED) {
            // 查询/同步响应可能早于已处理的完成回调，终态不回退。
            return account;
        }
        PayYeepayMerchantAccountDO update = new PayYeepayMerchantAccountDO().setId(account.getId())
                .setMerchantNo(StrUtil.blankToDefault(merchantNo, account.getMerchantNo()))
                .setApplicationNo(StrUtil.blankToDefault(applicationNo, account.getApplicationNo()))
                .setSignUrl(StrUtil.blankToDefault(text(response, "agreementSignUrl", "signUrl", "signingUrl"), account.getSignUrl()))
                .setAuthUrl(StrUtil.blankToDefault(text(response, "intentionAuthUrl", "authUrl", "authenticityUrl"), account.getAuthUrl()))
                .setOnboardingStatus(onboarding == null ? account.getOnboardingStatus() : onboarding.getStatus())
                .setStatus(onboarding != null && onboarding == PayYeepayOnboardingStatusEnum.COMPLETED
                        ? PayYeepayMerchantAccountStatusEnum.ENABLED.getStatus()
                        : onboarding == null ? account.getStatus() : PayYeepayMerchantAccountStatusEnum.DISABLED.getStatus())
                .setRemark(StrUtil.sub(text(response, "auditOpinion", "reason", "message", "msg"), 0, 250))
                .setLastError(StrUtil.blankToDefault(
                        StrUtil.sub(text(response, "error", "errorMessage", "reason"), 0, 512), ""))
                .setChannelData(toJsonString(sanitizeEventPayload(response)));
        merchantAccountMapper.updateById(update);
        account.setMerchantNo(update.getMerchantNo()).setApplicationNo(update.getApplicationNo())
                .setSignUrl(update.getSignUrl()).setAuthUrl(update.getAuthUrl()).setLastError(update.getLastError())
                .setOnboardingStatus(update.getOnboardingStatus()).setStatus(update.getStatus()).setRemark(update.getRemark())
                .setChannelData(update.getChannelData());
        return account;
    }

    private static Integer number(Object... values) {
        for (Object value : values) {
            if (value == null) continue;
            try {
                return Integer.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 资料版本是辅助字段，非法值不应阻断入网请求。
            }
        }
        return null;
    }

    private PayFundMerchantAccountRespDTO convertMerchantAccount(PayYeepayMerchantAccountDO account) {
        return new PayFundMerchantAccountRespDTO().setId(account.getId()).setMerchantId(account.getMerchantId())
                .setRequestNo(account.getRequestNo()).setApplicationNo(account.getApplicationNo())
                .setMerchantNo(account.getMerchantNo()).setSignUrl(account.getSignUrl()).setAuthUrl(account.getAuthUrl())
                .setOnboardingStatus(account.getOnboardingStatus()).setStatus(account.getStatus())
                .setReason(account.getRemark()).setLastError(account.getLastError())
                .setLastRequestTime(account.getLastRequestTime()).setMaterialsVersion(account.getMaterialsVersion());
    }

    @Override
    public List<PayOrderDividePlanDTO> validateDividePlan(Integer orderPrice,
                                                           List<PayOrderDividePlanDTO> plans,
                                                           int maxRecipients) {
        if (CollUtil.isEmpty(plans)) {
            return Collections.emptyList();
        }
        int max = maxRecipients > 0 ? maxRecipients : DEFAULT_MAX_RECIPIENTS;
        if (plans.size() > max) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        Set<Long> merchantIds = new HashSet<>();
        long total = 0;
        for (PayOrderDividePlanDTO plan : plans) {
            if (plan == null || plan.getMerchantId() == null || !PayFundAmountValidator.isPositive(plan.getAmount())
                    || plan.getTradeOrderId() == null || plan.getTradeOrderSubId() == null
                    || !merchantIds.add(plan.getMerchantId())) {
                throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
            }
            total += plan.getAmount();
        }
        if (orderPrice == null || total != orderPrice) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        return plans;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void preparePayment(PayOrderDO order, PayChannelDO channel) {
        preparePayment(order, channel, order == null ? null : order.getNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void preparePayment(PayOrderDO order, PayChannelDO channel, String payOrderNo) {
        if (!isYeepay(channel) || order == null) {
            return;
        }
        ((YeepayPayClientConfig) channel.getConfig()).validateFullAmountDivideGuarantee();
        if (StrUtil.isBlank(payOrderNo)) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        if (StrUtil.isBlank(order.getDividePlanData())) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        if (fundOrderMapper.selectByPayOrderId(order.getId()) != null) {
            return;
        }
        List<PayOrderDividePlanDTO> rawPlans = JsonUtils.parseArray(order.getDividePlanData(), PayOrderDividePlanDTO.class);
        if (CollUtil.isEmpty(rawPlans)) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        int maxRecipients = getMaxRecipients(channel);
        validateDividePlan(order.getPrice(), rawPlans, maxRecipients);
        List<PayOrderDividePlanDTO> plans = resolveMerchantSnapshots(order.getAppId(), rawPlans);

        String fundNo = noRedisDAO.generate(FUND_NO_PREFIX);
        PayFundOrderDO fundOrder = new PayFundOrderDO()
                .setNo(fundNo).setAppId(order.getAppId()).setChannelId(channel.getId())
                .setChannelCode(channel.getCode()).setPayOrderId(order.getId()).setPayOrderNo(payOrderNo)
                .setMerchantOrderId(order.getMerchantOrderId()).setUserId(order.getUserId()).setUserType(order.getUserType())
                .setTradeOrderId(plans.get(0).getTradeOrderId()).setTradeOrderSubId(plans.get(0).getTradeOrderSubId())
                .setDividePlanData(toJsonString(plans)).setAmount(order.getPrice()).setPrice(order.getPrice())
                .setDividePrice(plans.stream().mapToInt(PayOrderDividePlanDTO::getAmount).sum())
                .setRefundPrice(0).setStatus(PayFundOrderStatusEnum.PENDING.getStatus())
                .setClearingStatus(PayFundOrderStatusEnum.PENDING.getStatus())
                .setDivideStatus(PayFundOrderStatusEnum.PENDING.getStatus()).setRetryCount(0)
                .setParentMerchantNo(((YeepayPayClientConfig) channel.getConfig()).getParentMerchantNo())
                .setCollectionMerchantNo(((YeepayPayClientConfig) channel.getConfig()).getCollectionMerchantNo())
                .setCurrency("CNY")
                .setNotifyUrl(order.getNotifyUrl());
        fundOrderMapper.insert(fundOrder);

        for (PayOrderDividePlanDTO plan : plans) {
            String detailNo = noRedisDAO.generate(FUND_NO_PREFIX);
            PayFundDetailDO detail = new PayFundDetailDO()
                    .setNo(detailNo).setFundOrderId(fundOrder.getId()).setFundOrderNo(fundOrder.getNo())
                    .setPayOrderId(order.getId()).setPayOrderNo(payOrderNo).setTradeOrderId(plan.getTradeOrderId())
                    .setTradeOrderSubId(plan.getTradeOrderSubId()).setTradeOrderItemId(plan.getTradeOrderItemId())
                    .setMerchantId(plan.getMerchantId()).setMerchantNo(plan.getMerchantNo())
                    .setAccountNo(plan.getMerchantNo()).setAmount(plan.getAmount()).setPrice(plan.getAmount())
                    // 退款可能早于清算/分账，必须在支付准备阶段固定可复用的明细号。
                    .setDivideDetailNo("DD-" + detailNo)
                    .setStatus(PayFundDetailStatusEnum.PENDING.getStatus()).setCurrency("CNY");
            fundDetailMapper.insert(detail);
        }
        // 记录完整商编快照，后续退款不得重新读取商家当前资料。
        order.setDividePlanData(toJsonString(plans));
        payOrderMapper.updateById(new PayOrderDO().setId(order.getId()).setDividePlanData(order.getDividePlanData()));
    }

    @Override
    public String buildPrePayDivideDetail(PayOrderDO order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("支付订单不能为空");
        }
        PayFundOrderDO fundOrder = fundOrderMapper.selectByPayOrderId(order.getId());
        if (fundOrder == null) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        List<PayFundDetailDO> details = fundDetailMapper.selectListByFundOrderId(fundOrder.getId());
        if (CollUtil.isEmpty(details)) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        List<Map<String, Object>> officialDetails = new ArrayList<>(details.size());
        details.stream()
                .sorted(Comparator.comparing(PayFundDetailDO::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(detail -> {
                    if (StrUtil.isBlank(detail.getMerchantNo()) || detail.getAmount() == null
                            || detail.getAmount() <= 0) {
                        throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("amount", YeepayPayClient.money(detail.getAmount()));
                    item.put("ledgerNo", detail.getMerchantNo());
                    item.put("divideDetailDesc", "商家订单分账");
                    officialDetails.add(item);
                });
        long total = details.stream().mapToLong(detail -> detail.getAmount() == null ? 0 : detail.getAmount()).sum();
        if (total != order.getPrice() || total != fundOrder.getAmount()) {
            throw exception(PAY_ORDER_DIVIDE_PLAN_INVALID);
        }
        return toJsonString(officialDetails);
    }

    /** 便于主流程调用的订单对象版本。 */
    public void validateDividePlan(PayOrderDO order, PayChannelDO channel) {
        if (!isYeepay(channel) || order == null || StrUtil.isBlank(order.getDividePlanData())) {
            return;
        }
        List<PayOrderDividePlanDTO> plans = JsonUtils.parseArray(order.getDividePlanData(), PayOrderDividePlanDTO.class);
        validateDividePlan(order.getPrice(), plans, getMaxRecipients(channel));
        resolveMerchantSnapshots(order.getAppId(), plans);
    }

    private List<PayOrderDividePlanDTO> resolveMerchantSnapshots(Long appId, List<PayOrderDividePlanDTO> plans) {
        for (PayOrderDividePlanDTO plan : plans) {
            PayYeepayMerchantAccountDO account = merchantAccountMapper.selectByAppIdAndMerchantId(appId, plan.getMerchantId());
            if (account == null || !PayYeepayOnboardingStatusEnum.isCompleted(account.getOnboardingStatus())
                    || !PayYeepayMerchantAccountStatusEnum.isEnabled(account.getStatus())
                    || StrUtil.isBlank(account.getMerchantNo())) {
                throw exception(PAY_ORDER_DIVIDE_PLAN_MERCHANT_NOT_READY);
            }
            if (StrUtil.isNotBlank(plan.getMerchantNo()) && !Objects.equals(plan.getMerchantNo(), account.getMerchantNo())) {
                throw exception(PAY_ORDER_DIVIDE_PLAN_MERCHANT_NOT_READY);
            }
            plan.setMerchantNo(account.getMerchantNo());
        }
        return plans;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPaymentSuccess(PayOrderDO order, PayOrderRespDTO notify) {
        if (order == null || !PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(order.getChannelCode())) {
            return;
        }
        PayFundOrderDO fundOrder = fundOrderMapper.selectByPayOrderId(order.getId());
        if (fundOrder == null) {
            return;
        }
        PayFundOrderDO update = new PayFundOrderDO().setId(fundOrder.getId())
                // 已清算或分账成功的状态不可因乱序支付通知被回退。
                // 易宝通常回传渠道订单号；缺失时使用预支付时传入的稳定订单号，保证清算查询仍可推进。
                .setPayOrderNo(StrUtil.blankToDefault(fundOrder.getPayOrderNo(), order.getNo()))
                .setChannelOrderNo(notify == null || StrUtil.isBlank(notify.getChannelOrderNo())
                        ? StrUtil.blankToDefault(order.getChannelOrderNo(),
                        StrUtil.blankToDefault(fundOrder.getChannelOrderNo(), order.getNo()))
                        : notify.getChannelOrderNo())
                .setSuccessTime(notify == null || notify.getSuccessTime() == null
                        ? LocalDateTime.now() : notify.getSuccessTime())
                .setChannelNotifyData(notify == null ? null : toJsonString(notify));
        fundOrderMapper.updateById(update);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processDivide(Long fundOrderId) {
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        if (order == null || order.getSuccessTime() == null || StrUtil.isBlank(yeepayOrderId(order))
                || Objects.equals(order.getClearingStatus(), PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus())) return;
        if (order.getNextRetryTime() != null && order.getNextRetryTime().isAfter(LocalDateTime.now())) return;
        YeepayPayClient client = getYeepayClient(channelService.getChannel(order.getChannelId()));
        Integer clearingStatus = order.getClearingStatus();
        if (!PayFundOrderStatusEnum.isSuccess(clearingStatus)) {
            if (!self.claimClearingQuery(fundOrderId)) return;
            try {
                Map<String, Object> clearing = StrUtil.isBlank(order.getCollectionMerchantNo())
                        ? client.queryClearing(yeepayOrderId(order))
                        : client.queryClearing(yeepayOrderId(order), order.getChannelOrderNo(),
                            order.getParentMerchantNo(), order.getCollectionMerchantNo());
                // 订单查询中的 status 是支付状态，不能作为清算成功依据。
                String explicitClearing = text(clearing, "clearStatus", "clearingStatus");
                clearingStatus = explicitClearing == null ? null
                        : parseChannelStatus(Map.of("clearStatus", explicitClearing));
                if (PayFundOrderStatusEnum.isSuccess(clearingStatus)
                        && !Objects.equals(requiredAmount(clearing, "orderAmount", "清算查询缺少订单金额"), order.getAmount())) {
                    throw new IllegalArgumentException("易宝清算查询金额与原资金快照不一致");
                }
            } catch (Exception e) {
                self.recordClearingResult(fundOrderId, null);
                return;
            }
            self.recordClearingResult(fundOrderId, clearingStatus);
        }
        if (!PayFundOrderStatusEnum.isSuccess(clearingStatus)) return;
        DivideAttempt attempt = self.prepareDivideAttempt(fundOrderId);
        if (attempt == null) return;
        try {
            Map<String, Object> response = attempt.query()
                    ? queryDivide(client, attempt.order(), attempt.request().getRequestNo())
                    : applyDivide(client, attempt.order(), attempt.request().getRequestNo(), attempt.details());
            self.completeDivideAttempt(fundOrderId, response);
        } catch (Exception e) {
            // 超时、断连或无法校验的响应都可能已经被受理。保持 PROCESSING，仅查询原请求号。
            self.recordUnknownDivideResult(fundOrderId, e.getClass().getSimpleName());
        }
    }

    /** 在短事务中预留查询租约与一次重试；并发扫描不能重复消耗重试次数。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean claimClearingQuery(Long fundOrderId) {
        fundOrderMapper.lockById(fundOrderId);
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        LocalDateTime now = LocalDateTime.now();
        if (order == null || PayFundOrderStatusEnum.isSuccess(order.getClearingStatus())
                || Objects.equals(order.getClearingStatus(), PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus())
                || order.getNextRetryTime() != null && order.getNextRetryTime().isAfter(now)) return false;
        YeepayPayClientConfig config = (YeepayPayClientConfig) channelService.getChannel(order.getChannelId()).getConfig();
        int count = (order.getRetryCount() == null ? 0 : order.getRetryCount()) + 1;
        fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId).setRetryCount(count)
                .setNextRetryTime(now.plusSeconds(Math.max(60L,
                        (long) (config.getConnectTimeoutSeconds() + config.getTimeoutSeconds())
                                * (config.getMaxRetries() + 1) + 3))));
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordClearingResult(Long fundOrderId, Integer status) {
        fundOrderMapper.lockById(fundOrderId);
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        if (order == null || PayFundOrderStatusEnum.isSuccess(order.getClearingStatus())) return;
        YeepayPayClientConfig config = (YeepayPayClientConfig) channelService.getChannel(order.getChannelId()).getConfig();
        int count = PayFundOrderStatusEnum.isSuccess(status) ? 0 : (order.getRetryCount() == null ? 0 : order.getRetryCount());
        boolean manual = !PayFundOrderStatusEnum.isSuccess(status) && count > config.getMaxRetries();
        fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId).setRetryCount(count)
                .setClearingStatus(manual ? PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus()
                        : status == null ? PayFundOrderStatusEnum.PROCESSING.getStatus() : status)
                .setNextRetryTime(PayFundOrderStatusEnum.isSuccess(status) ? LocalDateTime.now()
                        : nextRetry(config, count))
                .setLastError(manual ? "清算结果未确认，需人工核实" : PayFundOrderStatusEnum.isFailed(status) ? "易宝清算未成功" : ""));
    }

    /** 资金行锁同时串行化退款预留与分账；网络调用只在此事务提交之后发生。 */
    @Transactional(rollbackFor = Exception.class)
    public DivideAttempt prepareDivideAttempt(Long fundOrderId) {
        fundOrderMapper.lockById(fundOrderId);
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        if (order == null || !PayFundOrderStatusEnum.isSuccess(order.getClearingStatus())) return null;
        PayDivideRequestDO request = divideRequestMapper.selectByFundOrderId(fundOrderId);
        if (request != null && (PayDivideRequestStatusEnum.isSuccess(request.getStatus())
                || Objects.equals(request.getStatus(), PayDivideRequestStatusEnum.MANUAL_REQUIRED.getStatus()))) return null;
        if (request != null && request.getNextRetryTime() != null
                && request.getNextRetryTime().isAfter(LocalDateTime.now())) return null;
        if (request != null && PayDivideRequestStatusEnum.isProcessing(request.getStatus())) {
            int count = request.getRetryCount() == null ? 0 : request.getRetryCount();
            YeepayPayClientConfig config = (YeepayPayClientConfig) channelService.getChannel(order.getChannelId()).getConfig();
            if (count > config.getMaxRetries()) {
                request.setStatus(PayDivideRequestStatusEnum.MANUAL_REQUIRED.getStatus())
                        .setChannelErrorMsg("原分账结果尚未确认，需人工核实");
                divideRequestMapper.updateById(request);
                fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId)
                        .setDivideStatus(PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus())
                        .setLastError("原分账结果尚未确认，需人工核实"));
                return null;
            }
            request.setRetryCount(count + 1).setNextRetryTime(nextRetry(config, count));
            divideRequestMapper.updateById(request);
            return new DivideAttempt(order, request, List.of(), true);
        }
        // 退款处理中不能固定新的分账执行金额；成功退款会减少执行金额，原支付快照保持不变。
        boolean refundPending = refundFundDetailMapper.selectListByFundOrderId(fundOrderId).stream()
                .anyMatch(row -> !PayRefundFundDetailStatusEnum.isSuccess(row.getRefundStatus())
                        && !PayRefundFundDetailStatusEnum.isFailed(row.getRefundStatus())
                        && !PayRefundFundDetailStatusEnum.isClosed(row.getStatus()));
        if (refundPending) return null;
        List<PayFundDetailDO> fundDetails = fundDetailMapper.selectListByFundOrderId(fundOrderId);
        if (fundDetails.isEmpty()) return null;
        List<Map<String, Object>> details = new ArrayList<>();
        int total = 0;
        for (PayFundDetailDO detail : fundDetails) {
            int amount = detail.getAmount() - (detail.getRefundedAmount() == null ? 0 : detail.getRefundedAmount());
            if (amount < 0) throw new IllegalStateException("退款累计金额超过原资金快照");
            // 一旦请求建立，执行快照也不可重新计算（包括失败重试）。
            if (request != null && detail.getDivideAmount() != null) amount = detail.getDivideAmount();
            else fundDetailMapper.updateById(new PayFundDetailDO().setId(detail.getId()).setDivideAmount(amount));
            if (amount == 0) continue;
            details.add(new LinkedHashMap<>(Map.of("ledgerNo", detail.getMerchantNo(),
                    "ledgerType", "MERCHANT2MERCHANT", "amount", YeepayPayClient.money(amount),
                    "divideDetailDesc", "商家订单分账")));
            total = Math.addExact(total, amount);
        }
        if (request == null) {
            request = new PayDivideRequestDO().setNo(noRedisDAO.generate(DIVIDE_NO_PREFIX))
                    .setRequestNo("DIVIDE_" + order.getId()).setAppId(order.getAppId())
                    .setChannelId(order.getChannelId()).setChannelCode(order.getChannelCode())
                    .setFundOrderId(fundOrderId).setFundOrderNo(order.getNo())
                    .setAmount(total).setPrice(total).setRequestData(toJsonString(details))
                    .setStatus(PayDivideRequestStatusEnum.PENDING.getStatus()).setRetryCount(0);
            divideRequestMapper.insert(request);
        }
        if (total == 0) {
            request.setStatus(PayDivideRequestStatusEnum.SUCCESS.getStatus()).setSuccessTime(LocalDateTime.now());
            divideRequestMapper.updateById(request);
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId)
                    .setDivideStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setStatus(PayFundOrderStatusEnum.SUCCESS.getStatus()));
            return null;
        }
        if (request.getAmount() != total) throw new IllegalStateException("分账执行快照金额冲突");
        request.setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus())
                .setRetryCount((request.getRetryCount() == null ? 0 : request.getRetryCount()) + 1)
                .setNextRetryTime(LocalDateTime.now().plusSeconds(3));
        divideRequestMapper.updateById(request);
        fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId)
                .setDivideStatus(PayFundOrderStatusEnum.PROCESSING.getStatus()));
        return new DivideAttempt(order, request, details, false);
    }

    public record DivideAttempt(PayFundOrderDO order, PayDivideRequestDO request,
                                List<Map<String, Object>> details, boolean query) { }

    @Transactional(rollbackFor = Exception.class)
    public void completeDivideAttempt(Long fundOrderId, Map<String, Object> response) {
        fundOrderMapper.lockById(fundOrderId);
        PayDivideRequestDO request = divideRequestMapper.selectByFundOrderId(fundOrderId);
        if (request == null || PayDivideRequestStatusEnum.isSuccess(request.getStatus())) return;
        List<PayFundDetailDO> details = fundDetailMapper.selectListByFundOrderId(fundOrderId).stream()
                .filter(detail -> executionAmount(detail) > 0).toList();
        applyDivideResponse(fundOrderMapper.selectById(fundOrderId), request, details, response);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordUnknownDivideResult(Long fundOrderId, String errorType) {
        fundOrderMapper.lockById(fundOrderId);
        PayDivideRequestDO request = divideRequestMapper.selectByFundOrderId(fundOrderId);
        if (request == null || PayDivideRequestStatusEnum.isSuccess(request.getStatus())) return;
        request.setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus())
                .setNextRetryTime(nextRetry((YeepayPayClientConfig) channelService.getChannel(request.getChannelId()).getConfig(),
                        request.getRetryCount() == null ? 0 : request.getRetryCount()))
                .setChannelErrorCode("UNKNOWN_RESULT").setChannelErrorMsg("待查询: " + errorType);
        divideRequestMapper.updateById(request);
    }

    private static int executionAmount(PayFundDetailDO detail) {
        return detail.getDivideAmount() != null ? detail.getDivideAmount() : detail.getAmount();
    }

    private void markDivideFailure(PayDivideRequestDO request, List<PayFundDetailDO> details,
                                   String code, String message) {
        int retry = request.getRetryCount() == null ? 0 : request.getRetryCount();
        YeepayPayClientConfig config = (YeepayPayClientConfig) channelService.getChannel(request.getChannelId()).getConfig();
        boolean manualRequired = retry > config.getMaxRetries();
        request.setChannelErrorCode(code).setChannelErrorMsg(StrUtil.sub(message, 0, 250))
                .setStatus(manualRequired ? PayDivideRequestStatusEnum.MANUAL_REQUIRED.getStatus()
                        : PayDivideRequestStatusEnum.RETRYING.getStatus())
                .setNextRetryTime(manualRequired ? null : nextRetry(config, retry));
        // Request and detail tables intentionally have different state machines. A retrying
        // request keeps its detail rows PROCESSING; RETRYING/MANUAL_REQUIRED are request-only
        // states and must never leak into the detail status column.
        markDetails(details, manualRequired ? PayFundDetailStatusEnum.FAILED.getStatus()
                : PayFundDetailStatusEnum.PROCESSING.getStatus());
    }

    private void applyDivideResponse(PayFundOrderDO fundOrder, PayDivideRequestDO request,
                                     List<PayFundDetailDO> details, Map<String, Object> response) {
        Integer status = parseChannelStatus(response);
        request.setResponseData(toJsonString(sanitizeEventPayload(response)))
                .setChannelRequestNo(text(response, "requestId", "divideRequestId"))
                .setChannelErrorCode(text(response, "code"))
                .setChannelErrorMsg(text(response, "message", "msg"));
        if (Objects.equals(status, PayDivideRequestStatusEnum.SUCCESS.getStatus())) {
            // The provider is allowed to return detail rows in a different order from the
            // request. Resolve every row against the immutable ledgerNo/amount snapshot before
            // mutating any local success state; an index-based binding could credit the wrong
            // merchant when two rows have different amounts or the response is reordered.
            List<Map<String, Object>> responseDetails = callbackDetails(response);
            if (responseDetails.size() != details.size()) {
                throw new IllegalStateException("易宝分账成功响应明细数量与本地分账计划不一致");
            }
            Set<Long> matched = new HashSet<>();
            Set<String> channelDetailNos = new HashSet<>();
            List<DivideResponseMatch> matches = new ArrayList<>(responseDetails.size());
            long total = 0;
            for (Map<String, Object> responseDetail : responseDetails) {
                String channelDetailNo = requiredText(responseDetail, "divideDetailNo", "detailNo",
                        "divideBackDetailNo", "易宝分账成功响应缺少 divideDetailNo");
                if (!channelDetailNos.add(channelDetailNo)) {
                    throw new IllegalStateException("易宝分账成功响应包含重复 divideDetailNo");
                }
                String ledgerNo = text(responseDetail, "ledgerNo", "merchantNo", "subMerchantNo",
                        "receiverMerchantNo");
                if (StrUtil.isBlank(ledgerNo)) {
                    throw new IllegalStateException("易宝分账成功响应缺少 ledgerNo");
                }
                int amount = requiredAmount(responseDetail, "amount", "divideAmount",
                        "易宝分账成功响应缺少明细金额");
                List<PayFundDetailDO> candidates = details.stream()
                        .filter(item -> !matched.contains(item.getId())
                                && Objects.equals(ledgerNo, item.getMerchantNo())
                                && amount == executionAmount(item))
                        .toList();
                if (candidates.size() != 1) {
                    throw new IllegalStateException("易宝分账成功响应明细与本地商家金额快照无法唯一匹配");
                }
                PayFundDetailDO detail = candidates.get(0);
                matched.add(detail.getId());
                total += amount;
                matches.add(new DivideResponseMatch(detail, channelDetailNo));
            }
            long expectedTotal = details.stream().mapToLong(item -> executionAmount(item)).sum();
            if (matched.size() != details.size() || expectedTotal != total) {
                throw new IllegalStateException("易宝分账成功响应明细合计与本地分账计划不一致");
            }
            String aggregateAmount = text(response, "divideAmount", "amount");
            if (StrUtil.isNotBlank(aggregateAmount)
                    && requiredAmount(response, "divideAmount", "amount", "易宝分账成功响应缺少分账金额") != total) {
                throw new IllegalStateException("易宝分账成功响应总金额与本地分账计划不一致");
            }
            LocalDateTime successTime = channelCompletionTime(response, "divideSuccessDate");
            for (DivideResponseMatch match : matches) {
                match.detail().setStatus(PayFundDetailStatusEnum.SUCCESS.getStatus()).setSuccessTime(successTime)
                        .setChannelDetailNo(match.channelDetailNo());
                fundDetailMapper.updateById(match.detail());
            }
            request.setStatus(PayDivideRequestStatusEnum.SUCCESS.getStatus()).setNextRetryTime(null)
                    .setSuccessTime(successTime);
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrder.getId())
                    .setStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setDivideStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setLastError(""));
        } else if (Objects.equals(status, PayDivideRequestStatusEnum.PROCESSING.getStatus())) {
            request.setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus());
            markDetails(details, PayFundDetailStatusEnum.PROCESSING.getStatus());
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrder.getId())
                    .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setDivideStatus(PayFundOrderStatusEnum.PROCESSING.getStatus()));
        } else if (status == null) {
            // 官方协议新增状态时必须保守等待查询，不能把未知状态当成最终失败。
            request.setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus());
            markDetails(details, PayFundDetailStatusEnum.PROCESSING.getStatus());
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrder.getId())
                    .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setDivideStatus(PayFundOrderStatusEnum.PROCESSING.getStatus())
                    .setLastError("易宝返回未识别的分账状态，等待查询"));
        } else {
            markDivideFailure(request, details, text(response, "code"), text(response, "message", "msg"));
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrder.getId())
                    .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .setDivideStatus(request.getStatus())
                    .setLastError(request.getChannelErrorMsg()));
        }
        divideRequestMapper.updateById(request);
    }

    private void markDetails(List<PayFundDetailDO> details, Integer status) {
        for (PayFundDetailDO detail : details) {
            detail.setStatus(status);
            fundDetailMapper.updateById(detail);
        }
    }

    @Override
    public int processPendingDivides() {
        List<PayDivideRequestDO> requests = divideRequestMapper.selectListByStatuses(List.of(
                PayDivideRequestStatusEnum.PENDING.getStatus(), PayDivideRequestStatusEnum.PROCESSING.getStatus(),
                PayDivideRequestStatusEnum.RETRYING.getStatus(), PayDivideRequestStatusEnum.FAILED.getStatus()));
        int count = 0;
        Set<Long> processedFundOrders = new HashSet<>();
        for (PayDivideRequestDO request : requests) {
            if (request.getNextRetryTime() != null && request.getNextRetryTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            try {
                self.processDivide(request.getFundOrderId());
                processedFundOrders.add(request.getFundOrderId());
                count++;
            } catch (Exception e) {
                log.warn("[processPendingDivides][request({}) 重试失败({})]", request.getRequestNo(), e.getClass().getSimpleName());
            }
        }
        // 清算回调可能丢失；只要支付成功且拿到了渠道订单号，就主动推进分账。
        for (PayFundOrderDO fundOrder : fundOrderMapper.selectListPendingDivide()) {
            if (processedFundOrders.contains(fundOrder.getId()) || StrUtil.isBlank(fundOrder.getChannelOrderNo())) {
                continue;
            }
            try {
                self.processDivide(fundOrder.getId());
                count++;
            } catch (Exception e) {
                log.warn("[processPendingDivides][fundOrder({}) 补偿失败({})]", fundOrder.getId(), e.getClass().getSimpleName());
            }
        }
        return count;
    }

    @Override
    public int processFailedYeepayEvents() {
        List<PayYeepayEventDO> events = yeepayEventMapper.selectListByStatus(
                List.of(PayYeepayEventStatusEnum.RECEIVED.getStatus(),
                        PayYeepayEventStatusEnum.PROCESSING.getStatus(),
                        PayYeepayEventStatusEnum.FAILED.getStatus()));
        int count = 0;
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        for (PayYeepayEventDO event : events) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            if (retryCount >= MAX_AUTO_RETRY || StrUtil.isBlank(event.getPayload())
                    || (PayYeepayEventStatusEnum.isProcessing(event.getStatus())
                    && event.getUpdateTime() != null && event.getUpdateTime().isAfter(staleBefore))) {
                continue;
            }
            if (!yeepayEventMapper.claimForProcessing(event.getId(), staleBefore)) {
                continue;
            }
            Map<String, Object> payload = JsonUtils.parseMap(event.getPayload());
            if (payload == null || payload.isEmpty()) {
                updateYeepayEventById(event.getId(), PayYeepayEventStatusEnum.FAILED.getStatus(),
                        "易宝事件 payload 为空或不是 JSON", retryCount + 1);
                continue;
            }
            try {
                switch (event.getEventType()) {
                    case "merchant" -> self.handleMerchantOnboarding(payload);
                    case "clearing" -> self.handleClearing(payload);
                    case "divide" -> self.handleDivide(payload);
                    case "divide-back" -> self.handleDivideBack(payload);
                    default -> {
                        // 支付和退款通过各自的订单/退款查询任务兜底，不能让事件永久停留在
                        // PROCESSING。保留事件审计记录，并明确标记为无需事件回放。
                        updateYeepayEventById(event.getId(), PayYeepayEventStatusEnum.IGNORED.getStatus(),
                                "该事件由支付/退款查询任务负责补偿，不执行通知回放", retryCount);
                        count++;
                        continue;
                    }
                }
                updateYeepayEventById(event.getId(), PayYeepayEventStatusEnum.PROCESSED.getStatus(), null,
                        retryCount);
                count++;
            } catch (Exception e) {
                String message = "易宝事件处理失败: " + e.getClass().getSimpleName();
                updateYeepayEventById(event.getId(), PayYeepayEventStatusEnum.FAILED.getStatus(), message,
                        retryCount + 1);
                log.warn("[processFailedYeepayEvents][event({}) 重放失败({})]", event.getEventId(), e.getClass().getSimpleName());
            }
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PayRefundFundAllocationDTO> prepareRefund(PayRefundDO refund, PayOrderDO order,
                                                           PayChannelDO channel,
                                                           List<PayRefundFundAllocationDTO> allocations) {
        if (!isYeepay(channel)) {
            return allocations == null ? Collections.emptyList() : allocations;
        }
        PayFundOrderDO fundOrder = fundOrderMapper.selectByPayOrderId(order.getId());
        if (fundOrder == null) {
            throw new IllegalStateException("易宝支付单缺少资金快照");
        }
        fundOrderMapper.lockById(fundOrder.getId());
        PayDivideRequestDO activeDivide = divideRequestMapper.selectByFundOrderId(fundOrder.getId());
        if (activeDivide != null && !PayDivideRequestStatusEnum.isSuccess(activeDivide.getStatus())) {
            throw new IllegalStateException("分账请求尚未确认完成，暂不可退款，请查询或人工核实后重试");
        }
        List<PayFundDetailDO> details = fundDetailMapper.selectListByFundOrderId(fundOrder.getId());
        List<PayRefundFundAllocationDTO> input = allocations == null ? Collections.emptyList() : allocations;
        if (input.isEmpty()) {
            throw new IllegalArgumentException("易宝退款必须提供商家资金分配");
        }
        long allocationTotal = input.stream().mapToLong(item -> item.getAmount() == null ? 0 : item.getAmount()).sum();
        if (allocationTotal != refund.getRefundPrice()) {
            throw new IllegalArgumentException("退款资金分配金额必须等于退款金额");
        }
        List<PayRefundFundAllocationDTO> resolved = new ArrayList<>();
        Set<Long> allocatedFundDetails = new HashSet<>();
        for (PayRefundFundAllocationDTO allocation : input) {
            if (!PayFundAmountValidator.isPositive(allocation.getAmount())) {
                throw new IllegalArgumentException("退款资金分配金额必须大于零");
            }
            PayFundDetailDO detail = findFundDetail(details, allocation);
            if (detail == null || allocation.getAmount() > detail.getAmount()) {
                throw new IllegalArgumentException("退款资金分配超过商家原分账金额");
            }
            if (!allocatedFundDetails.add(detail.getId())) {
                throw new IllegalArgumentException("同一资金明细不能重复分配");
            }
            PayRefundFundDetailDO existing = refundFundDetailMapper.selectByRefundIdAndFundDetailId(
                    refund.getId(), detail.getId());
            if (existing != null && !Objects.equals(existing.getAmount(), allocation.getAmount())) {
                throw new IllegalArgumentException("重复退款单的资金分配金额不一致");
            }
            long refunded = refundFundDetailMapper.selectListByFundDetailId(detail.getId()).stream()
                    .filter(item -> existing == null || !Objects.equals(item.getId(), existing.getId()))
                    .filter(item -> (!PayRefundFundDetailStatusEnum.isFailed(item.getStatus())
                            && !PayRefundFundDetailStatusEnum.isClosed(item.getStatus()))
                            || PayRefundFundDetailStatusEnum.isSuccess(item.getRefundStatus())
                            || hasReservedMerchantReturn(item))
                    .mapToLong(PayRefundFundDetailDO::getAmount).sum();
            if (refunded + allocation.getAmount() > detail.getAmount()) {
                throw new IllegalArgumentException("累计退款超过商家原分账金额");
            }
            boolean divided = PayFundDetailStatusEnum.isSuccess(detail.getStatus())
                    && StrUtil.isNotBlank(detail.getChannelDetailNo());
            if (StrUtil.isNotBlank(allocation.getDivideDetailNo())
                    && !Objects.equals(allocation.getDivideDetailNo(), detail.getChannelDetailNo())) {
                throw new IllegalArgumentException("退款原分账明细号与支付快照冲突");
            }
            allocation.setFundDetailId(detail.getId()).setMerchantId(detail.getMerchantId())
                    .setLedgerNo(detail.getMerchantNo())
                    .setDivideDetailNo(divided
                            ? detail.getChannelDetailNo() : null)
                    .setDivBackAccountType(divided ? null : "DIVIDE_ACCOUNT");
            resolved.add(allocation);
            PayRefundFundDetailDO refundDetail = existing;
            if (refundDetail == null) {
                refundDetail = new PayRefundFundDetailDO().setNo(noRedisDAO.generate(REFUND_FUND_NO_PREFIX))
                        .setRefundId(refund.getId()).setRefundNo(refund.getNo()).setPayOrderId(order.getId())
                        .setPayOrderNo(order.getNo()).setFundOrderId(fundOrder.getId()).setFundOrderNo(fundOrder.getNo())
                        .setFundDetailId(detail.getId()).setFundDetailNo(detail.getNo()).setAfterSaleId(allocation.getAfterSaleId())
                        .setMerchantId(detail.getMerchantId()).setDivideDetailNo(allocation.getDivideDetailNo())
                        .setAppId(refund.getAppId()).setChannelId(refund.getChannelId()).setChannelCode(refund.getChannelCode())
                        .setMerchantRefundId(refund.getMerchantRefundId()).setAmount(allocation.getAmount())
                        .setPrice(allocation.getAmount())
                        .setRefundStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus())
                        .setDivideBackStatus(PayFundDetailStatusEnum.isSuccess(detail.getStatus())
                                && StrUtil.isNotBlank(detail.getChannelDetailNo())
                                ? PayRefundFundDetailStatusEnum.PENDING.getStatus()
                                : PayRefundFundDetailStatusEnum.SUCCESS.getStatus())
                        .setStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus())
                        .setReason(refund.getReason());
                refundFundDetailMapper.insert(refundDetail);
            }
            if (divided && !PayRefundFundDetailStatusEnum.isSuccess(refundDetail.getDivideBackStatus())) {
                refundDetail.setDivideBackRequestId(StrUtil.blankToDefault(refundDetail.getDivideBackRequestId(),
                        refund.getNo() + "_BACK"));
                refundFundDetailMapper.updateById(refundDetail);
            }
        }
        for (PayRefundFundAllocationDTO allocation : resolved) {
            PayRefundFundDetailDO row = refundFundDetailMapper.selectByRefundIdAndFundDetailId(
                    refund.getId(), allocation.getFundDetailId());
            // A delayed-settlement detail has no /divide/back request. It still needs to
            // carry DIVIDE_ACCOUNT into /trade/refund, so only an actually divided detail can
            // be marked as already returned.
            PayFundDetailDO fundDetail = details.stream()
                    .filter(item -> Objects.equals(item.getId(), allocation.getFundDetailId()))
                    .findFirst().orElse(null);
            boolean divided = fundDetail != null && PayFundDetailStatusEnum.isSuccess(fundDetail.getStatus())
                    && StrUtil.isNotBlank(fundDetail.getChannelDetailNo());
            allocation.setDivideBackCompleted(divided && row != null
                    && PayRefundFundDetailStatusEnum.isSuccess(row.getDivideBackStatus()));
        }
        refund.setFundAllocationData(toJsonString(resolved));
        return resolved;
    }

    private static boolean hasReservedMerchantReturn(PayRefundFundDetailDO detail) {
        // 未分账明细的 SUCCESS 只是无需归还的标记，不代表资金已从商家转回。
        // 实际归还成功或结果不明时，退款失败/关闭也不能释放原商家的额度。
        return StrUtil.isNotBlank(detail.getDivideDetailNo())
                && !PayRefundFundDetailStatusEnum.isPending(detail.getDivideBackStatus())
                && !PayRefundFundDetailStatusEnum.isFailed(detail.getDivideBackStatus())
                && !PayRefundFundDetailStatusEnum.isClosed(detail.getDivideBackStatus());
    }

    @Override
    public boolean executeRefundPreparation(Long refundId) {
        ReturnAttempt attempt = self.prepareReturnAttempt(refundId);
        if (attempt == null) return isRefundReady(refundId);
        YeepayPayClient client = getYeepayClient(channelService.getChannel(attempt.order().getChannelId()));
        try {
            Map<String, Object> response = attempt.query()
                    ? queryDivideBack(client, attempt.order(), attempt.requestNo())
                    : applyDivideBack(client, attempt.order(), attempt.divideRequestNo(), attempt.requestNo(), attempt.details());
            self.completeReturnAttempt(refundId, response);
        } catch (Exception e) {
            // PROCESSING was committed before issuing the request. Only query this stable ID
            // after a timeout or an unverifiable response; never create another return attempt.
            log.warn("[executeRefundPreparation][refund({}) 资金归还结果待查询: {}]",
                    refundId, e.getClass().getSimpleName());
        }
        return isRefundReady(refundId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnAttempt prepareReturnAttempt(Long refundId) {
        PayRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) return null;
        List<PayRefundFundDetailDO> rows = refundFundDetailMapper.selectListByRefundNo(refund.getNo());
        if (rows.isEmpty()) return null;
        Long fundOrderId = rows.get(0).getFundOrderId();
        fundOrderMapper.lockById(fundOrderId);
        rows = refundFundDetailMapper.selectListByRefundNo(refund.getNo()).stream()
                .filter(row -> StrUtil.isNotBlank(row.getDivideDetailNo())).toList();
        if (rows.isEmpty() || rows.stream().allMatch(row -> PayRefundFundDetailStatusEnum.isSuccess(row.getDivideBackStatus()))) return null;
        if (rows.stream().anyMatch(row -> Objects.equals(row.getDivideBackStatus(),
                PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus()))) return null;
        if (rows.stream().anyMatch(row -> row.getNextRetryTime() != null
                && row.getNextRetryTime().isAfter(LocalDateTime.now()))) return null;
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        PayDivideRequestDO divide = divideRequestMapper.selectByFundOrderId(fundOrderId);
        if (divide == null || !PayDivideRequestStatusEnum.isSuccess(divide.getStatus())) {
            throw new IllegalStateException("归还资金必须引用已确认成功的原分账请求");
        }
        String requestNo = rows.get(0).getDivideBackRequestId();
        if (StrUtil.isBlank(requestNo) || rows.stream().anyMatch(row -> !Objects.equals(requestNo, row.getDivideBackRequestId()))) {
            throw new IllegalStateException("资金归还稳定请求号冲突");
        }
        boolean query = rows.stream().anyMatch(row -> !PayRefundFundDetailStatusEnum.isPending(row.getDivideBackStatus()));
        List<Map<String, Object>> details = new ArrayList<>();
        int maxRetries = ((YeepayPayClientConfig) channelService.getChannel(order.getChannelId()).getConfig()).getMaxRetries();
        for (PayRefundFundDetailDO row : rows) {
            details.add(new LinkedHashMap<>(Map.of("divideDetailNo", row.getDivideDetailNo(),
                    "amount", YeepayPayClient.money(row.getAmount()),
                    "divideBackReason", StrUtil.blankToDefault(refund.getReason(), "用户退款"))));
            if (PayRefundFundDetailStatusEnum.isSuccess(row.getDivideBackStatus())) continue;
            int retries = row.getRetryCount() == null ? 0 : row.getRetryCount();
            row.setRetryCount(retries + 1).setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(300, 3L << Math.min(retries, 6))))
                    .setDivideBackStatus(query && retries >= maxRetries
                            ? PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus()
                            : PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
            refundFundDetailMapper.updateById(row);
            if (row.getDivideBackStatus().equals(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus())) return null;
        }
        return new ReturnAttempt(order, divide.getRequestNo(), requestNo, details, query);
    }

    public record ReturnAttempt(PayFundOrderDO order, String divideRequestNo, String requestNo,
                                List<Map<String, Object>> details, boolean query) { }

    @Transactional(rollbackFor = Exception.class)
    public void completeReturnAttempt(Long refundId, Map<String, Object> response) {
        PayRefundDO refund = refundMapper.selectById(refundId);
        List<PayRefundFundDetailDO> rows = refundFundDetailMapper.selectListByRefundNo(refund.getNo());
        fundOrderMapper.lockById(rows.get(0).getFundOrderId());
        rows = refundFundDetailMapper.selectListByRefundNo(refund.getNo()).stream()
                .filter(row -> StrUtil.isNotBlank(row.getDivideDetailNo())).toList();
        applyDivideBackResponse(rows, response);
        List<PayRefundFundAllocationDTO> allocations = JsonUtils.parseArray(refund.getFundAllocationData(), PayRefundFundAllocationDTO.class);
        if (allocations != null) {
            for (PayRefundFundAllocationDTO allocation : allocations) {
                allocation.setDivideBackCompleted(rows.stream().anyMatch(row -> Objects.equals(row.getFundDetailId(), allocation.getFundDetailId())
                        && PayRefundFundDetailStatusEnum.isSuccess(row.getDivideBackStatus())));
            }
            refundMapper.updateById(new PayRefundDO().setId(refundId).setFundAllocationData(toJsonString(allocations)));
        }
    }

    private void applyDivideBackResponse(List<PayRefundFundDetailDO> rows, Map<String, Object> response) {
        Integer aggregateStatus = parseChannelStatus(response);
        List<Map<String, Object>> responseDetails = callbackDetails(response);
        if (responseDetails.isEmpty()) {
            if (PayDivideRequestStatusEnum.isSuccess(aggregateStatus)) {
                // 官方查询允许只有汇总成功状态；必须先绑定原订单、商编与稳定归还号。
                PayFundOrderDO order = fundOrderMapper.selectById(rows.get(0).getFundOrderId());
                validateDivideBackCallback(order, rows, response, rows.get(0).getDivideBackRequestId());
            }
            for (PayRefundFundDetailDO row : rows) applyDivideBackStatus(row, aggregateStatus, response);
            return;
        }
        Map<String, PayRefundFundDetailDO> rowsByDetailNo = new java.util.HashMap<>();
        for (PayRefundFundDetailDO row : rows) rowsByDetailNo.put(row.getDivideDetailNo(), row);
        Set<Long> matched = new HashSet<>();
        List<DivideBackCallbackMatch> matches = new ArrayList<>();
        for (Map<String, Object> responseDetail : responseDetails) {
            String detailNo = text(responseDetail, "divideDetailNo", "detailNo", "divideBackDetailNo");
            PayRefundFundDetailDO row = rowsByDetailNo.get(detailNo);
            if (row == null || !matched.add(row.getId())) throw new IllegalArgumentException("归还明细无法唯一匹配原快照");
            Integer status = parseChannelStatus(responseDetail);
            if (PayDivideRequestStatusEnum.isSuccess(status == null ? aggregateStatus : status)
                    && requiredAmount(responseDetail, "amount", "divideBackAmount", "归还成功缺少金额") != row.getAmount()) {
                throw new IllegalArgumentException("资金归还响应金额与原退款分配不符");
            }
            matches.add(new DivideBackCallbackMatch(row, responseDetail));
        }
        if (matched.size() != rows.size()) throw new IllegalArgumentException("资金归还响应缺少本地明细");
        for (DivideBackCallbackMatch match : matches) {
            Integer status = parseChannelStatus(match.callback());
            applyDivideBackStatus(match.local(), status == null ? aggregateStatus : status, response);
        }
    }

    private void applyDivideBackStatus(PayRefundFundDetailDO row, Integer status, Map<String, Object> response) {
        if (PayRefundFundDetailStatusEnum.isSuccess(row.getDivideBackStatus())) return;
        Integer divideBackStatus = Objects.equals(status, PayDivideRequestStatusEnum.SUCCESS.getStatus())
                ? PayRefundFundDetailStatusEnum.SUCCESS.getStatus()
                : status == null || Objects.equals(status, PayDivideRequestStatusEnum.PROCESSING.getStatus())
                ? PayRefundFundDetailStatusEnum.PROCESSING.getStatus()
                : PayRefundFundDetailStatusEnum.FAILED.getStatus();
        if (PayRefundFundDetailStatusEnum.isSuccess(divideBackStatus)) row.setDivideBackSuccessTime(channelCompletionTime(response, "divideBackSuccessDate"));
        row.setDivideBackStatus(divideBackStatus)
                .setStatus(PayRefundFundDetailStatusEnum.isSuccess(row.getRefundStatus())
                        ? PayRefundFundDetailStatusEnum.SUCCESS.getStatus()
                        : PayRefundFundDetailStatusEnum.isFailed(row.getRefundStatus()) && hasReservedMerchantReturn(row)
                        ? PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus()
                        : divideBackStatus == PayRefundFundDetailStatusEnum.SUCCESS.getStatus()
                        ? PayRefundFundDetailStatusEnum.PENDING.getStatus() : divideBackStatus)
                .setChannelNotifyData(toJsonString(sanitizeEventPayload(response)));
        if (PayRefundFundDetailStatusEnum.isFailed(row.getRefundStatus()) && hasReservedMerchantReturn(row)) {
            row.setLastError(PayRefundFundDetailStatusEnum.isSuccess(divideBackStatus)
                    ? "商家资金已归还，但用户退款失败，需人工核实原退款请求"
                    : "商家资金归还结果尚未确认，用户退款失败，需人工核实原归还和退款请求");
        }
        int updated = refundFundDetailMapper.updateDivideBackStatusIfNotSuccess(row);
        if (updated == 1 && divideBackStatus == PayRefundFundDetailStatusEnum.SUCCESS.getStatus()) {
            fundDetailMapper.incrementReturnedAmount(row.getFundDetailId(), row.getAmount());
        }
    }

    @Override
    public boolean isRefundReady(Long refundId) {
        if (refundId == null) {
            return false;
        }
        PayRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) {
            return false;
        }
        List<PayRefundFundDetailDO> details = refundFundDetailMapper.selectListByRefundNo(refund.getNo());
        if (CollUtil.isEmpty(details)) {
            return false;
        }
        return details.stream()
                .allMatch(item -> item.getDivideBackStatus() == null
                        || PayRefundFundDetailStatusEnum.isSuccess(item.getDivideBackStatus()));
    }

    private PayFundDetailDO findFundDetail(List<PayFundDetailDO> details, PayRefundFundAllocationDTO allocation) {
        if (allocation.getFundDetailId() == null && allocation.getTradeOrderSubId() == null
                && allocation.getMerchantId() == null) {
            throw new IllegalArgumentException("退款资金分配必须提供原资金明细或商家子订单关联");
        }
        List<PayFundDetailDO> matches = details.stream()
                .filter(detail -> (allocation.getFundDetailId() == null || Objects.equals(detail.getId(), allocation.getFundDetailId()))
                        && (allocation.getTradeOrderSubId() == null || Objects.equals(detail.getTradeOrderSubId(), allocation.getTradeOrderSubId()))
                        && (allocation.getMerchantId() == null || Objects.equals(detail.getMerchantId(), allocation.getMerchantId()))
                        && (allocation.getTradeOrderItemId() == null || detail.getTradeOrderItemId() == null
                            || Objects.equals(detail.getTradeOrderItemId(), allocation.getTradeOrderItemId())))
                .toList();
        if (matches.size() != 1) throw new IllegalArgumentException("退款资金关联冲突或无法唯一定位原快照");
        return matches.get(0);
    }

    private PayFundOrderDO findFundOrder(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (String key : List.of("orderId", "outTradeNo", "uniqueOrderNo", "channelOrderId",
                "channelOrderNo", "channelTrxId", "fundOrderNo")) {
            String value = text(payload, key);
            if (StrUtil.isBlank(value)) {
                continue;
            }
            PayFundOrderDO result = fundOrderMapper.selectByChannelOrderNo(value);
            if (result == null) {
                result = fundOrderMapper.selectByPayOrderNo(value);
            }
            if (result == null) {
                result = fundOrderMapper.selectByNo(value);
            }
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private void validateFundOrderCallback(PayFundOrderDO fundOrder, Map<String, Object> payload,
                                           String eventName) {
        validateFundOrderReferenceAndMerchant(fundOrder, payload, eventName);
        int amount = requiredAmount(payload, "amount", "orderAmount", "payAmount", "totalAmount",
                eventName + "回调缺少金额");
        if (!Objects.equals(amount, fundOrder.getAmount())) {
            throw new IllegalArgumentException("易宝" + eventName + "金额与本地资金单不一致");
        }
    }

    private void validateTrustedChannel(PayFundOrderDO order, Map<String, Object> payload) {
        Object trusted = payload.get("_trustedChannelId");
        if (trusted != null && !Objects.equals(String.valueOf(order.getChannelId()), String.valueOf(trusted))) {
            throw new IllegalArgumentException("易宝回调渠道与原资金单绑定不一致");
        }
    }

    private void validateFundOrderReferenceAndMerchant(PayFundOrderDO fundOrder, Map<String, Object> payload,
                                                       String eventName) {
        validateTrustedChannel(fundOrder, payload);
        String orderNo = requiredText(payload, "orderId", "outTradeNo", "uniqueOrderNo", "channelOrderId",
                "channelOrderNo", "channelTrxId", eventName + "回调缺少订单号");
        if (!Objects.equals(orderNo, fundOrder.getNo())
                && !Objects.equals(orderNo, fundOrder.getPayOrderNo())
                && !Objects.equals(orderNo, fundOrder.getChannelOrderNo())) {
            throw new IllegalArgumentException("易宝" + eventName + "订单号与本地资金单不一致");
        }
        validateMerchantNo(fundOrder, payload, eventName);
    }

    private void validateMerchantNo(PayFundOrderDO fundOrder, Map<String, Object> payload, String eventName) {
        String collectionMerchantNo = text(payload, "merchantNo", "collectionMerchantNo");
        String parentMerchantNo = text(payload, "parentMerchantNo");
        if (StrUtil.isBlank(collectionMerchantNo) && StrUtil.isBlank(parentMerchantNo)) {
            throw new IllegalArgumentException("易宝" + eventName + "回调缺少商编");
        }
        if (StrUtil.isNotBlank(collectionMerchantNo)
                && !Objects.equals(collectionMerchantNo, fundOrder.getCollectionMerchantNo())) {
            throw new IllegalArgumentException("易宝" + eventName + "收款商编与本地快照不一致");
        }
        if (StrUtil.isNotBlank(parentMerchantNo)
                && !Objects.equals(parentMerchantNo, fundOrder.getParentMerchantNo())) {
            throw new IllegalArgumentException("易宝" + eventName + "平台商编与本地快照不一致");
        }
    }

    private List<DivideCallbackMatch> validateDivideCallback(PayFundOrderDO fundOrder, PayDivideRequestDO request,
                                                              Map<String, Object> payload, Integer status) {
        // YopOrderDivideResDTO does not contain root-level merchantNo or
        // parentMerchantNo. Validate the order reference here and only validate
        // merchant numbers when the provider happens to include them.
        validateFundOrderReference(fundOrder, payload, "分账");
        String requestNo = requiredText(payload, "divideRequestId", "requestId", "requestNo", "分账回调缺少请求号");
        if (!Objects.equals(requestNo, request.getRequestNo())
                && !Objects.equals(requestNo, request.getChannelRequestNo())) {
            throw new IllegalArgumentException("易宝分账请求号与本地请求不一致");
        }
        // Processing and failure notifications are often summaries without detail rows. The
        // authoritative detail mapping is required only once YeePay reports SUCCESS.
        List<Map<String, Object>> callbackDetails = callbackDetails(payload);
        if (!Objects.equals(status, PayDivideRequestStatusEnum.SUCCESS.getStatus())) {
            return Collections.emptyList();
        }
        // The official response names the aggregate amount divideAmount. Keep
        // amount as a compatibility fallback for older callback samples.
        int divideAmount = requiredAmount(payload, "divideAmount", "amount", "分账回调缺少分账金额");
        if (!Objects.equals(divideAmount, request.getAmount())) {
            throw new IllegalArgumentException("易宝分账金额与本地资金单不一致");
        }
        if (callbackDetails.isEmpty()) {
            throw new IllegalArgumentException("易宝分账回调缺少分账明细");
        }
        List<PayFundDetailDO> localDetails = fundDetailMapper.selectListByFundOrderId(fundOrder.getId()).stream()
                .filter(detail -> executionAmount(detail) > 0).toList();
        Set<Long> matched = new HashSet<>();
        List<DivideCallbackMatch> matches = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> callbackDetail : callbackDetails) {
            String detailNo = requiredText(callbackDetail, "divideDetailNo", "detailNo", "divideBackDetailNo",
                    "分账回调缺少明细号");
            int amount = requiredAmount(callbackDetail, "amount", "divideAmount", "分账回调缺少明细金额");
            PayFundDetailDO local = localDetails.stream()
                    .filter(item -> !matched.contains(item.getId())
                            && (Objects.equals(detailNo, item.getDivideDetailNo())
                            || Objects.equals(detailNo, item.getChannelDetailNo())))
                    .findFirst().orElse(null);
            if (local == null) {
                // The detail number is created by YeePay. On the first successful callback it
                // may not equal the local pre-submit placeholder, so use the immutable receiver
                // merchant and amount as the fallback identity.
                String merchantNo = text(callbackDetail, "ledgerNo", "merchantNo", "subMerchantNo",
                        "receiverMerchantNo");
                List<PayFundDetailDO> candidates = localDetails.stream()
                        .filter(item -> !matched.contains(item.getId())
                                && Objects.equals(merchantNo, item.getMerchantNo())
                                && amount == executionAmount(item))
                        .toList();
                if (candidates.size() == 1) {
                    local = candidates.get(0);
                } else if (candidates.size() > 1) {
                    throw new IllegalArgumentException("易宝分账明细无法唯一匹配本地商家快照");
                }
            }
            if (local == null || !matched.add(local.getId()) || amount != executionAmount(local)) {
                throw new IllegalArgumentException("易宝分账明细与本地分账快照不一致");
            }
            validateDetailMerchant(local, callbackDetail, "分账");
            matches.add(new DivideCallbackMatch(local, callbackDetail, detailNo));
            total += amount;
        }
        // `total` is accumulated as a long while the persisted amount fields use Integer.
        // Comparing the boxed values with Objects.equals would reject equal numeric values
        // because Long(300) and Integer(300) are different types.
        if (request.getAmount() == null || total != request.getAmount()) {
            throw new IllegalArgumentException("易宝分账明细合计与本地金额不一致");
        }
        if (matched.size() != localDetails.size()) {
            throw new IllegalArgumentException("易宝分账成功回调缺少本地分账明细");
        }
        return matches;
    }

    private record DivideCallbackMatch(PayFundDetailDO local, Map<String, Object> callback,
                                       String channelDetailNo) {
    }

    private record DivideResponseMatch(PayFundDetailDO detail, String channelDetailNo) {
    }

    private List<DivideBackCallbackMatch> validateDivideBackCallback(PayFundOrderDO fundOrder,
                                                                       List<PayRefundFundDetailDO> localRows,
                                                                       Map<String, Object> payload,
                                                                       String requestNo) {
        validateFundOrderReferenceAndMerchant(fundOrder, payload, "资金归还");
        String callbackRequestNo = requiredText(payload, "divideBackRequestId", "requestId", "requestNo",
                "资金归还回调缺少请求号");
        if (!Objects.equals(callbackRequestNo, requestNo)) {
            throw new IllegalArgumentException("易宝资金归还请求号与本地请求不一致");
        }
        List<Map<String, Object>> callbackDetails = callbackDetails(payload);
        if (callbackDetails.isEmpty()) {
            // YopDivideBackResDTO may carry only the aggregate status. The
            // request snapshot is the authoritative amount/detail set in that
            // case; query responses are used to reconcile any later detail data.
            return localRows.stream()
                    .map(row -> new DivideBackCallbackMatch(row, Collections.emptyMap()))
                    .toList();
        }
        Set<Long> matched = new HashSet<>();
        List<DivideBackCallbackMatch> matches = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> callbackDetail : callbackDetails) {
            String detailNo = requiredText(callbackDetail, "divideDetailNo", "detailNo", "divideBackDetailNo",
                    "资金归还回调缺少明细号");
            int amount = requiredAmount(callbackDetail, "amount", "divideBackAmount", "资金归还回调缺少明细金额");
            PayRefundFundDetailDO local = localRows.stream()
                    .filter(item -> Objects.equals(detailNo, item.getDivideDetailNo()))
                    .findFirst().orElse(null);
            if (local == null || !matched.add(local.getId()) || !Objects.equals(amount, local.getAmount())) {
                throw new IllegalArgumentException("易宝资金归还明细与本地退款资金快照不一致");
            }
            matches.add(new DivideBackCallbackMatch(local, callbackDetail));
            total += amount;
        }
        long expected = localRows.stream().mapToLong(item -> item.getAmount() == null ? 0 : item.getAmount()).sum();
        if (total != expected) {
            throw new IllegalArgumentException("易宝资金归还金额与本地退款资金快照不一致");
        }
        if (matched.size() != localRows.size()) {
            throw new IllegalArgumentException("易宝资金归还回调缺少本地退款资金明细");
        }
        return matches;
    }

    private record DivideBackCallbackMatch(PayRefundFundDetailDO local, Map<String, Object> callback) {
    }

    private void validateDetailMerchant(PayFundDetailDO local, Map<String, Object> callbackDetail,
                                        String eventName) {
        String merchantNo = text(callbackDetail, "ledgerNo", "merchantNo", "subMerchantNo",
                "receiverMerchantNo");
        if (StrUtil.isNotBlank(merchantNo) && !Objects.equals(merchantNo, local.getMerchantNo())) {
            throw new IllegalArgumentException("易宝" + eventName + "明细商编与本地快照不一致");
        }
    }

    private void validateFundOrderReference(PayFundOrderDO fundOrder, Map<String, Object> payload,
                                            String eventName) {
        validateTrustedChannel(fundOrder, payload);
        String orderNo = requiredText(payload, "orderId", "outTradeNo", "uniqueOrderNo", "channelOrderId",
                "channelOrderNo", "channelTrxId", eventName + "回调缺少订单号");
        if (!Objects.equals(orderNo, fundOrder.getNo())
                && !Objects.equals(orderNo, fundOrder.getPayOrderNo())
                && !Objects.equals(orderNo, fundOrder.getChannelOrderNo())) {
            throw new IllegalArgumentException("易宝" + eventName + "订单号与本地资金单不一致");
        }
        String collectionMerchantNo = text(payload, "merchantNo", "collectionMerchantNo");
        String parentMerchantNo = text(payload, "parentMerchantNo");
        if (StrUtil.isNotBlank(collectionMerchantNo) || StrUtil.isNotBlank(parentMerchantNo)) {
            validateMerchantNo(fundOrder, payload, eventName);
        }
    }

    private static String requiredText(Map<String, Object> payload, String key1, String key2, String key3,
                                       String errorMessage) {
        String value = text(payload, key1, key2, key3);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    private static String requiredText(Map<String, Object> payload, String key1, String key2, String key3,
                                       String key4, String key5, String key6, String errorMessage) {
        String value = text(payload, key1, key2, key3, key4, key5, key6);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    private static int requiredAmount(Map<String, Object> payload, String... keysAndError) {
        if (keysAndError == null || keysAndError.length < 2) {
            throw new IllegalArgumentException("回调金额字段配置错误");
        }
        String errorMessage = keysAndError[keysAndError.length - 1];
        String[] keys = java.util.Arrays.copyOf(keysAndError, keysAndError.length - 1);
        String value = text(payload, keys);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            BigDecimal decimal = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
            int cents = decimal.movePointRight(2).intValueExact();
            if (cents < 0) {
                throw new IllegalArgumentException(errorMessage);
            }
            return cents;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException(errorMessage, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> callbackDetails(Map<String, Object> payload) {
        for (String key : List.of("divideDetail", "divideDetails", "details", "resultDetails",
                "divideBackDetail", "divideBackDetails")) {
            Object value = payload == null ? null : payload.get(key);
            if (value instanceof String raw && StrUtil.isNotBlank(raw)) {
                try {
                    value = JsonUtils.parseObject(raw, Object.class);
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
            if (value instanceof Map<?, ?> map) {
                return List.of((Map<String, Object>) map);
            }
            if (value instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add((Map<String, Object>) map);
                    }
                }
                return result;
            }
        }
        String detailNo = text(payload, "divideDetailNo", "detailNo", "divideBackDetailNo");
        if (StrUtil.isNotBlank(detailNo)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("divideDetailNo", detailNo);
            if (payload != null) {
                for (String key : List.of("amount", "divideAmount", "divideBackAmount")) {
                    if (payload.containsKey(key)) {
                        detail.put("amount", payload.get(key));
                        break;
                    }
                }
            }
            return List.of(detail);
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onRefundResult(PayRefundDO refund, PayRefundRespDTO notify) {
        if (refund == null || !PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(refund.getChannelCode())) return;
        List<PayRefundFundDetailDO> details = refundFundDetailMapper.selectListByRefundNo(refund.getNo());
        Map<Long, Integer> newlyRefundedByFundOrder = new LinkedHashMap<>();
        for (PayRefundFundDetailDO detail : details) {
            detail.setChannelRefundNo(notify.getChannelRefundNo()).setChannelNotifyData(toJsonString(notify));
            boolean success = PayRefundStatusEnum.isSuccess(notify.getStatus());
            if (PayRefundStatusEnum.isSuccess(notify.getStatus())) {
                detail.setRefundStatus(PayRefundFundDetailStatusEnum.SUCCESS.getStatus())
                        .setStatus(PayRefundFundDetailStatusEnum.SUCCESS.getStatus()).setSuccessTime(notify.getSuccessTime())
                        .setLastError(null);
            } else if (PayRefundStatusEnum.isFailure(notify.getStatus())) {
                boolean reservedReturn = hasReservedMerchantReturn(detail);
                detail.setRefundStatus(PayRefundFundDetailStatusEnum.FAILED.getStatus())
                        .setStatus(reservedReturn ? PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus()
                                : PayRefundFundDetailStatusEnum.FAILED.getStatus())
                        .setLastError(!reservedReturn ? null
                                : PayRefundFundDetailStatusEnum.isSuccess(detail.getDivideBackStatus())
                                ? "商家资金已归还，但用户退款失败，需人工核实原退款请求"
                                : "商家资金归还结果尚未确认，用户退款失败，需人工核实原归还和退款请求")
                        .setChannelErrorCode(notify.getChannelErrorCode()).setChannelErrorMsg(notify.getChannelErrorMsg());
            }
            if (success || PayRefundStatusEnum.isFailure(notify.getStatus())) {
                // The conditional update is the idempotency gate. A successful row can be
                // counted exactly once even when callback and query workers race.
                int updated = refundFundDetailMapper.updateRefundStatusIfNotSuccess(detail);
                if (success && updated == 1) {
                    int amount = detail.getAmount() == null ? 0 : detail.getAmount();
                    if (amount > 0) {
                        fundDetailMapper.incrementRefundedAmount(detail.getFundDetailId(), amount);
                        newlyRefundedByFundOrder.merge(detail.getFundOrderId(), amount, Integer::sum);
                    }
                }
            }
        }
        for (Map.Entry<Long, Integer> entry : newlyRefundedByFundOrder.entrySet()) {
            fundOrderMapper.incrementRefundPrice(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean validatePaymentNotify(Map<String, Object> payload) {
        PayFundOrderDO fundOrder = findFundOrder(payload);
        if (fundOrder == null) {
            return false;
        }
        validateFundOrderCallback(fundOrder, payload, "支付");
        return true;
    }

    @Override
    public boolean validateRefundNotify(Map<String, Object> payload) {
        String refundNo = requiredText(payload, "refundRequestId", "outRefundNo", "refundId", "退款回调缺少退款请求号");
        PayRefundDO refund = refundMapper.selectByNo(refundNo);
        if (refund == null) {
            return false;
        }
        PayFundOrderDO fundOrder = fundOrderMapper.selectByPayOrderId(refund.getOrderId());
        if (fundOrder == null || refundFundDetailMapper.selectListByRefundNo(refund.getNo()).isEmpty()) {
            return false;
        }
        validateFundOrderReferenceAndMerchant(fundOrder, payload, "退款");
        int amount = requiredAmount(payload, "refundAmount", "amount", "refundPrice", "退款回调金额");
        if (!Objects.equals(amount, refund.getRefundPrice())) {
            throw new IllegalArgumentException("易宝退款金额与本地退款单不一致");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleClearing(Map<String, Object> payload) {
        // 清算回调只负责记录/触发分账，支付成功和清算成功保持独立。
        String orderNo = text(payload, "orderId", "outTradeNo", "uniqueOrderNo", "channelOrderId", "channelOrderNo");
        if (StrUtil.isBlank(orderNo)) return;
        PayFundOrderDO fundOrder = findFundOrder(payload);
        if (fundOrder != null) {
            fundOrderMapper.lockById(fundOrder.getId());
            fundOrder = fundOrderMapper.selectById(fundOrder.getId());
            validateFundOrderCallback(fundOrder, payload, "清算");
            Integer status = parseChannelStatus(payload);
            if (PayFundOrderStatusEnum.isSuccess(fundOrder.getClearingStatus())
                    && !Objects.equals(status, PayFundOrderStatusEnum.SUCCESS.getStatus())) {
                return;
            }
            PayFundOrderDO update = new PayFundOrderDO().setId(fundOrder.getId())
                    .setClearingStatus(status == null ? PayFundOrderStatusEnum.PROCESSING.getStatus() : status)
                    .setLastError(status != null && PayFundOrderStatusEnum.isFailed(status)
                            ? text(payload, "code", "message", "msg") : null);
            if (StrUtil.isBlank(fundOrder.getChannelOrderNo()) && !Objects.equals(orderNo, fundOrder.getPayOrderNo())) {
                update.setChannelOrderNo(orderNo);
            }
            fundOrderMapper.updateById(update);
            if (update.getChannelOrderNo() != null) {
                fundOrder.setChannelOrderNo(update.getChannelOrderNo());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDivide(Map<String, Object> payload) {
        String requestNo = text(payload, "divideRequestId", "requestId", "requestNo");
        if (StrUtil.isBlank(requestNo)) return;
        PayDivideRequestDO request = divideRequestMapper.selectByRequestNo(requestNo);
        if (request == null) return;
        PayFundOrderDO fundOrder = fundOrderMapper.selectById(request.getFundOrderId());
        if (fundOrder == null) return;
        fundOrderMapper.lockById(fundOrder.getId());
        request = divideRequestMapper.selectByRequestNo(requestNo);
        if (PayDivideRequestStatusEnum.isSuccess(request.getStatus())) return;
        Integer status = parseChannelStatus(payload);
        List<DivideCallbackMatch> matches = validateDivideCallback(fundOrder, request, payload, status);
        request.setResponseData(toJsonString(sanitizeEventPayload(payload)));
        if (PayDivideRequestStatusEnum.isSuccess(request.getStatus())
                && !Objects.equals(status, PayDivideRequestStatusEnum.SUCCESS.getStatus())) {
            return;
        }
        request.setStatus(status == null ? PayDivideRequestStatusEnum.PROCESSING.getStatus()
                : status).setSuccessTime(status != null && status == PayDivideRequestStatusEnum.SUCCESS.getStatus()
                ? channelCompletionTime(payload, "divideSuccessDate") : null);
        request.setNextRetryTime(status == null || PayDivideRequestStatusEnum.isProcessing(status)
                ? LocalDateTime.now().plusSeconds(3) : null);
        divideRequestMapper.updateById(request);
        fundOrderMapper.updateById(new PayFundOrderDO().setId(request.getFundOrderId())
                .setStatus(status != null && PayDivideRequestStatusEnum.isSuccess(status)
                        ? PayFundOrderStatusEnum.SUCCESS.getStatus() : null)
                .setDivideStatus(status == null ? PayFundOrderStatusEnum.PROCESSING.getStatus() : status)
                .setLastError(status != null && PayFundOrderStatusEnum.isFailed(status)
                        ? text(payload, "code", "message", "msg") : null));
        List<PayFundDetailDO> fundDetails = fundDetailMapper.selectListByFundOrderId(request.getFundOrderId());
        if (status != null && status == PayDivideRequestStatusEnum.SUCCESS.getStatus()) {
            for (DivideCallbackMatch match : matches) {
                PayFundDetailDO detail = match.local();
                detail.setStatus(PayFundDetailStatusEnum.SUCCESS.getStatus()).setSuccessTime(channelCompletionTime(payload, "divideSuccessDate"))
                        .setChannelDetailNo(match.channelDetailNo());
                fundDetailMapper.updateById(detail);
            }
        } else if (status != null && PayDivideRequestStatusEnum.isFailed(status)) {
            for (PayFundDetailDO detail : fundDetails) {
                detail.setStatus(PayFundDetailStatusEnum.FAILED.getStatus())
                        .setChannelErrorCode(text(payload, "code"))
                        .setChannelErrorMsg(StrUtil.sub(text(payload, "message", "msg"), 0, 256));
                fundDetailMapper.updateById(detail);
            }
        } else {
            for (PayFundDetailDO detail : fundDetails) {
                detail.setStatus(PayFundDetailStatusEnum.PROCESSING.getStatus());
                fundDetailMapper.updateById(detail);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDivideBack(Map<String, Object> payload) {
        String requestNo = text(payload, "divideBackRequestId", "requestId", "requestNo");
        if (StrUtil.isBlank(requestNo)) return;
        List<PayRefundFundDetailDO> details = refundFundDetailMapper.selectListByDivideBackRequestId(requestNo);
        if (details.isEmpty()) {
            details = refundFundDetailMapper.selectListByRefundNo(
                    requestNo.endsWith("-BACK") || requestNo.endsWith("_BACK")
                            ? requestNo.substring(0, requestNo.length() - 5) : requestNo);
        }
        if (details.isEmpty()) return;
        PayFundOrderDO fundOrder = fundOrderMapper.selectById(details.get(0).getFundOrderId());
        if (fundOrder == null) return;
        fundOrderMapper.lockById(fundOrder.getId());
        List<DivideBackCallbackMatch> matches = validateDivideBackCallback(fundOrder, details, payload, requestNo);
        Integer aggregateStatus = parseChannelStatus(payload);
        for (DivideBackCallbackMatch match : matches) {
            PayRefundFundDetailDO detail = match.local();
            Integer detailStatus = parseChannelStatus(match.callback());
            Integer status = detailStatus == null ? aggregateStatus : detailStatus;
            Integer divideBackStatus = Objects.equals(status, PayDivideRequestStatusEnum.SUCCESS.getStatus())
                    ? PayRefundFundDetailStatusEnum.SUCCESS.getStatus()
                    : status == null || Objects.equals(status, PayDivideRequestStatusEnum.PROCESSING.getStatus())
                    ? PayRefundFundDetailStatusEnum.PROCESSING.getStatus()
                    : PayRefundFundDetailStatusEnum.FAILED.getStatus();
            detail.setChannelNotifyData(toJsonString(sanitizeEventPayload(payload)));
            if (PayRefundFundDetailStatusEnum.isSuccess(divideBackStatus)) detail.setDivideBackSuccessTime(channelCompletionTime(payload, "divideBackSuccessDate"));
            detail.setDivideBackStatus(divideBackStatus)
                    .setStatus(divideBackStatus == PayRefundFundDetailStatusEnum.SUCCESS.getStatus()
                            ? PayRefundFundDetailStatusEnum.PENDING.getStatus() : divideBackStatus)
                    .setLastError(divideBackStatus == PayRefundFundDetailStatusEnum.FAILED.getStatus()
                            ? StrUtil.sub(text(match.callback(), "code", "message", "msg"), 0, 512) : null);
            int updated = refundFundDetailMapper.updateDivideBackStatusIfNotSuccess(detail);
            if (updated == 1 && divideBackStatus == PayRefundFundDetailStatusEnum.SUCCESS.getStatus()) {
                fundDetailMapper.incrementReturnedAmount(detail.getFundDetailId(), detail.getAmount());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleMerchantOnboarding(Map<String, Object> payload) {
        String requestNo = requiredText(payload, "requestNo", "", "", "入网回调缺少 requestNo");
        PayYeepayMerchantAccountDO account = merchantAccountMapper.selectByRequestNo(requestNo);
        Object trusted = payload.get("_trustedChannelId");
        if (account != null && trusted != null) {
            PayChannelDO channel = channelService.getChannel(Long.valueOf(String.valueOf(trusted)));
            if (channel == null || !Objects.equals(account.getAppId(), channel.getAppId())) {
                throw new IllegalArgumentException("易宝入网回调与原应用绑定不一致");
            }
        }
        if (account == null) {
            return;
        }
        updateMerchantAccount(account, payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptYeepayEvent(String eventType, Map<String, Object> payload) {
        return acceptYeepayEvent(eventType, payload, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptYeepayEvent(String eventType, Map<String, Object> payload, String originalEnvelope) {
        // eventId/eventNo 是渠道级稳定事件号；没有事件号时只按稳定业务字段去重。
        // 回调中的时间戳、签名外字段可能每次变化，不能把完整报文作为幂等键。
        String externalId = eventExternalId(payload);
        String eventId = eventKey(eventType, externalId, payload);
        PayYeepayEventDO existing = yeepayEventMapper.selectByEventId(eventId);
        if (existing != null && PayYeepayEventStatusEnum.isFailed(existing.getStatus())
                && (existing.getRetryCount() == null || existing.getRetryCount() < MAX_AUTO_RETRY)) {
            int retryCount = existing.getRetryCount() == null ? 1 : existing.getRetryCount() + 1;
            // 易宝会重试回调；失败事件在自动重试上限内重新开放，避免永久吞掉后续通知。
            yeepayEventMapper.updateById(new PayYeepayEventDO().setId(existing.getId())
                    .setStatus(PayYeepayEventStatusEnum.PROCESSING.getStatus())
                    .setRetryCount(retryCount).setLastError(""));
            return true;
        }
        if (existing != null) {
            return false;
        }
        String encryptedOriginal = null;
        if (originalEnvelope != null) {
            Long tenantId = TenantContextHolder.getTenantId();
            Object channelId = payload.get("_trustedChannelId");
            if (tenantId == null || channelId == null) {
                throw new IllegalArgumentException("易宝回调原文缺少可信租户或渠道");
            }
            YeepayCallbackArchive archive = callbackArchiveProvider.getIfAvailable();
            if (archive == null) {
                throw new IllegalStateException("易宝回调归档服务未配置");
            }
            encryptedOriginal = archive.encrypt(originalEnvelope,
                    tenantId + "|" + channelId + "|" + eventType + "|" + eventId);
        }
        PayYeepayEventDO event = new PayYeepayEventDO().setEventId(eventId)
                .setEventNo(externalId).setEventType(eventType)
                .setMerchantNo(text(payload, "merchantNo", "parentMerchantNo"))
                .setRequestId(text(payload, "requestId", "divideRequestId", "divideBackRequestId", "refundRequestId", "requestNo"))
                .setFundOrderNo(text(payload, "orderId", "outTradeNo"))
                // 回放使用脱敏摘要；已验签原文以独立外部密钥加密后保存在同一事务中。
                .setPayload(toJsonString(sanitizeEventPayload(payload)))
                .setEncryptedOriginal(encryptedOriginal)
                .setRawDataRef(encryptedOriginal == null ? "sha256:" + digestEventPayload(payload)
                        : encryptedOriginalReference(encryptedOriginal))
                .setDecryptedSummary(toJsonString(sanitizeEventPayload(eventSummary(payload))))
                .setStatus(0).setAckStatus(1).setRetryCount(0)
                .setEventTime(LocalDateTime.now());
        try {
            yeepayEventMapper.insert(event);
            return true;
        } catch (Exception duplicate) {
            // 唯一索引兜底并发回调，回调入口仍然快速返回 SUCCESS。
            if (yeepayEventMapper.selectByEventId(eventId) != null) {
                return false;
            }
            throw duplicate;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markYeepayEventProcessed(String eventType, Map<String, Object> payload) {
        updateYeepayEvent(eventType, payload, PayYeepayEventStatusEnum.PROCESSED.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markYeepayEventFailed(String eventType, Map<String, Object> payload, Throwable error) {
        String message = "易宝事件处理失败" + (error == null ? "" : ": " + error.getClass().getSimpleName());
        updateYeepayEvent(eventType, payload, PayYeepayEventStatusEnum.FAILED.getStatus(),
                StrUtil.sub(message, 0, 512));
    }

    private void updateYeepayEvent(String eventType, Map<String, Object> payload,
                                   Integer status, String lastError) {
        String eventId = eventKey(eventType, eventExternalId(payload), payload);
        PayYeepayEventDO event = yeepayEventMapper.selectByEventId(eventId);
        if (event == null) {
            return;
        }
        Integer retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        updateYeepayEventById(event.getId(), status, lastError,
                status == PayYeepayEventStatusEnum.FAILED.getStatus() ? retryCount + 1 : retryCount);
    }

    private void updateYeepayEventById(Long eventId, Integer status, String lastError, Integer retryCount) {
        yeepayEventMapper.updateById(new PayYeepayEventDO().setId(eventId).setStatus(status)
                .setLastError(StrUtil.blankToDefault(lastError, "")).setRetryCount(retryCount)
                .setProcessedTime(status == PayYeepayEventStatusEnum.PROCESSED.getStatus()
                        ? LocalDateTime.now() : null));
    }

    static String eventExternalId(Map<String, Object> payload) {
        String externalId = text(payload, "eventId", "eventNo");
        if (StrUtil.isNotBlank(externalId)) {
            return externalId;
        }
        Map<String, Object> stable = new LinkedHashMap<>();
        for (String key : List.of("orderId", "outTradeNo", "refundRequestId", "refundOrderId",
                "requestId", "tradeNo", "tradeOrderNo", "uniqueRefundNo", "refundNo",
                "divideRequestId", "divideBackRequestId", "divideNo", "divideBackNo", "requestNo", "applicationNo",
                "merchantNo", "parentMerchantNo",
                "status", "payStatus", "tradeStatus", "clearingStatus", "clearStatus",
                "divideStatus", "refundStatus", "divideBackStatus", "merchantStatus", "auditStatus", "code", "amount",
                "orderAmount", "refundAmount", "divideAmount", "divideDetailNo",
                "divideBackDetailNo", "channelOrderId", "channelRefundNo")) {
            Object value = payload == null ? null : payload.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                stable.put(key, value);
            }
        }
        addStableDetailIdentifiers(stable, payload, "divideDetail", "divideDetailNo");
        addStableDetailIdentifiers(stable, payload, "divideBackDetail", "divideBackDetailNo");
        boolean hasBusinessIdentifier = stable.keySet().stream()
                .anyMatch(key -> EVENT_IDENTIFIER_KEYS.contains(key) || key.endsWith("Ids"));
        if (!hasBusinessIdentifier) {
            throw new IllegalArgumentException("易宝回调缺少业务稳定标识，拒绝建立事件幂等键");
        }
        // Canonicalize only the stable business fields. Timestamps and signature envelope fields
        // are intentionally excluded so a provider retry maps to the same local event.
        return digestEventPayload(stable);
    }

    @SuppressWarnings("unchecked")
    private static void addStableDetailIdentifiers(Map<String, Object> stable,
                                                    Map<String, Object> payload,
                                                    String listKey, String detailKey) {
        Object value = payload == null ? null : payload.get(listKey);
        if (!(value instanceof Collection<?> collection)) {
            return;
        }
        List<String> identifiers = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                Object identifier = ((Map<String, Object>) map).get(detailKey);
                if (identifier != null && StrUtil.isNotBlank(String.valueOf(identifier))) {
                    identifiers.add(String.valueOf(identifier));
                }
            }
        }
        if (!identifiers.isEmpty()) {
            identifiers.sort(String::compareTo);
            stable.put(listKey + "Ids", identifiers);
        }
    }

    private static String eventKey(String eventType, String externalId, Map<String, Object> payload) {
        Object trustedChannelId = payload == null ? null : payload.get("_trustedChannelId");
        // 易宝事件号可以是渠道级编号；相同租户的两个渠道也必须各自接收同号事件。
        return eventType + ":" + (trustedChannelId == null ? "" : trustedChannelId + ":") + externalId;
    }

    private static String encryptedOriginalReference(String encryptedOriginal) {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(encryptedOriginal.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("易宝回调原文引用生成失败");
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void retryDivide(Long fundOrderId) {
        self.prepareDivideRetry(fundOrderId);
        self.processDivide(fundOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void prepareDivideRetry(Long fundOrderId) {
        fundOrderMapper.lockById(fundOrderId);
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        if (order == null) throw new IllegalArgumentException("资金主单不存在");
        if (Objects.equals(order.getClearingStatus(), PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus())) {
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId)
                    .setClearingStatus(PayFundOrderStatusEnum.PROCESSING.getStatus()).setRetryCount(0));
        }
        PayDivideRequestDO request = divideRequestMapper.selectByFundOrderId(fundOrderId);
        if (request != null && Objects.equals(request.getStatus(), PayDivideRequestStatusEnum.MANUAL_REQUIRED.getStatus())) {
            // 回调与重试共享资金行锁，并以状态 CAS 防止陈旧读取覆盖成功结果。
            divideRequestMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PayDivideRequestDO>()
                    .eq(PayDivideRequestDO::getId, request.getId())
                    .eq(PayDivideRequestDO::getStatus, PayDivideRequestStatusEnum.MANUAL_REQUIRED.getStatus())
                    .set(PayDivideRequestDO::getStatus, PayDivideRequestStatusEnum.PROCESSING.getStatus())
                    .set(PayDivideRequestDO::getRetryCount, 0)
                    .set(PayDivideRequestDO::getNextRetryTime, LocalDateTime.now().minusSeconds(1))
                    .set(PayDivideRequestDO::getChannelErrorMsg, ""));
            fundOrderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PayFundOrderDO>()
                    .eq(PayFundOrderDO::getId, fundOrderId)
                    .ne(PayFundOrderDO::getDivideStatus, PayFundOrderStatusEnum.SUCCESS.getStatus())
                    .set(PayFundOrderDO::getDivideStatus, PayFundOrderStatusEnum.PROCESSING.getStatus()));
        }
        fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId).setNextRetryTime(LocalDateTime.now().minusSeconds(1)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void resumeManualClearing(Long fundOrderId) {
        fundOrderMapper.lockById(fundOrderId);
        PayFundOrderDO order = fundOrderMapper.selectById(fundOrderId);
        if (order != null && Objects.equals(order.getClearingStatus(), PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus())) {
            fundOrderMapper.updateById(new PayFundOrderDO().setId(fundOrderId)
                    .setClearingStatus(PayFundOrderStatusEnum.PROCESSING.getStatus()).setRetryCount(0));
        }
    }

    private static LocalDateTime channelCompletionTime(Map<String, Object> payload, String field) {
        String value = text(payload, field);
        if (StrUtil.isBlank(value)) return LocalDateTime.now();
        for (String format : List.of("yyyy-MM-dd HH:mm:ss", "yyyyMMddHHmmss", "yyyy-MM-dd'T'HH:mm:ss")) {
            try { return LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern(format)); }
            catch (java.time.format.DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("易宝完成时间格式无法识别");
    }

    private static LocalDateTime nextRetry(YeepayPayClientConfig config, int count) {
        return LocalDateTime.now().plusSeconds(Math.min(300L,
                config.getRetryBackoffSeconds().longValue() << Math.min(count, 6)));
    }

    private static String yeepayOrderId(PayFundOrderDO fundOrder) {
        if (fundOrder == null) return null;
        return StrUtil.blankToDefault(fundOrder.getPayOrderNo(), fundOrder.getChannelOrderNo());
    }

    private static Map<String, Object> queryDivide(YeepayPayClient client, PayFundOrderDO fundOrder,
                                                    String requestNo) {
        if (StrUtil.isNotBlank(fundOrder.getCollectionMerchantNo())) {
            return client.queryDivide(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), requestNo, fundOrder.getParentMerchantNo(), fundOrder.getCollectionMerchantNo());
        }
        if (StrUtil.isBlank(fundOrder.getPayOrderNo())) {
            return client.queryDivide(fundOrder.getChannelOrderNo(), requestNo);
        }
        return client.queryDivide(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), requestNo);
    }

    private static Map<String, Object> applyDivide(YeepayPayClient client, PayFundOrderDO fundOrder,
                                                    String requestNo, List<Map<String, Object>> details) {
        if (StrUtil.isNotBlank(fundOrder.getCollectionMerchantNo())) {
            return client.applyDivide(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), requestNo, details, fundOrder.getParentMerchantNo(), fundOrder.getCollectionMerchantNo());
        }
        if (StrUtil.isBlank(fundOrder.getPayOrderNo())) {
            return client.applyDivide(fundOrder.getChannelOrderNo(), requestNo, details);
        }
        return client.applyDivide(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), requestNo, details);
    }

    private static Map<String, Object> queryDivideBack(YeepayPayClient client, PayFundOrderDO fundOrder,
                                                        String requestNo) {
        if (StrUtil.isNotBlank(fundOrder.getCollectionMerchantNo())) {
            return client.queryDivideBack(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), requestNo, fundOrder.getParentMerchantNo(), fundOrder.getCollectionMerchantNo());
        }
        if (StrUtil.isBlank(fundOrder.getPayOrderNo())) {
            return client.queryDivideBack(fundOrder.getChannelOrderNo(), requestNo);
        }
        return client.queryDivideBack(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), requestNo);
    }

    private static Map<String, Object> applyDivideBack(YeepayPayClient client, PayFundOrderDO fundOrder,
                                                        String divideRequestNo, String divideBackRequestNo,
                                                        List<Map<String, Object>> details) {
        if (StrUtil.isNotBlank(fundOrder.getCollectionMerchantNo())) {
            return client.applyDivideBack(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), divideRequestNo,
                divideBackRequestNo, details, fundOrder.getParentMerchantNo(), fundOrder.getCollectionMerchantNo());
        }
        if (StrUtil.isBlank(fundOrder.getPayOrderNo())) {
            return client.applyDivideBack(fundOrder.getChannelOrderNo(), divideRequestNo, divideBackRequestNo,
                    details);
        }
        return client.applyDivideBack(yeepayOrderId(fundOrder), fundOrder.getChannelOrderNo(), divideRequestNo,
                divideBackRequestNo, details);
    }

    private boolean isYeepay(PayChannelDO channel) {
        return channel != null && PayChannelEnum.YEEPAY_WX_LITE.getCode().equals(channel.getCode());
    }

    private int getMaxRecipients(PayChannelDO channel) {
        if (channel == null || !(channel.getConfig() instanceof YeepayPayClientConfig config)
                || config.getMaxDivideRecipients() == null) return DEFAULT_MAX_RECIPIENTS;
        return Math.min(DEFAULT_MAX_RECIPIENTS, config.getMaxDivideRecipients());
    }

    private YeepayPayClient getYeepayClient(PayChannelDO channel) {
        if (!isYeepay(channel)) throw new IllegalArgumentException("当前渠道不是易宝支付");
        PayClient<?> client = channelService.getPayClient(channel.getId());
        if (!(client instanceof YeepayPayClient yeepay)) throw new IllegalStateException("易宝支付客户端未初始化");
        return yeepay;
    }

    static Integer parseChannelStatus(Map<String, Object> payload) {
        // YOP wraps the business object with an API code (for example OPR00000). That code only
        // says the HTTP/API call was accepted; it is not a clearing/divide/refund state. Prefer
        // the operation-specific state and then the generic business status, and never infer a
        // final state from code alone.
        String status = text(payload, "clearingStatus", "clearStatus", "divideStatus", "refundStatus",
                "divideBackStatus", "status");
        if (status == null) return null;
        return switch (status.toUpperCase()) {
            case "SUCCESS", "COMPLETED", "00", "0", "200" -> PayDivideRequestStatusEnum.SUCCESS.getStatus();
            case "PROCESSING", "PENDING", "WAITING" -> PayDivideRequestStatusEnum.PROCESSING.getStatus();
            case "FAIL", "FAILED", "FAILURE", "CLOSED", "CANCELLED", "ERROR" ->
                    PayDivideRequestStatusEnum.FAILED.getStatus();
            default -> null;
        };
    }

    private static String text(Map<String, Object> payload, String... keys) {
        if (payload == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) return String.valueOf(value);
        }
        return null;
    }

    static String digestEventPayload(Map<String, Object> payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(toCanonicalEventValue(payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成易宝事件去重键失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String toCanonicalEventValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Map<String, Object> canonical = new LinkedHashMap<>();
            sorted.forEach((key, item) -> canonical.put(key,
                    JsonUtils.parseObject(toCanonicalEventValue(item), Object.class)));
            return toJsonString(canonical);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> canonical = new ArrayList<>(collection.size());
            for (Object item : collection) {
                canonical.add(JsonUtils.parseObject(toCanonicalEventValue(item), Object.class));
            }
            return toJsonString(canonical);
        }
        return toJsonString(value);
    }

    private static Map<String, Object> eventSummary(Map<String, Object> payload) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String key : List.of("eventId", "eventNo", "eventType", "orderId", "outTradeNo",
                "requestId", "divideRequestId", "divideBackRequestId", "refundRequestId",
                "merchantNo", "status", "code", "message", "msg")) {
            Object value = payload == null ? null : payload.get(key);
            if (value != null) {
                summary.put(key, value);
            }
        }
        return summary;
    }

    private static Map<String, Object> sanitizeEventPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) sanitizeEventValue(payload);
        return sanitized;
    }

    private static Object sanitizeEventValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                if (isSensitiveEventKey(key)) {
                    continue;
                }
                sanitized.put(key, sanitizeEventValue(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                sanitized.add(sanitizeEventValue(item));
            }
            return sanitized;
        }
        return value;
    }

    private static boolean isSensitiveEventKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return normalized.equals("sign") || normalized.endsWith("signature")
                || normalized.contains("openid") || normalized.contains("idcard")
                || normalized.contains("bankcard") || normalized.contains("bankaccount")
                || normalized.equals("accountno") || normalized.equals("privatekey")
                || normalized.contains("secret") || normalized.equals("appkey")
                || normalized.contains("authorization") || normalized.equals("token");
    }

    @SuppressWarnings("unchecked")
    private static String divideDetailNo(Map<String, Object> response, int index, String fallback) {
        List<Map<String, Object>> details = callbackDetails(response);
        if (index >= 0 && index < details.size()) {
            String detailNo = text(details.get(index), "divideDetailNo", "detailNo", "divideBackDetailNo");
            if (StrUtil.isNotBlank(detailNo)) {
                return detailNo;
            }
        }
        return StrUtil.blankToDefault(text(response, "divideDetailNo", "detailNo"), fallback);
    }
}
