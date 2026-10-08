package cn.iocoder.yudao.module.trade.controller.admin.notify.logistics;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfReturnRoutePushReqVO;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushRespVO;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleReturnShipmentService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.HexFormat;
import cn.hutool.core.util.StrUtil;

@RestController
@RequestMapping("/trade/logistics/sf/return-callback")
public class SfReturnRoutePushController {

    @Resource private AfterSaleReturnShipmentService service;
    @Value("${yudao.trade.logistics.sf.callback-signing-secret:}")
    private String callbackSigningSecret;

    @PostMapping("/route")
    @PermitAll
    @TenantIgnore
    public SfRoutePushRespVO routePush(@RequestParam(value = "signature", required = false) String signature,
                                       @Valid @RequestBody SfReturnRoutePushReqVO request) {
        if (callbackSigningSecret == null || callbackSigningSecret.length() < 32 || request == null
                || StrUtil.isBlank(request.getEventId())
                || (StrUtil.isBlank(request.getProviderOrderNo()) && StrUtil.isBlank(request.getWaybillNo()))
                || StrUtil.isBlank(request.getStatus())
                || StrUtil.isBlank(request.getTimestamp()) || StrUtil.isBlank(request.getNonce())
                || StrUtil.isBlank(signature) || !isFresh(request.getTimestamp())
                || !MessageDigest.isEqual(sign(request).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return SfRoutePushRespVO.failure();
        }
        try {
            service.syncStatusByProviderRefs(request.getProviderOrderNo(), request.getWaybillNo(), request.getStatus(),
                    request.getEventId(), request.getDescription(), request.getLocation(), null);
            return SfRoutePushRespVO.success();
        } catch (RuntimeException exception) {
            return SfRoutePushRespVO.failure();
        }
    }

    private boolean isFresh(String timestamp) {
        try {
            long seconds = Long.parseLong(timestamp);
            return Math.abs(Instant.now().getEpochSecond() - seconds) <= 300;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String sign(SfReturnRoutePushReqVO request) {
        String payload = String.join("|", value(request.getProviderOrderNo()), value(request.getWaybillNo()),
                value(request.getStatus()), value(request.getEventId()), value(request.getDescription()),
                value(request.getLocation()), value(request.getTimestamp()), value(request.getNonce()));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSigningSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return "";
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
