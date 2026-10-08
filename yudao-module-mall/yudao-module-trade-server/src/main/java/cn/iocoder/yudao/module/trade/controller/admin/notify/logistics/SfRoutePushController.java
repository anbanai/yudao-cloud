package cn.iocoder.yudao.module.trade.controller.admin.notify.logistics;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushReqVO;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushRespVO;
import cn.iocoder.yudao.module.trade.service.logistics.SfRoutePushService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@RequestMapping("/trade/logistics/sf/callback")
public class SfRoutePushController {

    @Resource private SfRoutePushService service;
    @Value("${yudao.trade.logistics.sf.callback-token:}")
    private String callbackToken;

    @PostMapping(value = "/route", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    @TenantIgnore
    public SfRoutePushRespVO routePush(@RequestParam(value = "token", required = false) String token,
                                       @RequestBody SfRoutePushReqVO request) {
        if (!validToken(token)) {
            log.warn("[routePush][拒绝未通过鉴权的顺丰路由推送]");
            return SfRoutePushRespVO.failure();
        }
        try {
            service.process(request);
            return SfRoutePushRespVO.success();
        } catch (RuntimeException exception) {
            log.warn("[routePush][顺丰路由推送处理失败: {}]", exception.getMessage());
            return SfRoutePushRespVO.failure();
        }
    }

    private boolean validToken(String token) {
        if (callbackToken == null || callbackToken.length() < 16) {
            log.warn("[routePush][拒绝未通过鉴权的顺丰路由推送：未配置回调 token，请设置 SF_LOGISTICS_CALLBACK_TOKEN 环境变量（至少 16 位）]");
            return false;
        }
        if (token == null || !MessageDigest.isEqual(callbackToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[routePush][拒绝未通过鉴权的顺丰路由推送：请求 token 缺失或不匹配，请确认顺丰推送地址已追加 ?token=]");
            return false;
        }
        return true;
    }
}
