package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.exception.PayClientException;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.AbstractPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.enums.PayOrderDisplayModeEnum;
import com.yeepay.yop.sdk.http.YopContentType;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import com.yeepay.yop.sdk.model.YopRequestConfig;
import com.yeepay.yop.sdk.service.common.YopClient;
import com.yeepay.yop.sdk.service.common.YopCallbackEngine;
import com.yeepay.yop.sdk.service.common.callback.YopCallback;
import com.yeepay.yop.sdk.service.common.callback.YopCallbackRequest;
import com.yeepay.yop.sdk.service.common.callback.YopCallbackResponse;
import com.yeepay.yop.sdk.service.common.callback.handler.YopCallbackHandler;
import com.yeepay.yop.sdk.service.common.callback.handler.YopCallbackHandlerFactory;
import com.yeepay.yop.sdk.service.common.request.YopRequest;
import com.yeepay.yop.sdk.service.common.response.YopResponse;
import com.yeepay.yop.sdk.service.common.response.YosUploadResponse;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;

/**
 * 易宝聚合支付客户端。
 *
 * <p>易宝接口的签名和请求格式集中在本类，业务服务只使用明确的领域方法，避免散落拼接
 * form 参数和密钥。</p>
 */
@Slf4j
public class YeepayPayClient extends AbstractPayClient<YeepayPayClientConfig> {

    private static final String PRE_PAY_PATH = "/rest/v1.0/aggpay/pre-pay";
    private static final String PAY_QUERY_PATH = "/rest/v1.0/trade/order/query";
    private static final String REFUND_PATH = "/rest/v1.0/trade/refund";
    private static final String REFUND_QUERY_PATH = "/rest/v1.0/trade/refund/query";
    private static final String MERCHANT_REGISTER_PATH = "/rest/v2.0/mer/register/contribute/merchant";
    private static final String MICRO_MERCHANT_REGISTER_PATH = "/rest/v2.0/mer/register/contribute/micro";
    private static final String MERCHANT_QUERY_PATH = "/rest/v2.0/mer/register/query";
    private static final String MERCHANT_STATUS_QUERY_PATH = "/rest/v1.0/mer/status/query";
    private static final String MERCHANT_NOTIFY_REPEAT_PATH = "/rest/v2.0/mer/notify/repeat";
    private static final String QUALIFICATION_UPLOAD_PATH = "/yos/v1.0/sys/merchant/qual/upload";
    private static final String DIVIDE_PATH = "/rest/v1.0/divide/apply";
    private static final String DIVIDE_QUERY_PATH = "/rest/v1.0/divide/query";
    private static final String DIVIDE_BACK_PATH = "/rest/v1.0/divide/back";
    private static final String DIVIDE_BACK_QUERY_PATH = "/rest/v1.0/divide/back/query";
    private static final String PAYMENT_CALLBACK_PATH = "/pay/notify/yeepay/payment";
    private static final String REFUND_CALLBACK_PATH = "/pay/notify/yeepay/refund";
    private static final String CLEARING_CALLBACK_PATH = "/pay/notify/yeepay/clearing";
    private static final String DIVIDE_CALLBACK_PATH = "/pay/notify/yeepay/divide";
    private static final String DIVIDE_BACK_CALLBACK_PATH = "/pay/notify/yeepay/divide-back";
    private static final String MERCHANT_CALLBACK_PATH = "/pay/notify/yeepay/merchant";
    private static final DateTimeFormatter YEEPAY_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SENSITIVE_PAYLOAD_KEYS = Set.of(
            "sign", "signature", "openid", "idcard", "bankcard", "bankaccount",
            "accountno", "privatekey", "secret", "appkey", "authorization", "token");

    /**
     * 每个渠道客户端独立限流，避免多个支付应用共享一个全局桶而相互影响。
     * 时间桶只负责平滑请求间隔，最终一致性重试仍由资金任务负责。
     */
    private final Object rateLimitMonitor = new Object();
    private long nextRequestNanos;

    /**
     * YOP keeps callback handlers in a process-wide registry. Registering a temporary handler
     * therefore has to be paired with handle() under one lock, otherwise two channels can capture
     * each other's callback while the SDK is parsing and signing the acknowledgement.
     */
    private static final Object CALLBACK_ENGINE_LOCK = new Object();

    private volatile YopClient yopClient;

    public YeepayPayClient(Long channelId, YeepayPayClientConfig config) {
        super(channelId, PayChannelEnum.YEEPAY_WX_LITE.getCode(), config);
    }

    @Override
    protected boolean redactFailureDetails() {
        return true;
    }

    @Override
    protected void doInit() {
        // 配置校验已经在 PayChannelService 中执行，这里只做默认值兜底。
        if (StrUtil.isBlank(config.getServerUrl())) {
            config.setServerUrl(YeepayPayClientConfig.DEFAULT_SERVER_URL);
        }
        if (StrUtil.isBlank(config.getYosServerUrl())) {
            config.setYosServerUrl("https://yos.yeepay.com/yop-center");
        }
        if (config.getTimeoutSeconds() == null) {
            config.setTimeoutSeconds(10);
        }
        if (config.getConnectTimeoutSeconds() == null) {
            config.setConnectTimeoutSeconds(5);
        }
        if (config.getMaxRetries() == null) {
            config.setMaxRetries(3);
        }
        if (config.getRetryBackoffSeconds() == null) {
            config.setRetryBackoffSeconds(3);
        }
        if (config.getRateLimitPerSecond() == null) {
            config.setRateLimitPerSecond(20);
        }
        if (config.getMaxDivideRecipients() == null) {
            config.setMaxDivideRecipients(5);
        }
    }

