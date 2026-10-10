package cn.iocoder.yudao.module.pay.controller.admin.notify;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.controller.admin.notify.vo.PayNotifyTaskDetailRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.notify.vo.PayNotifyTaskPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.notify.vo.PayNotifyTaskRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.notify.PayNotifyLogDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.notify.PayNotifyTaskDO;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.notify.PayNotifyService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.refund.PayRefundService;
import cn.iocoder.yudao.module.pay.service.transfer.PayTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants.CHANNEL_NOT_FOUND;
import static cn.iocoder.yudao.module.pay.framework.job.config.PayJobConfiguration.YEEPAY_EVENT_THREAD_POOL_TASK_EXECUTOR;

@Tag(name = "管理后台 - 回调通知")
@RestController
@RequestMapping("/pay/notify")
@Validated
@Slf4j
public class PayNotifyController {

    @Resource
    private PayOrderService orderService;
    @Resource
    private PayRefundService refundService;
    @Resource
    private PayTransferService payTransferService;
    @Resource
    private PayNotifyService notifyService;
    @Resource
    private PayAppService appService;
    @Resource
    private PayChannelService channelService;
    @Resource
    private PayFundService payFundService;
    @Resource(name = YEEPAY_EVENT_THREAD_POOL_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor yeepayEventExecutor;

    @PostMapping(value = "/order/{channelId}")
    @Operation(summary = "支付渠道的统一【支付】回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyOrder(@PathVariable("channelId") Long channelId,
                                             @RequestParam(required = false) Map<String, String> params,
                                             @RequestBody(required = false) String body,
                                             @RequestHeader Map<String, String> headers) {
        log.info("[notifyOrder][channelId({}) 收到支付回调，参数数({})，报文长度({})]",
                channelId, params == null ? 0 : params.size(), body == null ? 0 : body.length());
        // 1. 校验支付渠道是否存在
        PayClient payClient = channelService.getPayClient(channelId);
        if (payClient == null) {
            log.error("[notifyOrder][渠道编号({}) 找不到对应的支付客户端]", channelId);
            throw exception(CHANNEL_NOT_FOUND);
        }
        if (payClient instanceof YeepayPayClient) {
            return notifyYeepayPayment(channelId, params, body, headers);
        }

        // 2. 解析通知数据
        PayOrderRespDTO notify = payClient.parseOrderNotify(params, body, headers);
        orderService.notifyOrder(channelId, notify);
        return ResponseEntity.ok("success");
    }

    @PostMapping(value = "/refund/{channelId}")
    @Operation(summary = "支付渠道的统一【退款】回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyRefund(@PathVariable("channelId") Long channelId,
                                              @RequestParam(required = false) Map<String, String> params,
                                              @RequestBody(required = false) String body,
                                              @RequestHeader Map<String, String> headers) {
        log.info("[notifyRefund][channelId({}) 收到退款回调，参数数({})，报文长度({})]",
                channelId, params == null ? 0 : params.size(), body == null ? 0 : body.length());
        // 1. 校验支付渠道是否存在
        PayClient payClient = channelService.getPayClient(channelId);
        if (payClient == null) {
            log.error("[notifyRefund][渠道编号({}) 找不到对应的支付客户端]", channelId);
            throw exception(CHANNEL_NOT_FOUND);
        }
        if (payClient instanceof YeepayPayClient) {
            return notifyYeepayRefund(channelId, params, body, headers);
        }

        // 2. 解析通知数据
        PayRefundRespDTO notify = payClient.parseRefundNotify(params, body, headers);
        refundService.notifyRefund(channelId, notify);
        return ResponseEntity.ok("success");
    }

    @PostMapping(value = "/transfer/{channelId}")
    @Operation(summary = "支付渠道的统一【转账】回调")
    @PermitAll
    @TenantIgnore
    public String notifyTransfer(@PathVariable("channelId") Long channelId,
                                 @RequestParam(required = false) Map<String, String> params,
                                 @RequestBody(required = false) String body,
                                 @RequestHeader Map<String, String> headers) {
        log.info("[notifyTransfer][channelId({}) 回调数据({}/{})]", channelId, params, body);
        // 1. 校验支付渠道是否存在
        PayClient payClient = channelService.getPayClient(channelId);
        if (payClient == null) {
            log.error("[notifyTransfer][渠道编号({}) 找不到对应的支付客户端]", channelId);
            throw exception(CHANNEL_NOT_FOUND);
        }

        // 2. 解析通知数据
        PayTransferRespDTO notify = payClient.parseTransferNotify(params, body, headers);
        payTransferService.notifyTransfer(channelId, notify);
        return "success";
    }

    /** 易宝支付回调专用入口，回调先验签解析，再复用现有支付状态机。 */
    @PostMapping(value = "/yeepay/payment")
    @Operation(summary = "易宝支付回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyYeepayPayment(@RequestParam("channelId") Long channelId,
                                                      @RequestParam(required = false) Map<String, String> params,
                                                      @RequestBody(required = false) String body,
                                                      @RequestHeader Map<String, String> headers) {
        YeepayPayClient client = getYeepayClient(channelId);
        YeepayPayClient.VerifiedCallback verified = client.handlePaymentNotify(params, body, headers);
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        Map<String, Object> event = trustedYeepayEvent(channel, verified.event());
        acceptAndSubmitYeepayEvent(channel, "payment", event, originalYeepayEnvelope(params, body, headers),
                () -> processPaymentEvent(channelId, client, event));
        return yeepayAck(verified.ack());
    }

    @PostMapping(value = "/yeepay/clearing")
    @Operation(summary = "易宝清算回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyYeepayClearing(@RequestParam("channelId") Long channelId,
                                                       @RequestParam(required = false) Map<String, String> params,
                                                       @RequestBody(required = false) String body,
                                                       @RequestHeader Map<String, String> headers) {
        YeepayPayClient client = getYeepayClient(channelId);
        YeepayPayClient.VerifiedCallback verified = client.handleClearingNotify(params, body, headers);
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        Map<String, Object> event = trustedYeepayEvent(channel, verified.event());
        acceptAndSubmitYeepayEvent(channel, "clearing", event, originalYeepayEnvelope(params, body, headers),
                () -> processFundEvent("clearing", event, () -> payFundService.handleClearing(event)));
        return yeepayAck(verified.ack());
    }

    @PostMapping(value = "/yeepay/divide")
    @Operation(summary = "易宝分账回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyYeepayDivide(@RequestParam("channelId") Long channelId,
                                                     @RequestParam(required = false) Map<String, String> params,
                                                     @RequestBody(required = false) String body,
                                                     @RequestHeader Map<String, String> headers) {
        YeepayPayClient client = getYeepayClient(channelId);
        YeepayPayClient.VerifiedCallback verified = client.handleDivideNotify(params, body, headers);
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        Map<String, Object> event = trustedYeepayEvent(channel, verified.event());
        acceptAndSubmitYeepayEvent(channel, "divide", event, originalYeepayEnvelope(params, body, headers),
                () -> processFundEvent("divide", event, () -> payFundService.handleDivide(event)));
        return yeepayAck(verified.ack());
    }

    @PostMapping(value = "/yeepay/refund")
    @Operation(summary = "易宝退款回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyYeepayRefund(@RequestParam("channelId") Long channelId,
                                                     @RequestParam(required = false) Map<String, String> params,
                                                     @RequestBody(required = false) String body,
                                                     @RequestHeader Map<String, String> headers) {
        YeepayPayClient client = getYeepayClient(channelId);
        YeepayPayClient.VerifiedCallback verified = client.handleRefundNotify(params, body, headers);
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        Map<String, Object> event = trustedYeepayEvent(channel, verified.event());
        acceptAndSubmitYeepayEvent(channel, "refund", event, originalYeepayEnvelope(params, body, headers),
                () -> processRefundEvent(channelId, client, event));
        return yeepayAck(verified.ack());
    }

    @PostMapping(value = "/yeepay/divide-back")
    @Operation(summary = "易宝资金归还回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyYeepayDivideBack(@RequestParam("channelId") Long channelId,
                                                         @RequestParam(required = false) Map<String, String> params,
                                                         @RequestBody(required = false) String body,
                                                         @RequestHeader Map<String, String> headers) {
        YeepayPayClient client = getYeepayClient(channelId);
        YeepayPayClient.VerifiedCallback verified = client.handleDivideBackNotify(params, body, headers);
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        Map<String, Object> event = trustedYeepayEvent(channel, verified.event());
        acceptAndSubmitYeepayEvent(channel, "divide-back", event, originalYeepayEnvelope(params, body, headers),
                () -> processFundEvent("divide-back", event, () -> payFundService.handleDivideBack(event)));
        return yeepayAck(verified.ack());
    }

    @PostMapping(value = "/yeepay/merchant")
    @Operation(summary = "易宝商户入网结果回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> notifyYeepayMerchant(@RequestParam("channelId") Long channelId,
                                                       @RequestParam(required = false) Map<String, String> params,
                                                       @RequestBody(required = false) String body,
                                                       @RequestHeader Map<String, String> headers) {
        YeepayPayClient client = getYeepayClient(channelId);
        YeepayPayClient.VerifiedCallback verified = client.handleMerchantNotify(params, body, headers);
        PayChannelDO channel = channelService.validExistingPayChannel(channelId);
        Map<String, Object> event = trustedYeepayEvent(channel, verified.event());
        acceptAndSubmitYeepayEvent(channel, "merchant", event, originalYeepayEnvelope(params, body, headers),
                () -> processFundEvent("merchant", event, () -> payFundService.handleMerchantOnboarding(event)));
        return yeepayAck(verified.ack());
    }

    private void processPaymentEvent(Long channelId, YeepayPayClient client, Map<String, Object> event) {
        try {
            if (!payFundService.validatePaymentNotify(event)) {
                // 已验签但找不到本地资金单：只保留事件审计，不能用外部通知创建或修改本地订单。
                payFundService.markYeepayEventProcessed("payment", event);
                return;
            }
            PayOrderRespDTO notify = client.parseYeepayOrderNotify(event);
            orderService.notifyOrder(channelId, notify);
            payFundService.markYeepayEventProcessed("payment", event);
        } catch (Exception e) {
            payFundService.markYeepayEventFailed("payment", event, e);
            log.error("[processPaymentEvent][易宝支付事件处理失败，异常类型({})]", e.getClass().getSimpleName());
        }
    }

    private void processRefundEvent(Long channelId, YeepayPayClient client, Map<String, Object> event) {
        try {
            if (!payFundService.validateRefundNotify(event)) {
                // 已验签但找不到本地退款资金明细：只保留事件审计，不更新退款单。
                payFundService.markYeepayEventProcessed("refund", event);
                return;
            }
            PayRefundRespDTO notify = client.parseYeepayRefundNotify(event);
            refundService.notifyRefund(channelId, notify);
            payFundService.markYeepayEventProcessed("refund", event);
        } catch (Exception e) {
            payFundService.markYeepayEventFailed("refund", event, e);
            log.error("[processRefundEvent][易宝退款事件处理失败，异常类型({})]", e.getClass().getSimpleName());
        }
    }

    private void processFundEvent(String type, Map<String, Object> event, Runnable processor) {
        try {
            processor.run();
            payFundService.markYeepayEventProcessed(type, event);
        } catch (Exception e) {
            payFundService.markYeepayEventFailed(type, event, e);
            log.error("[processFundEvent][易宝资金事件 type({}) 处理失败，异常类型({})]", type, e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> trustedYeepayEvent(PayChannelDO channel, Map<String, Object> payload) {
        if (channel.getTenantId() == null) {
            throw exception(CHANNEL_NOT_FOUND);
        }
        Map<String, Object> event = new LinkedHashMap<>(payload);
        // 这些值由服务器原渠道提供，不能信任签名报文中同名的字段。
        event.put("_trustedChannelId", channel.getId());
        event.put("_trustedAppId", channel.getAppId());
        return event;
    }

    private void acceptAndSubmitYeepayEvent(PayChannelDO channel, String type,
                                           Map<String, Object> event, String originalEnvelope, Runnable processor) {
        TenantUtils.execute(channel.getTenantId(), () -> {
            if (payFundService.acceptYeepayEvent(type, event, originalEnvelope)) {
                // 显式绑定 worker，确保即使入口和线程池继承 TenantIgnore 也不会跨租户查询。
                try {
                    yeepayEventExecutor.execute(() -> TenantUtils.execute(channel.getTenantId(), processor));
                } catch (java.util.concurrent.RejectedExecutionException rejected) {
                    // 事件已提交；保留 RECEIVED 状态由补偿任务重放，线程池压力不得影响 ACK。
                    log.warn("[acceptAndSubmitYeepayEvent][事件 type({}) 等待定时补偿]", type);
                }
            }
        });
    }

    private String originalYeepayEnvelope(Map<String, String> params, String body, Map<String, String> headers) {
        Map<String, String> securityHeaders = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name == null || value == null) {
                    return;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("x-yop-") || lower.startsWith("yop-")
                        || lower.startsWith("x-yeepay-") || HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                    securityHeaders.put(name, value);
                }
            });
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("params", params == null ? Map.of() : params);
        envelope.put("body", body);
        envelope.put("securityHeaders", securityHeaders);
        return toJsonString(envelope);
    }

    private ResponseEntity<String> yeepayAck(YeepayPayClient.YeepayCallbackAck ack) {
        HttpHeaders responseHeaders = new HttpHeaders();
        if (ack.contentType() != null && !ack.contentType().isBlank()) {
            responseHeaders.set(HttpHeaders.CONTENT_TYPE, ack.contentType());
        }
        ack.headers().forEach((name, value) -> {
            if (name != null && value != null && !HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                responseHeaders.set(name, value);
            }
        });
        return ResponseEntity.ok().headers(responseHeaders).body(ack.body());
    }

    private YeepayPayClient getYeepayClient(Long channelId) {
        if (channelId == null) {
            throw exception(CHANNEL_NOT_FOUND);
        }
        PayClient<?> client = channelService.getPayClient(channelId);
        if (!(client instanceof YeepayPayClient yeepayPayClient)) {
            throw exception(CHANNEL_NOT_FOUND);
        }
        return yeepayPayClient;
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得回调通知的明细")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pay:notify:query')")
    public CommonResult<PayNotifyTaskDetailRespVO> getNotifyTaskDetail(@RequestParam("id") Long id) {
        PayNotifyTaskDO task = notifyService.getNotifyTask(id);
        if (task == null) {
            return success(null);
        }
        // 拼接返回
        PayAppDO app = appService.getApp(task.getAppId());
        List<PayNotifyLogDO> logs = notifyService.getNotifyLogList(id);
        return success(BeanUtils.toBean(task, PayNotifyTaskDetailRespVO.class, respVO -> {
            if (app != null) {
                respVO.setAppName(app.getName());
            }
            respVO.setLogs(BeanUtils.toBean(logs, PayNotifyTaskDetailRespVO.Log.class));
        }));
    }

    @GetMapping("/page")
    @Operation(summary = "获得回调通知分页")
    @PreAuthorize("@ss.hasPermission('pay:notify:query')")
    public CommonResult<PageResult<PayNotifyTaskRespVO>> getNotifyTaskPage(@Valid PayNotifyTaskPageReqVO pageVO) {
        PageResult<PayNotifyTaskDO> pageResult = notifyService.getNotifyTaskPage(pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }
        // 拼接返回
        Map<Long, PayAppDO> apps = appService.getAppMap(convertList(pageResult.getList(), PayNotifyTaskDO::getAppId));

        // 转换对象
        return success(BeanUtils.toBean(pageResult, PayNotifyTaskRespVO.class, order -> {
            PayAppDO app = apps.get(order.getAppId());
            if (app != null) {
                order.setAppName(app.getName());
            }
        }));
    }

}
