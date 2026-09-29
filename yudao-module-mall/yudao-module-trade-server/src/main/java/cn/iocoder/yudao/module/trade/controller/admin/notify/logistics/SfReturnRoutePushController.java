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
import cn.hutool.core.util.StrUtil;

@RestController
@RequestMapping("/trade/logistics/sf/return-callback")
public class SfReturnRoutePushController {

    @Resource private AfterSaleReturnShipmentService service;
    @Value("${yudao.trade.logistics.sf.callback-token:}")
    private String callbackToken;

    @PostMapping("/route")
    @PermitAll
    @TenantIgnore
    public SfRoutePushRespVO routePush(@RequestParam(value = "token", required = false) String token,
                                       @Valid @RequestBody SfReturnRoutePushReqVO request) {
        if (callbackToken == null || callbackToken.length() < 16 || token == null || request == null
                || StrUtil.isBlank(request.getEventId())
                || (StrUtil.isBlank(request.getProviderOrderNo()) && StrUtil.isBlank(request.getWaybillNo()))
                || StrUtil.isBlank(request.getStatus())
                || !MessageDigest.isEqual(callbackToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            return SfRoutePushRespVO.failure();
        }
        try {
            service.syncStatusByProviderRefs(request.getProviderOrderNo(), request.getWaybillNo(), request.getStatus(),
                    request.getEventId(), request.getDescription(), request.getLocation(), request.getTenantId());
            return SfRoutePushRespVO.success();
        } catch (RuntimeException exception) {
            return SfRoutePushRespVO.failure();
        }
    }
}