    @Override
    protected PayOrderRespDTO doUnifiedOrder(PayOrderUnifiedReqDTO reqDTO) {
        config.validateFullAmountDivideGuarantee();
        requireMerchantSnapshot(reqDTO.getParentMerchantNo(), reqDTO.getCollectionMerchantNo());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("payWay", "MINI_PROGRAM");
        params.put("channel", "WECHAT");
        params.put("scene", "ONLINE");
        params.put("appId", config.getAppId());
        params.put("userId", firstNonBlank(extra(reqDTO, "openId"), extra(reqDTO, "openid")));
        params.put("userIp", reqDTO.getUserIp());
        params.put("orderId", reqDTO.getOutTradeNo());
        params.put("orderAmount", money(reqDTO.getPrice()));
        params.put("goodsName", reqDTO.getSubject());
        params.put("expiredTime", reqDTO.getExpireTime().format(YEEPAY_DATETIME_FORMATTER));
        params.put("notifyUrl", callbackUrl(config.getPaymentNotifyUrl(), reqDTO.getNotifyUrl()));
        params.put("csUrl", callbackUrl(config.getClearingNotifyUrl(), null));
        params.put("fundProcessType", config.getFundProcessType());
        params.put("parentMerchantNo", reqDTO.getParentMerchantNo());
        params.put("merchantNo", reqDTO.getCollectionMerchantNo());
        if (StrUtil.isNotBlank(config.getFeeSubsidyInfo())) {
            params.put("feeSubsidyInfo", config.getFeeSubsidyInfo());
        }
        // The official aggregate-pay contract requires divideDetail whenever the order is
        // marked for settlement/division. DELAY_SETTLE still sends the immutable snapshot here;
        // /divide/apply is the later execution request after payment/clearing, not a replacement
        // for the pre-pay declaration.
        String divideDetail = extra(reqDTO, "divideDetail");
        if (StrUtil.isBlank(divideDetail)) {
            throw new IllegalArgumentException("需要分账的聚合支付必须提供 divideDetail");
        }
        params.put("divideDetail", divideDetail);

        Map<String, Object> response = post(PRE_PAY_PATH, params);
        if (!isSuccess(response)) {
            return PayOrderRespDTO.closedOf(text(response, "code"), text(response, "message", "msg"),
                    reqDTO.getOutTradeNo(), sanitizePayload(response));
        }
        // MINI_PROGRAM + WECHAT returns the complete WeChat invocation payload in the
        // documented prePayTn field. Do not accept a raw prepay_id or an SDK/provider-specific
        // alias: those values cannot carry YeePay's RSA paySign and are not safe to manufacture.
        String prePayTn = text(response, "prePayTn");
        if (StrUtil.isBlank(prePayTn)) {
            return PayOrderRespDTO.closedOf("MISSING_PREPAY_TN", "易宝未返回预支付凭证",
                    reqDTO.getOutTradeNo(), sanitizePayload(response));
        }
        String channelOrderNo = firstNonBlank(text(response, "uniqueOrderNo", "channelOrderNo", "channelOrderId"),
                reqDTO.getOutTradeNo());
        PayOrderRespDTO result = PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.APP.getMode(),
                toJsonString(buildMiniProgramPayParams(prePayTn, response)), reqDTO.getOutTradeNo(),
                sanitizePayload(response));
        result.setChannelOrderNo(channelOrderNo);
        result.setChannelUserId(text(response, "userId", "openId"));
        return result;
    }

    /**
     * 将易宝官方 prePayTn 转换为现有小程序适配层使用的统一参数。
     * prePayTn 必须是官方返回的完整 JSON；外层响应字段和 raw prepay_id 均不能补齐或替代它。
     */
    public Map<String, String> buildMiniProgramPayParams(String prePayTn, Map<String, Object> response) {
        if (StrUtil.isBlank(prePayTn)) {
            throw new IllegalArgumentException("易宝预支付凭证不能为空");
        }
        Map<String, Object> payload = parsePrePayTnPayload(prePayTn);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("appId", text(payload, "appId"));
        result.put("timeStamp", text(payload, "timeStamp"));
        result.put("nonceStr", text(payload, "nonceStr"));
        // MINI_PROGRAM + WECHAT returns prePayTn as the complete JSON payload documented by
        // YeePay. A raw token is not a valid substitute for that payload: manufacturing
        // `prepay_id=` would lose the appId, nonce and YeePay-generated RSA paySign.
        result.put("package", text(payload, "package"));
        // yudao-mall-uniapp 的微信适配层历史上读取 packageValue，保留别名以兼容旧版本。
        result.put("packageValue", result.get("package"));
        result.put("signType", text(payload, "signType"));
        result.put("paySign", text(payload, "paySign"));
        if (StrUtil.isBlank(result.get("appId")) || StrUtil.isBlank(result.get("timeStamp"))
                || StrUtil.isBlank(result.get("nonceStr")) || StrUtil.isBlank(result.get("package"))
                || StrUtil.isBlank(result.get("signType")) || StrUtil.isBlank(result.get("paySign"))) {
            throw new IllegalArgumentException("易宝 prePayTn 未包含完整的微信小程序支付参数");
        }
        return result;
    }

    @Override
    protected PayOrderRespDTO doParseOrderNotify(Map<String, String> params, String body,
                                                  Map<String, String> headers) {
        return parseYeepayOrderNotify(parseResponse(params, body, headers, PAYMENT_CALLBACK_PATH));
    }

    public PayOrderRespDTO parseYeepayOrderNotify(Map<String, Object> response) {
        Integer status = parseOrderStatus(text(response, "status", "payStatus", "tradeStatus", "result"));
        if (status == null) {
            throw new IllegalArgumentException("易宝支付回调缺少可识别的支付状态");
        }
        String outTradeNo = text(response, "orderId", "outTradeNo", "uniqueOrderNo");
        String channelOrderNo = firstNonBlank(text(response, "uniqueOrderNo", "channelOrderNo", "channelOrderId",
                        "channelTrxId"),
                outTradeNo);
        return PayOrderRespDTO.of(status,
                channelOrderNo,
                text(response, "userId", "openId", "openid"),
                parseTime(text(response, "paySuccessDate", "successTime", "paySuccessTime", "completeTime")),
                outTradeNo, sanitizePayload(response));
    }

    @Override
    protected PayOrderRespDTO doGetOrder(String outTradeNo) {
        return getOrder(outTradeNo, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    public PayOrderRespDTO getOrder(String outTradeNo, String parentMerchantNo, String collectionMerchantNo) {
        return getOrder(outTradeNo, parentMerchantNo, collectionMerchantNo, null);
    }

    public PayOrderRespDTO getOrder(String outTradeNo, String parentMerchantNo, String collectionMerchantNo,
                                   Integer expectedPrice) {
        requireMerchantSnapshot(parentMerchantNo, collectionMerchantNo);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("orderId", outTradeNo);
        query.put("parentMerchantNo", parentMerchantNo);
        query.put("merchantNo", collectionMerchantNo);
        Map<String, Object> response = query(PAY_QUERY_PATH, query);
        Integer status = parseOrderStatus(text(response, "status", "payStatus", "tradeStatus", "result"));
        boolean success = PayOrderStatusEnum.SUCCESS.getStatus().equals(status);
        validatePaymentQueryReference(response, "orderId", outTradeNo, success);
        validatePaymentQueryReference(response, "parentMerchantNo", parentMerchantNo, success);
        validatePaymentQueryReference(response, "merchantNo", collectionMerchantNo, success);
        String amount = text(response, "orderAmount");
        if (success && (amount == null || !isSuccess(response))) {
            throw new IllegalArgumentException("易宝支付成功查询缺少金额或业务结果不一致");
        }
        if (amount != null) {
            int actual;
            try { actual = new BigDecimal(amount).movePointRight(2).intValueExact(); }
            catch (NumberFormatException | ArithmeticException invalid) {
                throw new IllegalArgumentException("易宝支付查询金额不是整数分");
            }
            if (actual <= 0 || expectedPrice != null && actual != expectedPrice) {
                throw new IllegalArgumentException("易宝支付查询金额与原支付单不一致");
            }
        }
        LocalDateTime completedTime = parseTime(text(response, "paySuccessDate", "successTime", "paySuccessTime"));
        if (success && (StrUtil.isBlank(text(response, "uniqueOrderNo")) || completedTime == null)) {
            throw new IllegalArgumentException("易宝支付成功查询缺少渠道流水号或完成时间");
        }
        if (!isSuccess(response) && status == null) {
            return PayOrderRespDTO.closedOf(text(response, "code"), text(response, "message", "msg"),
                    outTradeNo, sanitizePayload(response));
        }
        return PayOrderRespDTO.of(status == null ? PayOrderStatusEnum.WAITING.getStatus() : status,
                firstNonBlank(text(response, "uniqueOrderNo", "channelOrderNo", "channelOrderId", "channelTrxId"),
                        outTradeNo),
                text(response, "userId", "openId"), completedTime,
                outTradeNo, sanitizePayload(response));
    }

    private static void validatePaymentQueryReference(Map<String, Object> response, String field,
                                                       String expected, boolean required) {
        String actual = text(response, field);
        if (required && actual == null || actual != null && !Objects.equals(actual, expected)) {
            throw new IllegalArgumentException("易宝支付查询身份与原支付单不一致或不完整");
        }
    }

    @Override
    protected PayRefundRespDTO doUnifiedRefund(PayRefundUnifiedReqDTO reqDTO) {
        String parentMerchantNo = firstNonBlank(reqDTO.getParentMerchantNo(), config.getParentMerchantNo());
        String collectionMerchantNo = firstNonBlank(reqDTO.getCollectionMerchantNo(), config.getCollectionMerchantNo());
        Map<String, Object> params = new LinkedHashMap<>();
        // 易宝退款的订单标识是三选一。uniqueOrderNo 是聚合支付返回的渠道订单号，
        // 存在时优先使用它，不能再同时发送 orderId。
        if (StrUtil.isNotBlank(reqDTO.getUniqueOrderNo())) {
            params.put("uniqueOrderNo", reqDTO.getUniqueOrderNo());
        } else {
            params.put("orderId", reqDTO.getOutTradeNo());
        }
        params.put("refundRequestId", reqDTO.getOutRefundNo());
        params.put("refundAmount", money(reqDTO.getRefundPrice()));
        params.put("description", reqDTO.getReason());
        params.put("notifyUrl", callbackUrl(config.getRefundNotifyUrl(), reqDTO.getNotifyUrl()));
        params.put("parentMerchantNo", parentMerchantNo);
        params.put("merchantNo", collectionMerchantNo);
        if (reqDTO.getFundAllocation() != null && !reqDTO.getFundAllocation().isEmpty()) {
            List<Map<String, Object>> divideBackDetails = new ArrayList<>();
            boolean deductPendingDivideAccount = false;
            for (PayRefundFundAllocationDTO allocation : reqDTO.getFundAllocation()) {
                if (allocation == null || Boolean.TRUE.equals(allocation.getDivideBackCompleted())) {
                    continue;
                }
                if (StrUtil.isNotBlank(allocation.getDivBackAccountType())) {
                    if (!"DIVIDE_ACCOUNT".equals(allocation.getDivBackAccountType())) {
                        throw new IllegalArgumentException("易宝退款扣账账户类型必须为 DIVIDE_ACCOUNT");
                    }
                    if (StrUtil.isNotBlank(allocation.getDivideDetailNo())) {
                        throw new IllegalArgumentException("易宝待分账账户扣款明细不能同时包含 divideDetailNo");
                    }
                    // 尚未分账时资金仍属于原收款方。多个本地商家分配只能折叠成一条
                    // 收款方扣账明细，且官方协议禁止该明细携带金额、原分账号和原因。
                    deductPendingDivideAccount = true;
                    continue;
                }
                if (StrUtil.isBlank(allocation.getLedgerNo())) {
                    throw new IllegalArgumentException("易宝退款资金明细缺少归集商编");
                }
                if (StrUtil.isBlank(allocation.getDivideDetailNo())) {
                    throw new IllegalArgumentException("易宝已分账退款资金明细缺少 divideDetailNo");
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("ledgerNo", allocation.getLedgerNo());
                detail.put("divideDetailNo", allocation.getDivideDetailNo());
                detail.put("amount", new BigDecimal(money(allocation.getAmount())));
                detail.put("divideBackReason", firstNonBlank(reqDTO.getReason(), "用户退款"));
                divideBackDetails.add(detail);
            }
            if (deductPendingDivideAccount) {
                divideBackDetails.add(Map.of("ledgerNo", collectionMerchantNo,
                        "divBackAccountType", "DIVIDE_ACCOUNT"));
            }
            if (!divideBackDetails.isEmpty()) {
                params.put("divideBackDetail", divideBackDetails);
            }
        }
        Map<String, Object> response = post(REFUND_PATH, params);
        if (!isSuccess(response)) {
            return PayRefundRespDTO.failureOf(text(response, "code"), text(response, "message", "msg"),
                    reqDTO.getOutRefundNo(), sanitizePayload(response));
        }
        Integer status = parseRefundStatus(text(response, "status", "refundStatus", "result"));
        String channelRefundNo = firstNonBlank(text(response, "uniqueRefundNo"),
                text(response, "refundOrderId", "channelRefundNo", "refundId"));
        if (Objects.equals(status, PayRefundStatusEnum.SUCCESS.getStatus())) {
            return PayRefundRespDTO.successOf(channelRefundNo, parseTime(text(response, "successTime")),
                    reqDTO.getOutRefundNo(), sanitizePayload(response));
        }
        if (Objects.equals(status, PayRefundStatusEnum.FAILURE.getStatus())) {
            return PayRefundRespDTO.failureOf(text(response, "code"), text(response, "message", "msg"),
                    reqDTO.getOutRefundNo(), sanitizePayload(response));
        }
        return PayRefundRespDTO.waitingOf(channelRefundNo, reqDTO.getOutRefundNo(), sanitizePayload(response));
    }

    @Override
    protected PayRefundRespDTO doParseRefundNotify(Map<String, String> params, String body,
                                                   Map<String, String> headers) {
        return parseYeepayRefundNotify(parseResponse(params, body, headers, REFUND_CALLBACK_PATH));
    }

    public PayRefundRespDTO parseYeepayRefundNotify(Map<String, Object> response) {
        Integer status = parseRefundStatus(text(response, "status", "refundStatus", "result"));
        if (status == null) {
            throw new IllegalArgumentException("易宝退款回调缺少可识别的退款状态");
        }
        // refundRequestId is our stable idempotency key. uniqueRefundNo is generated by
        // YeePay and belongs in channelRefundNo, never in PayRefundDO.no.
        String refundNo = text(response, "refundRequestId", "outRefundNo", "refundId");
        if (Objects.equals(status, PayRefundStatusEnum.SUCCESS.getStatus())) {
            return PayRefundRespDTO.successOf(firstNonBlank(text(response, "uniqueRefundNo"),
                            text(response, "refundOrderId", "channelRefundNo", "refundId")),
                    parseTime(text(response, "refundSuccessDate", "successTime")), refundNo,
                    sanitizePayload(response));
        }
        if (Objects.equals(status, PayRefundStatusEnum.FAILURE.getStatus())) {
            return PayRefundRespDTO.failureOf(text(response, "code"), text(response, "message", "msg"), refundNo,
                    sanitizePayload(response));
        }
        return PayRefundRespDTO.waitingOf(firstNonBlank(text(response, "uniqueRefundNo"),
                        text(response, "refundOrderId", "channelRefundNo", "refundId")), refundNo,
                sanitizePayload(response));
    }

    @Override
    protected PayRefundRespDTO doGetRefund(String outTradeNo, String outRefundNo) {
        return getRefund(outTradeNo, outRefundNo, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    /** 查询原订单退款时使用创建支付单时保存的商编。 */
    public PayRefundRespDTO getRefund(String outTradeNo, String outRefundNo,
                                      String parentMerchantNo, String collectionMerchantNo) {
        return getRefund(outTradeNo, outRefundNo, parentMerchantNo, collectionMerchantNo, null);
    }

    /** 原退款金额与身份均由本地持久化记录提供，成功结果必须包含可完整核对的官方字段。 */
    public PayRefundRespDTO getRefund(String outTradeNo, String outRefundNo,
                                      String parentMerchantNo, String collectionMerchantNo,
                                      Integer expectedRefundPrice) {
        if (StrUtil.isBlank(outTradeNo) || StrUtil.isBlank(outRefundNo)
                || StrUtil.isBlank(parentMerchantNo) || StrUtil.isBlank(collectionMerchantNo)) {
            throw new IllegalArgumentException("易宝退款查询缺少原订单、退款号或商编快照");
        }
        if (expectedRefundPrice != null && expectedRefundPrice <= 0) {
            throw new IllegalArgumentException("易宝退款查询的原退款金额必须大于零");
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("orderId", outTradeNo);
        query.put("refundRequestId", outRefundNo);
        query.put("parentMerchantNo", parentMerchantNo);
        query.put("merchantNo", collectionMerchantNo);
        Map<String, Object> response = query(REFUND_QUERY_PATH, query);
        Integer status = parseRefundStatus(text(response, "status", "refundStatus", "result"));
        boolean success = Objects.equals(status, PayRefundStatusEnum.SUCCESS.getStatus());
        validateRefundQueryReference(response, "refundRequestId", outRefundNo, success);
        validateRefundQueryReference(response, "orderId", outTradeNo, success);
        validateRefundQueryReference(response, "parentMerchantNo", parentMerchantNo, success);
        validateRefundQueryReference(response, "merchantNo", collectionMerchantNo, success);
        validateRefundQueryAmount(response, expectedRefundPrice, success);
        if (Objects.equals(status, PayRefundStatusEnum.SUCCESS.getStatus())) {
            return PayRefundRespDTO.successOf(firstNonBlank(text(response, "uniqueRefundNo"),
                            text(response, "refundOrderId", "channelRefundNo", "refundId")),
                    parseTime(text(response, "refundSuccessDate", "successTime")), outRefundNo,
                    sanitizePayload(response));
        }
        if (Objects.equals(status, PayRefundStatusEnum.FAILURE.getStatus())) {
            return PayRefundRespDTO.failureOf(text(response, "code"), text(response, "message", "msg"), outRefundNo,
                    sanitizePayload(response));
        }
        return PayRefundRespDTO.waitingOf(firstNonBlank(text(response, "uniqueRefundNo"),
                        text(response, "refundOrderId", "channelRefundNo", "refundId")), outRefundNo,
                sanitizePayload(response));
    }

    private static void validateRefundQueryReference(Map<String, Object> response, String field,
                                                     String expected, boolean required) {
        String actual = text(response, field);
        if ((required && actual == null) || (actual != null && !Objects.equals(expected, actual))) {
            throw new IllegalArgumentException("易宝退款查询缺少完整身份或与原退款记录不一致");
        }
    }

    private static void validateRefundQueryAmount(Map<String, Object> response, Integer expected,
                                                  boolean required) {
        String amount = text(response, "refundAmount");
        if (amount == null) {
            if (required) {
                throw new IllegalArgumentException("易宝退款查询成功结果缺少退款金额");
            }
            return;
        }
        int actual;
        try {
            actual = new BigDecimal(amount).movePointRight(2).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("易宝退款查询金额不是有效的整数分");
        }
        if (actual <= 0 || (expected != null && actual != expected)) {
            throw new IllegalArgumentException("易宝退款查询金额与原退款记录不一致");
        }
    }

    // ========== 易宝资金扩展能力 ==========

    /**
     * 按官方入网 OpenAPI 规范生成请求。身份证、银行卡等原始资料只在当前调用内存中使用，
     * 不落入支付库；YOP SDK 根据官方接口定义对标记为敏感的字段执行加密。
     */
    public Map<String, Object> buildMerchantRegisterParams(Map<String, Object> materials,
                                                            String requestNo, String businessRole) {
        return buildMerchantRegisterParams(materials, requestNo, businessRole, true);
    }

    /**
     * 生成易宝小微商户入网参数。
     *
     * <p>小微入网和企业/个体入网是两个不同的官方 API。小微接口不要求
     * {@code merchantContactInfo}，因此不能复用企业接口的必填校验；同时保留官方要求的
     * {@code merchantSubjectInfo}、{@code merchantCorporationInfo} 和
     * {@code businessAddressInfo}。</p>
     */
    public Map<String, Object> buildMicroMerchantRegisterParams(Map<String, Object> materials,
                                                                 String requestNo, String businessRole) {
        return buildMerchantRegisterParams(materials, requestNo, businessRole, false);
    }

    private Map<String, Object> buildMerchantRegisterParams(Map<String, Object> materials,
                                                              String requestNo, String businessRole,
                                                              boolean requireContactInfo) {
        if (StrUtil.isBlank(requestNo) || requestNo.length() > 64) {
            throw new IllegalArgumentException("易宝入网 requestNo 不能为空且不能超过 64 位");
        }
        if (StrUtil.isBlank(config.getMerchantNotifyUrl())) {
            throw new IllegalStateException("易宝商户入网结果回调地址 merchantNotifyUrl 未配置");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (materials != null) {
            params.putAll(materials);
        }
        params.remove("merchantId");
        params.remove("materialsRef");
        params.remove("materials_ref");
        params.remove("materialsVersion");
        params.remove("materials_version");
        params.put("requestNo", requestNo);
        String role = StrUtil.blankToDefault(businessRole, config.getBusinessRole());
        if (!"SETTLED_MERCHANT".equals(role) && !"SHARE_MERCHANT".equals(role)) {
            throw new IllegalArgumentException("易宝入网 businessRole 不受支持");
        }
        params.put("businessRole", role);
        List<String> requiredFields = new ArrayList<>(List.of("merchantSubjectInfo",
                "merchantCorporationInfo", "businessAddressInfo"));
        if (requireContactInfo) {
            requiredFields.add("merchantContactInfo");
        }
        for (String key : requiredFields) {
            params.put(key, requireJsonObject(params.get(key), key));
        }
        // Optional nested objects are also encoded as JSON strings. This is the wire format
        // declared by the official OpenAPI definitions and avoids SDK form serialization of a
        // Java Map as an implementation-specific value.
        if (!requireContactInfo && params.containsKey("merchantContactInfo")) {
            params.put("merchantContactInfo", requireJsonObject(params.get("merchantContactInfo"),
                    "merchantContactInfo"));
        }
        for (String key : List.of("industryCategoryInfo", "accountInfo", "productInfo")) {
            if (params.containsKey(key) && params.get(key) != null) {
                params.put(key, requireJsonObject(params.get(key), key));
            }
        }
        params.put("notifyUrl", callbackUrl(config.getMerchantNotifyUrl(), null));

        List<String> functionServices = parseStringArray(params.get("functionService"), "functionService");
        if (!functionServices.contains(config.getSharePermission())) {
            functionServices.add(config.getSharePermission());
        }
        params.put("functionService", toJsonString(functionServices));
        Map<String, Object> serviceQualification = requireJsonObject(
                params.get("functionServiceQualificationInfo"), "functionServiceQualificationInfo");
        if (StrUtil.isBlank(text(serviceQualification, "shareScene"))) {
            throw new IllegalArgumentException("易宝开通 SHARE 分账服务必须提供 shareScene");
        }
        params.put("functionServiceQualificationInfo", toJsonString(serviceQualification));
        return params;
    }

    /** 根据商户资料显式选择小微接口，避免把企业资料误发到小微接口。 */
    public boolean isMicroMerchantMaterials(Map<String, Object> materials) {
        String type = text(materials, "merchantType", "merchant_type");
        if (StrUtil.isBlank(type)) {
            Object subject = materials == null ? null : materials.get("merchantSubjectInfo");
            if (subject instanceof Map<?, ?> subjectMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedSubject = new LinkedHashMap<>((Map<String, Object>) subjectMap);
                type = text(typedSubject, "merchantType", "merchant_type");
            } else if (subject instanceof String subjectJson && StrUtil.isNotBlank(subjectJson)) {
                Map<String, Object> typedSubject = JsonUtils.parseMap(subjectJson);
                type = text(typedSubject, "merchantType", "merchant_type");
            }
        }
        if (StrUtil.isBlank(type)) {
            return false;
        }
        return Set.of("MICRO", "MICRO_MERCHANT", "SMALL_MICRO", "SMALL_MICRO_MERCHANT")
                .contains(type.trim().toUpperCase());
    }

    private static Map<String, Object> requireJsonObject(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = new LinkedHashMap<>((Map<String, Object>) map);
            if (!object.isEmpty()) {
                return object;
            }
        } else if (value instanceof String text && StrUtil.isNotBlank(text)) {
            Map<String, Object> object = JsonUtils.parseMap(text);
            if (object != null && !object.isEmpty()) {
                return object;
            }
        }
        throw new IllegalArgumentException("易宝入网字段 " + field + " 必须是非空 JSON 对象");
    }

    private static List<String> parseStringArray(Object value, String field) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof String raw && StrUtil.isNotBlank(raw)) {
            List<String> parsed = JsonUtils.parseArray(raw, String.class);
            if (parsed != null) {
                result.addAll(parsed);
                return result;
            }
        }
        if (value == null) {
            return result;
        }
        throw new IllegalArgumentException("易宝入网字段 " + field + " 必须是 JSON 字符串数组");
    }

    public Map<String, Object> registerMerchant(Map<String, Object> params) {
        return post(MERCHANT_REGISTER_PATH, params);
    }

    public Map<String, Object> registerMicroMerchant(Map<String, Object> params) {
        return post(MICRO_MERCHANT_REGISTER_PATH, params);
    }

    public Map<String, Object> queryMerchant(String requestNo) {
        return queryMerchantProgress(requestNo, null);
    }

    /** 查询入网申请进度；requestNo 与 merchantNo 必须二选一。 */
    public Map<String, Object> queryMerchantProgress(String requestNo, String merchantNo) {
        boolean hasRequestNo = StrUtil.isNotBlank(requestNo);
        boolean hasMerchantNo = StrUtil.isNotBlank(merchantNo);
        if (hasRequestNo == hasMerchantNo) {
            throw new IllegalArgumentException("易宝入网进度查询 requestNo 与 merchantNo 必须二选一");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(hasRequestNo ? "requestNo" : "merchantNo", hasRequestNo ? requestNo : merchantNo);
        return query(MERCHANT_QUERY_PATH, params);
    }

    /** 查询已开通商户的外部状态，官方接口只接收 merchantNo。 */
    public Map<String, Object> queryMerchantStatus(String merchantNo) {
        if (StrUtil.isBlank(merchantNo)) {
            throw new IllegalArgumentException("易宝商户状态查询 merchantNo 不能为空");
        }
        return query(MERCHANT_STATUS_QUERY_PATH, Map.of("merchantNo", merchantNo));
    }

    /** 重复获取短信、邮件或电子签章通知；字段按官方接口定义透传。 */
    public Map<String, Object> repeatMerchantNotify(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("易宝重复获取通知参数不能为空");
        }
        return post(MERCHANT_NOTIFY_REPEAT_PATH, params);
    }

    /**
     * 使用官方 YOS SDK 上传商户资质。merQual 是官方 multipart 字段名，上传响应中的
     * merQualUrl 只能作为入网请求引用，不应直接暴露给前端预览。
     */
    public Map<String, Object> uploadMerchantQualification(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            throw new IllegalArgumentException("易宝商户资质文件不能为空");
        }
        if (StrUtil.isBlank(fileName) || fileName.length() > 255) {
            throw new IllegalArgumentException("易宝商户资质文件名不能为空且不能超过 255 位");
        }
        try {
            acquireRateLimit();
            YopRequestConfig requestConfig = buildRequestConfig();
            YopRequest request = new YopRequest(QUALIFICATION_UPLOAD_PATH, "POST", requestConfig);
            request.addMultiPartFile("merQual", inputStream, fileName);
            YosUploadResponse response = getYopClient().upload(request);
            Map<String, Object> result = parseUploadResult(response);
            if (StrUtil.isBlank(text(result, "merQualUrl"))) {
                throw new PayClientException(new IllegalStateException("易宝资质上传响应缺少 merQualUrl"));
            }
            return result;
        } catch (PayClientException e) {
            throw e;
        } catch (Exception e) {
            throw new PayClientException(e);
        }
    }

    /** 解析资金域回调，所有资金事件共用同一套验签和解密入口。 */
    public Map<String, Object> parseFundNotify(Map<String, String> params, String body,
                                                Map<String, String> headers) {
        return parseFundNotify(params, body, headers, CLEARING_CALLBACK_PATH);
    }

    public Map<String, Object> parsePaymentFundNotify(Map<String, String> params, String body,
                                                      Map<String, String> headers) {
        return parseFundNotify(params, body, headers, PAYMENT_CALLBACK_PATH);
    }

    public Map<String, Object> parseRefundFundNotify(Map<String, String> params, String body,
                                                     Map<String, String> headers) {
        return parseFundNotify(params, body, headers, REFUND_CALLBACK_PATH);
    }

    private Map<String, Object> parseFundNotify(Map<String, String> params, String body,
                                                Map<String, String> headers, String callbackPath) {
        return parseResponse(params, body, headers, callbackPath);
    }

    public Map<String, Object> parseClearingNotify(Map<String, String> params, String body,
                                                   Map<String, String> headers) {
        return parseFundNotify(params, body, headers, CLEARING_CALLBACK_PATH);
    }

    public Map<String, Object> parseDivideNotify(Map<String, String> params, String body,
                                                 Map<String, String> headers) {
        return parseFundNotify(params, body, headers, DIVIDE_CALLBACK_PATH);
    }

    public Map<String, Object> parseDivideBackNotify(Map<String, String> params, String body,
                                                     Map<String, String> headers) {
        return parseFundNotify(params, body, headers, DIVIDE_BACK_CALLBACK_PATH);
    }

    /**
     * Parse, verify, decrypt and acknowledge one callback with a single official SDK operation.
     *
     * <p>{@link YopCallbackEngine#handle(YopCallbackRequest)} performs the protocol parse before
     * invoking the registered handler and creates the protocol-correct ACK afterwards. Calling
     * parse() first and handle() second would verify and decrypt the same envelope twice, and is
     * especially unsafe for SM2/SM4 callbacks. The handler below only captures the already parsed
     * callback; all business work remains in the application outbox.</p>
     */
    public VerifiedCallback handlePaymentNotify(Map<String, String> params, String body,
                                                Map<String, String> headers) {
        return handleVerifiedCallback(params, body, headers, PAYMENT_CALLBACK_PATH);
    }

    public VerifiedCallback handleClearingNotify(Map<String, String> params, String body,
                                                 Map<String, String> headers) {
        return handleVerifiedCallback(params, body, headers, CLEARING_CALLBACK_PATH);
    }

    public VerifiedCallback handleDivideNotify(Map<String, String> params, String body,
                                               Map<String, String> headers) {
        return handleVerifiedCallback(params, body, headers, DIVIDE_CALLBACK_PATH);
    }

    public VerifiedCallback handleRefundNotify(Map<String, String> params, String body,
                                               Map<String, String> headers) {
        return handleVerifiedCallback(params, body, headers, REFUND_CALLBACK_PATH);
    }

    public VerifiedCallback handleDivideBackNotify(Map<String, String> params, String body,
                                                    Map<String, String> headers) {
        return handleVerifiedCallback(params, body, headers, DIVIDE_BACK_CALLBACK_PATH);
    }

    public VerifiedCallback handleMerchantNotify(Map<String, String> params, String body,
                                                  Map<String, String> headers) {
        return handleVerifiedCallback(params, body, headers, MERCHANT_CALLBACK_PATH);
    }

    /** Verified business event plus the exact SDK-generated HTTP acknowledgement. */
    public record VerifiedCallback(Map<String, Object> event, YeepayCallbackAck ack) {
        public VerifiedCallback {
            event = event == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(event));
            Objects.requireNonNull(ack, "ack");
        }
    }

    /**
     * 使用官方 SDK 生成易宝回调成功应答。
     *
     * <p>{@link YopCallbackEngine#handle(YopCallbackRequest)} 同时负责协议解析、回调验签和
     * SM2 ACK 签名，但 SDK 默认 handler 会抛出 {@code no YopCallbackHandler found}。本地
     * 业务已经在控制器中异步落库，因此这里只注册一个按回调路径匹配的空 handler，让 SDK
     * 生成官方 RSA 文本 ACK 或 SM2 JSON + x-yop-sign ACK。</p>
     */
    public YeepayCallbackAck acknowledgePaymentNotify(Map<String, String> params, String body,
                                                       Map<String, String> headers) {
        return acknowledgeCallback(params, body, headers, PAYMENT_CALLBACK_PATH);
    }

    public YeepayCallbackAck acknowledgeClearingNotify(Map<String, String> params, String body,
                                                        Map<String, String> headers) {
        return acknowledgeCallback(params, body, headers, CLEARING_CALLBACK_PATH);
    }

    public YeepayCallbackAck acknowledgeDivideNotify(Map<String, String> params, String body,
                                                      Map<String, String> headers) {
        return acknowledgeCallback(params, body, headers, DIVIDE_CALLBACK_PATH);
    }

    public YeepayCallbackAck acknowledgeRefundNotify(Map<String, String> params, String body,
                                                      Map<String, String> headers) {
        return acknowledgeCallback(params, body, headers, REFUND_CALLBACK_PATH);
    }

    public YeepayCallbackAck acknowledgeDivideBackNotify(Map<String, String> params, String body,
                                                          Map<String, String> headers) {
        return acknowledgeCallback(params, body, headers, DIVIDE_BACK_CALLBACK_PATH);
    }

    private YeepayCallbackAck acknowledgeCallback(Map<String, String> params, String body,
                                                   Map<String, String> headers, String callbackPath) {
        // YOP stores handlers in a process-wide registry. Keep registration and handle() in the
        // same critical section as verified callbacks so concurrent channels cannot overwrite the
        // handler while the SDK is dispatching this acknowledgement.
        synchronized (CALLBACK_ENGINE_LOCK) {
            YopCallbackHandlerFactory.register(callbackPath, new YopCallbackHandler() {
                @Override
                public String getType() {
                    return callbackPath;
                }

                @Override
                public void handle(YopCallback callback) {
                    // Verified callback persistence and business processing run in the local outbox task.
                }
            });
            YopCallbackResponse response = YopCallbackEngine.handle(buildCallbackRequest(
                    params, body, headers, callbackPath));
            if (response.getStatus() != com.yeepay.yop.sdk.service.common.callback.enums.YopCallbackHandleStatus.SUCCESS) {
                throw new IllegalStateException("易宝官方 SDK 回调应答生成失败: " + response.getMessage());
            }
            return new YeepayCallbackAck(response.getBody(),
                    response.getContentType() == null ? null : response.getContentType().getValue(),
                    response.getHeaders());
        }
    }

    private VerifiedCallback handleVerifiedCallback(Map<String, String> params, String body,
                                                     Map<String, String> headers, String callbackPath) {
        AtomicReference<YopCallback> captured = new AtomicReference<>();
        synchronized (CALLBACK_ENGINE_LOCK) {
            YopCallbackHandlerFactory.register(callbackPath, new YopCallbackHandler() {
                @Override
                public String getType() {
                    return callbackPath;
                }

                @Override
                public void handle(YopCallback callback) {
                    captured.set(callback);
                }
            });
            YopCallbackResponse response = YopCallbackEngine.handle(buildCallbackRequest(
                    params, body, headers, callbackPath));
            if (response.getStatus() != com.yeepay.yop.sdk.service.common.callback.enums.YopCallbackHandleStatus.SUCCESS) {
                throw new IllegalArgumentException("易宝官方 SDK 回调处理失败: " + response.getMessage());
            }
            YopCallback callback = captured.get();
            Map<String, Object> event = verifiedPayload(callback);
            YeepayCallbackAck ack = new YeepayCallbackAck(response.getBody(),
                    response.getContentType() == null ? null : response.getContentType().getValue(),
                    response.getHeaders());
            return new VerifiedCallback(event, ack);
        }
    }

    /** SDK-generated HTTP response data for a verified callback. */
    public record YeepayCallbackAck(String body, String contentType, Map<String, String> headers) {
        public YeepayCallbackAck {
            headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
        }
    }

    public Map<String, Object> queryClearing(String orderId) {
        // 易宝订单查询同时返回 clearStatus；不存在独立的 clearing/query 读取契约。
        return queryClearing(orderId, null);
    }

    public Map<String, Object> queryClearing(String orderId, String uniqueOrderNo) {
        return queryClearing(orderId, uniqueOrderNo, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    public Map<String, Object> queryClearing(String orderId, String uniqueOrderNo,
                                               String parentMerchantNo, String collectionMerchantNo) {
        if (StrUtil.isBlank(orderId)) {
            throw new IllegalArgumentException("易宝清算查询订单号不能为空");
        }
        requireMerchantSnapshot(parentMerchantNo, collectionMerchantNo);
        Map<String, Object> params = new LinkedHashMap<>();
        // /trade/order/query 的官方契约要求传商户 orderId；uniqueOrderNo 不属于该查询接口参数。
        params.put("orderId", orderId);
        params.put("parentMerchantNo", parentMerchantNo);
        params.put("merchantNo", collectionMerchantNo);
        Map<String, Object> response = query(PAY_QUERY_PATH, params);
        validatePaymentQueryReference(response, "orderId", orderId, true);
        validatePaymentQueryReference(response, "parentMerchantNo", parentMerchantNo, true);
        validatePaymentQueryReference(response, "merchantNo", collectionMerchantNo, true);
        String channelNo = text(response, "uniqueOrderNo");
        if (!isSuccess(response) || StrUtil.isBlank(channelNo)
                || StrUtil.isNotBlank(uniqueOrderNo) && !uniqueOrderNo.equals(channelNo)) {
            throw new IllegalArgumentException("易宝清算查询业务结果或渠道流水号与原支付单不一致");
        }
        try {
            String amount = text(response, "orderAmount");
            if (amount == null || new BigDecimal(amount).movePointRight(2).intValueExact() <= 0) {
                throw new IllegalArgumentException("易宝清算查询订单金额不完整或不合法");
            }
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("易宝清算查询订单金额不是正整数分");
        }
        return response;
    }

    public Map<String, Object> applyDivide(String orderId, String divideRequestId,
                                           List<Map<String, Object>> details) {
        return applyDivide(orderId, null, divideRequestId, details);
    }

    /**
     * 提交分账。官方协议要求 orderId/uniqueOrderNo 二选一；有商户 orderId 时优先使用
     * 它，否则才使用易宝返回的 uniqueOrderNo。
     */
    public Map<String, Object> applyDivide(String orderId, String uniqueOrderNo,
                                           String divideRequestId, List<Map<String, Object>> details) {
        return applyDivide(orderId, uniqueOrderNo, divideRequestId, details, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    public Map<String, Object> applyDivide(String orderId, String uniqueOrderNo,
                                           String divideRequestId, List<Map<String, Object>> details,
                                               String parentMerchantNo, String collectionMerchantNo) {
        if (StrUtil.isBlank(orderId) && StrUtil.isBlank(uniqueOrderNo)
                || StrUtil.isBlank(divideRequestId)) {
            throw new IllegalArgumentException("易宝分账订单号和分账请求号不能为空");
        }
        if (details == null || details.isEmpty() || details.size() > config.getMaxDivideRecipients()) {
            throw new IllegalArgumentException("易宝分账接收方数量不合法");
        }
        List<Map<String, Object>> officialDetails = normalizeDivideDetails(details);
        Map<String, Object> params = new LinkedHashMap<>();
        putExclusiveOrderIdentifier(params, orderId, uniqueOrderNo);
        params.put("divideRequestId", divideRequestId);
        params.put("divideRule", "AMOUNT");
        params.put("divideDetail", officialDetails);
        params.put("parentMerchantNo", parentMerchantNo);
        params.put("merchantNo", collectionMerchantNo);
        return post(DIVIDE_PATH, params);
    }

    public Map<String, Object> queryDivide(String orderId, String divideRequestId) {
        return queryDivide(orderId, null, divideRequestId);
    }

    public Map<String, Object> queryDivide(String orderId, String uniqueOrderNo, String divideRequestId) {
        return queryDivide(orderId, uniqueOrderNo, divideRequestId, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    public Map<String, Object> queryDivide(String orderId, String uniqueOrderNo, String divideRequestId,
                                               String parentMerchantNo, String collectionMerchantNo) {
        if (StrUtil.isBlank(orderId) && StrUtil.isBlank(uniqueOrderNo)
                || StrUtil.isBlank(divideRequestId)) {
            throw new IllegalArgumentException("易宝分账查询订单号和请求号不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        putExclusiveOrderIdentifier(params, orderId, uniqueOrderNo);
        params.put("divideRequestId", divideRequestId);
        params.put("parentMerchantNo", parentMerchantNo);
        params.put("merchantNo", collectionMerchantNo);
        return query(DIVIDE_QUERY_PATH, params);
    }

    public Map<String, Object> applyDivideBack(String orderId, String divideRequestId,
                                               String divideBackRequestId,
                                               List<Map<String, Object>> details) {
        return applyDivideBack(orderId, null, divideRequestId, divideBackRequestId, details);
    }

    public Map<String, Object> applyDivideBack(String orderId, String uniqueOrderNo,
                                               String divideRequestId, String divideBackRequestId,
                                               List<Map<String, Object>> details) {
        return applyDivideBack(orderId, uniqueOrderNo, divideRequestId, divideBackRequestId, details, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    public Map<String, Object> applyDivideBack(String orderId, String uniqueOrderNo,
                                               String divideRequestId, String divideBackRequestId,
                                               List<Map<String, Object>> details,
                                               String parentMerchantNo, String collectionMerchantNo) {
        // /divide/back 的官方定义将 orderId 列为必填；uniqueOrderNo 只是可选辅助标识。
        if (StrUtil.isBlank(orderId) || StrUtil.isBlank(divideRequestId)
                || StrUtil.isBlank(divideBackRequestId)) {
            throw new IllegalArgumentException("易宝资金归还 orderId 和请求号不能为空");
        }
        if (details == null || details.isEmpty()) {
            throw new IllegalArgumentException("易宝资金归还明细不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderId", orderId);
        if (StrUtil.isNotBlank(uniqueOrderNo)) {
            params.put("uniqueOrderNo", uniqueOrderNo);
        }
        params.put("divideRequestId", divideRequestId);
        params.put("divideBackRequestId", divideBackRequestId);
        params.put("divideBackDetail", normalizeDivideBackDetails(details));
        params.put("parentMerchantNo", parentMerchantNo);
        params.put("merchantNo", collectionMerchantNo);
        return post(DIVIDE_BACK_PATH, params);
    }

    private List<Map<String, Object>> normalizeDivideDetails(List<Map<String, Object>> details) {
        List<Map<String, Object>> normalized = new ArrayList<>(details.size());
        for (Map<String, Object> detail : details) {
            if (detail == null || StrUtil.isBlank(text(detail, "ledgerNo"))) {
                throw new IllegalArgumentException("易宝分账明细必须包含 ledgerNo");
            }
            if (detail.containsKey("merchantNo") || detail.containsKey("divideDetailNo")) {
                throw new IllegalArgumentException("易宝分账请求不得传入 merchantNo 或 divideDetailNo");
            }
            String ledgerType = firstNonBlank(text(detail, "ledgerType"), "MERCHANT2MERCHANT");
            if (!"MERCHANT2MERCHANT".equals(ledgerType)) {
                throw new IllegalArgumentException("易宝分账明细 ledgerType 必须为 MERCHANT2MERCHANT");
            }
            String amountText = text(detail, "amount");
            if (StrUtil.isBlank(amountText)) {
                throw new IllegalArgumentException("易宝分账明细必须包含 amount");
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountText).setScale(2, RoundingMode.UNNECESSARY);
            } catch (Exception e) {
                throw new IllegalArgumentException("易宝分账金额必须是两位小数", e);
            }
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("易宝分账金额必须大于零");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ledgerNo", String.valueOf(detail.get("ledgerNo")));
            item.put("ledgerType", "MERCHANT2MERCHANT");
            item.put("amount", amount.toPlainString());
            String description = text(detail, "divideDetailDesc");
            if (StrUtil.isNotBlank(description)) {
                item.put("divideDetailDesc", description);
            }
            normalized.add(item);
        }
        return normalized;
    }

    /**
     * /divide/back has a deliberately smaller detail contract than /trade/refund's
     * divideBackDetail.  Keep the business-layer snapshot fields out of this request so
     * a future addition to PayRefundFundAllocationDTO cannot silently become an unsupported
     * YeePay parameter.
     */
    private List<Map<String, Object>> normalizeDivideBackDetails(List<Map<String, Object>> details) {
        List<Map<String, Object>> normalized = new ArrayList<>(details.size());
        for (Map<String, Object> detail : details) {
            if (detail == null || StrUtil.isBlank(text(detail, "divideDetailNo"))) {
                throw new IllegalArgumentException("易宝资金归还明细必须包含 divideDetailNo");
            }
            String amountText = text(detail, "amount");
            if (StrUtil.isBlank(amountText)) {
                throw new IllegalArgumentException("易宝资金归还明细必须包含 amount");
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountText).setScale(2, RoundingMode.UNNECESSARY);
            } catch (Exception e) {
                throw new IllegalArgumentException("易宝资金归还金额必须是两位小数", e);
            }
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("易宝资金归还金额必须大于零");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("amount", amount);
            item.put("divideDetailNo", text(detail, "divideDetailNo"));
            String reason = text(detail, "divideBackReason");
            if (StrUtil.isNotBlank(reason)) {
                item.put("divideBackReason", reason);
            }
            normalized.add(item);
        }
        return normalized;
    }

    public Map<String, Object> queryDivideBack(String orderId, String divideBackRequestId) {
        return queryDivideBack(orderId, null, divideBackRequestId);
    }

    public Map<String, Object> queryDivideBack(String orderId, String uniqueOrderNo,
                                               String divideBackRequestId) {
        return queryDivideBack(orderId, uniqueOrderNo, divideBackRequestId, config.getParentMerchantNo(), config.getCollectionMerchantNo());
    }

    public Map<String, Object> queryDivideBack(String orderId, String uniqueOrderNo,
                                               String divideBackRequestId,
                                               String parentMerchantNo, String collectionMerchantNo) {
        // /divide/back/query 的官方定义要求商户 orderId；uniqueOrderNo 仅作可选辅助标识。
        if (StrUtil.isBlank(orderId) || StrUtil.isBlank(divideBackRequestId)) {
            throw new IllegalArgumentException("易宝资金归还查询 orderId 和请求号不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderId", orderId);
        if (StrUtil.isNotBlank(uniqueOrderNo)) {
            params.put("uniqueOrderNo", uniqueOrderNo);
        }
        params.put("divideBackRequestId", divideBackRequestId);
        params.put("parentMerchantNo", parentMerchantNo);
        params.put("merchantNo", collectionMerchantNo);
        return query(DIVIDE_BACK_QUERY_PATH, params);
    }

    private static void putExclusiveOrderIdentifier(Map<String, Object> params, String orderId,
                                                    String uniqueOrderNo) {
        if (StrUtil.isNotBlank(orderId)) {
            params.put("orderId", orderId);
        } else if (StrUtil.isNotBlank(uniqueOrderNo)) {
            params.put("uniqueOrderNo", uniqueOrderNo);
        }
    }

    public static String money(Integer cents) {
        if (cents == null || cents < 0) {
            throw new IllegalArgumentException("金额必须为非负分");
        }
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private Map<String, Object> post(String path, Map<String, ?> params) {
        return request(path, params, false);
    }

    private Map<String, Object> query(String path, Map<String, ?> params) {
        return request(path, params, true);
    }

    private Map<String, Object> request(String path, Map<String, ?> params, boolean retryable) {
        Map<String, Object> request = new TreeMap<>();
        params.forEach((key, value) -> {
            if (value != null) {
                request.put(key, value instanceof List ? toJsonString(value) : value);
            }
        });
        // A timeout or malformed response cannot establish whether a state-changing request was
        // accepted. Only read-only queries may be retried automatically at this transport layer.
        int maxAttempts = retryable ? (config.getMaxRetries() == null ? 0 : config.getMaxRetries()) + 1 : 1;
        Exception lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                acquireRateLimit();
                YopRequestConfig requestConfig = buildRequestConfig();
                YopRequest yopRequest = new YopRequest(path, retryable ? "GET" : "POST", requestConfig);
                if (requestConfig.getReadTimeout() <= 0 || requestConfig.getConnectTimeout() <= 0) {
                    throw new IllegalStateException("易宝 SDK 超时配置必须大于 0");
                }
                request.forEach((key, value) -> yopRequest.addParameter(key, value));
                YopResponse response = getYopClient().request(yopRequest);
                return unwrapResult(response.getResult());
            } catch (PayClientException e) {
                lastException = e;
                if (attempt + 1 >= maxAttempts) {
                    throw safeRequestException(e);
                }
                sleepBeforeRetry(attempt);
            } catch (Exception e) {
                lastException = e;
                if (attempt + 1 >= maxAttempts) {
                    throw safeRequestException(e);
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw safeRequestException(lastException);
    }

    private static PayClientException safeRequestException(Throwable cause) {
        return new PayClientException(new IllegalStateException("易宝请求失败，异常类型："
                + (cause == null ? "Unknown" : cause.getClass().getSimpleName())));
    }

    private YopRequestConfig buildRequestConfig() {
        return new YopRequestConfig()
                .setAppKey(config.getAppKey())
                .setSecurityReq(config.getSecurityReq())
                .setReadTimeout(config.getTimeoutSeconds() * 1000)
                .setConnectTimeout(config.getConnectTimeoutSeconds() * 1000)
                .setCredentials(YeepaySdkClientFactory.credentials(config))
                .setEnableCircuitBreaker(false)
                .setSkipVerifySign(config.isSkipResponseVerifySign());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseUploadResult(YosUploadResponse response) {
        if (response == null) {
            throw new PayClientException(new IllegalStateException("易宝资质上传响应为空"));
        }
        Object raw = response.getResult();
        if (raw == null) {
            raw = response.getStringResult();
        }
        if (raw instanceof Map<?, ?> map) {
            return unwrap(new LinkedHashMap<>((Map<String, Object>) map));
        }
        if (raw instanceof String text && StrUtil.isNotBlank(text)) {
            Map<String, Object> parsed = JsonUtils.parseMap(text);
            if (parsed != null) {
                return unwrap(parsed);
            }
        }
        throw new PayClientException(new IllegalStateException("易宝资质上传响应无法解析"));
    }

    /** 下载官方平台账单；平台商商编的账单包含全部入驻商户，可发现本地遗漏的外部订单。 */
    public byte[] downloadDailyBill(LocalDate date, boolean divide) {
        return downloadDailyBill(date, divide, config.getParentMerchantNo());
    }

    public byte[] downloadDailyBill(LocalDate date, boolean divide, String parentMerchantNo) {
        if (StrUtil.isBlank(parentMerchantNo)) throw new IllegalArgumentException("原平台商编不能为空");
        if (date == null || !date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("易宝日账单日期必须早于今天");
        }
        try {
            acquireRateLimit();
            YopRequest request = new YopRequest(divide
                    ? "/yos/v1.0/std/bill/dividedaydownload"
                    : "/yos/v1.0/std/bill/tradedaydownload", "GET", buildRequestConfig());
            request.addParameter("merchantNo", parentMerchantNo);
            request.addParameter("dayString", date.toString());
            if (!divide) request.addParameter("dataType", "platform");
            com.yeepay.yop.sdk.model.yos.YosDownloadResponse response = getYopClient().download(request);
            if (response == null || response.getResult() == null) {
                throw new IllegalStateException("易宝日账单下载响应为空");
            }
            // Fail closed on an unexpectedly large response, close the SDK HTTP stream in all cases.
            try (InputStream stream = response.getResult()) {
                byte[] data = stream.readNBytes(64 * 1024 * 1024 + 1);
                if (data.length > 64 * 1024 * 1024 || data.length == 0) {
                    throw new IllegalStateException("易宝日账单为空或超过 64 MiB，需要人工处理");
                }
                return data;
            }
        } catch (Exception e) {
            throw safeRequestException(e);
        }
    }

    private static void requireMerchantSnapshot(String parent, String collection) {
        if (StrUtil.isBlank(parent) || StrUtil.isBlank(collection)) {
            throw new IllegalArgumentException("易宝原收款商编快照不能为空");
        }
    }

    private YopClient getYopClient() {
        YopClient result = yopClient;
        if (result == null) {
            synchronized (this) {
                result = yopClient;
                if (result == null) {
                    result = YeepaySdkClientFactory.getClient(config);
                    yopClient = result;
                }
            }
        }
        return result;
    }

    private void acquireRateLimit() {
        Integer rate = config.getRateLimitPerSecond();
        if (rate == null || rate <= 0) {
            return;
        }
        long intervalNanos = 1_000_000_000L / rate;
        synchronized (rateLimitMonitor) {
            long now = System.nanoTime();
            long waitNanos = nextRequestNanos - now;
            if (waitNanos > 0) {
                try {
                    long millis = waitNanos / 1_000_000L;
                    int nanos = (int) (waitNanos % 1_000_000L);
                    rateLimitMonitor.wait(millis, nanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PayClientException(e);
                }
                now = System.nanoTime();
            }
            nextRequestNanos = Math.max(now, nextRequestNanos) + intervalNanos;
        }
    }

    private void sleepBeforeRetry(int attempt) {
        long baseSeconds = Math.max(1, config.getRetryBackoffSeconds() == null
                ? 1 : config.getRetryBackoffSeconds());
        long multiplier = 1L << Math.min(attempt, 4);
        long millis = Math.min(30_000L, baseSeconds * multiplier * 1_000L);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PayClientException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            return unwrap(new LinkedHashMap<>((Map<String, Object>) map));
        }
        if (result instanceof String raw) {
            String json = raw.trim();
            if (json.startsWith("\\\"") && json.endsWith("\\\"")) {
                json = JsonUtils.parseObject(json, String.class);
            }
            if (!json.startsWith("{")) {
                throw new PayClientException(new IllegalStateException("易宝响应 result 不是 JSON 对象"));
            }
            Map<String, Object> parsed;
            try {
                parsed = JsonUtils.parseObject(json, Map.class);
            } catch (RuntimeException e) {
                throw new PayClientException(new IllegalStateException("易宝响应 result JSON 无法解析: " + json, e));
            }
            return unwrap(parsed);
        }
        throw new PayClientException(new IllegalStateException("易宝响应缺少 result 业务对象"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<String, Object> response) {
        Object nested = response.get("result");
        if (nested instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>(response);
            copy.putAll((Map<String, Object>) map);
            return copy;
        }
        return response;
    }

    /**
     * Parse callbacks through the official YOP callback engine. The engine owns protocol selection,
     * RSA digital-envelope verification/decryption, SM2 signature verification and SM4 decryption.
     */
    private Map<String, Object> parseResponse(Map<String, String> params, String body,
                                              Map<String, String> headers, String callbackPath) {
        YopCallback callback = YopCallbackEngine.parse(buildCallbackRequest(params, body, headers, callbackPath));
        return verifiedPayload(callback);
    }

    private Map<String, Object> verifiedPayload(YopCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("易宝官方 SDK 未返回回调对象");
        }
        if (!callback.isValidSign()) {
            throw new IllegalArgumentException("易宝官方 SDK 回调签名校验失败");
        }
        if (!Objects.equals(config.getAppKey(), callback.getAppKey())) {
            throw new IllegalArgumentException("易宝回调 appKey 与渠道配置不匹配");
        }
        Map<String, Object> payload = JsonUtils.parseMap(callback.getBizData());
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("易宝官方 SDK 回调业务数据不是 JSON 对象");
        }
        // callback.getId() is the SDK protocol callback identifier. It is not the business
        // event number and may be regenerated when YeePay retries a notification. Return the
        // complete verified/decrypted business payload here: the fund state machine needs all
        // fields (merchant numbers, event ids, and divide detail numbers) to validate and match
        // the immutable local snapshot. Callers persist only sanitizeEventPayload(payload) and
        // use sanitizePayload(response) for DTO/log data; credentials never leave the verified
        // in-memory path.
        return unwrap(payload);
    }

    private YopCallbackRequest buildCallbackRequest(Map<String, String> params, String body,
                                                     Map<String, String> headers, String callbackPath) {
        YeepaySdkClientFactory.registerCredentials(config);
        Map<String, String> callbackHeaders = headers == null
                ? Collections.emptyMap() : new LinkedHashMap<>(headers);
        String contentTypeHeader = header(callbackHeaders, "content-type");
        String authorization = header(callbackHeaders, "authorization");
        boolean json = (contentTypeHeader != null && contentTypeHeader.toLowerCase().contains("json"))
                || (authorization != null && authorization.startsWith("YOP-SM2"));
        YopCallbackRequest request = new YopCallbackRequest(callbackPath, "POST")
                .setProvider(config.getSdkProvider()).setEnv(config.getSdkEnv())
                .setContentType(json ? YopContentType.JSON : YopContentType.FORM_URL_ENCODE)
                .setHeaders(callbackHeaders).setPlatformServerRoot(config.getServerUrl());
        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((key, value) -> queryParams.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value));
        }
        if (json) {
            request.setParams(queryParams).setContent(body == null ? "" : body);
        } else {
            Map<String, List<String>> formParams = mergeCallbackFormParams(body, queryParams);
            request.setParams(formParams);
        }
        return request;
    }

    /**
     * Spring MVC exposes form fields through both the raw body and {@code @RequestParam}. The
     * body is authoritative for legacy YOP callbacks; query parameters are only a fallback for
     * keys which are absent from the body (for example the channel id added to the callback URL).
     */
    static Map<String, List<String>> mergeCallbackFormParams(String body, Map<String, List<String>> queryParams) {
        Map<String, List<String>> formParams = decodeForm(body);
        if (queryParams == null || queryParams.isEmpty()) {
            return formParams;
        }
        if (formParams.isEmpty()) {
            formParams.putAll(queryParams);
            return formParams;
        }
        queryParams.forEach((key, values) -> formParams.putIfAbsent(key, values));
        return formParams;
    }

    private static Map<String, List<String>> decodeForm(String body) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (StrUtil.isBlank(body)) {
            return result;
        }
        for (String pair : body.split("&")) {
            if (pair.isBlank()) continue;
            String[] parts = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 1 ? "" : java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static String header(Map<String, String> headers, String expectedName) {
        return headers.entrySet().stream().filter(entry -> entry.getKey() != null
                        && entry.getKey().equalsIgnoreCase(expectedName))
                .map(Map.Entry::getValue).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * 只允许脱敏后的渠道报文进入支付/退款记录和资金事件表。
     * 验签必须在调用方使用原始结构完成，脱敏不会影响业务状态字段。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) sanitizeValue(payload);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                if (SENSITIVE_PAYLOAD_KEYS.contains(key.toLowerCase())) {
                    continue;
                }
                sanitized.put(key, sanitizeValue(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>(list.size());
            for (Object item : list) {
                sanitized.add(sanitizeValue(item));
            }
            return sanitized;
        }
        return value;
    }

    /**
     * 固定回调地址可以被多个支付渠道复用。将渠道编号追加到本次请求的回调 URL，
     * 使回调入口不依赖易宝是否回传自定义 channelId 字段。
     */
    private String callbackUrl(String configuredUrl, String fallbackUrl) {
        String url = firstNonBlank(configuredUrl, fallbackUrl);
        if (StrUtil.isBlank(url) || getId() == null || url.contains("channelId=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "channelId=" + getId();
    }

    public String buildMerchantNotifyUrl() {
        return callbackUrl(config.getMerchantNotifyUrl(), null);
    }


    private static String extra(PayOrderUnifiedReqDTO reqDTO, String key) {
        return reqDTO.getChannelExtras() == null ? null : reqDTO.getChannelExtras().get(key);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String text(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static Integer parseOrderStatus(String status) {
        if (status == null) return null;
        return switch (status.toUpperCase()) {
            case "SUCCESS", "PAID", "PAY_SUCCESS" -> PayOrderStatusEnum.SUCCESS.getStatus();
            case "FAIL", "FAILED", "CLOSED", "TIME_OUT", "TIMEOUT" -> PayOrderStatusEnum.CLOSED.getStatus();
            case "PROCESSING", "WAITING", "NOTPAY", "USERPAYING" -> PayOrderStatusEnum.WAITING.getStatus();
            default -> null;
        };
    }

    private static Integer parseRefundStatus(String status) {
        if (status == null) return null;
        return switch (status.toUpperCase()) {
            case "SUCCESS", "REFUND_SUCCESS" -> PayRefundStatusEnum.SUCCESS.getStatus();
            case "FAIL", "FAILED", "REFUND_FAIL", "CANCEL" -> PayRefundStatusEnum.FAILURE.getStatus();
            case "PROCESSING", "WAITING", "SUSPEND" -> PayRefundStatusEnum.WAITING.getStatus();
            default -> null;
        };
    }

    private static LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value, YEEPAY_DATETIME_FORMATTER);
            } catch (DateTimeParseException ignoredDateTime) {
                // Continue with epoch-millisecond compatibility for older callbacks.
            }
            try {
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Long.parseLong(value)),
                        java.time.ZoneId.systemDefault());
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static boolean isSuccess(Map<String, Object> response) {
        String code = text(response, "code", "status");
        String subCode = text(response, "subCode", "sub_code");
        if ("40044".equals(code)) {
            return "00000".equals(subCode);
        }
        if (code == null) {
            Object success = response.get("success");
            return !(success instanceof Boolean) || (Boolean) success;
        }
        return "SUCCESS".equalsIgnoreCase(code) || "OPR00000".equals(code) || "00000".equals(code) || "0".equals(code)
                || "200".equals(code) || "true".equalsIgnoreCase(code);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePrePayTnPayload(String prePayTn) {
        String candidate = prePayTn.trim();
        if (candidate.startsWith("\\\"") && candidate.endsWith("\\\"")) {
            try {
                candidate = JsonUtils.parseObject(candidate, String.class);
            } catch (RuntimeException ignored) {
                throw new IllegalArgumentException("易宝 prePayTn 不是合法 JSON", ignored);
            }
        }
        if (!candidate.startsWith("{")) {
            throw new IllegalArgumentException("易宝 prePayTn 必须是完整 JSON，不能使用 raw token");
        }
        try {
            Map<String, Object> parsed = JsonUtils.parseObject(candidate, Map.class);
            if (parsed == null || parsed.isEmpty()) {
                throw new IllegalArgumentException("易宝 prePayTn JSON 不能为空");
            }
            return new LinkedHashMap<>(parsed);
        } catch (RuntimeException ignored) {
            if (ignored instanceof IllegalArgumentException) {
                throw ignored;
            }
            throw new IllegalArgumentException("易宝 prePayTn 不是合法 JSON", ignored);
        }
    }

    @Override
    protected PayTransferRespDTO doUnifiedTransfer(PayTransferUnifiedReqDTO reqDTO) {
        throw new UnsupportedOperationException("易宝渠道首期不支持转账");
    }

    @Override
    protected PayTransferRespDTO doGetTransfer(String outTradeNo) {
        throw new UnsupportedOperationException("易宝渠道首期不支持转账");
    }

    @Override
    protected PayTransferRespDTO doParseTransferNotify(Map<String, String> params, String body,
                                                        Map<String, String> headers) {
        throw new UnsupportedOperationException("易宝渠道首期不支持转账");
    }
}
