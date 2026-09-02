package cn.iocoder.yudao.module.trade.controller.admin.notify.logistics;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushReqVO;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushRespVO;
import cn.iocoder.yudao.module.trade.service.logistics.SfRoutePushService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/trade/logistics/sf/callback")
public class SfRoutePushController {

    @Resource private SfRoutePushService service;

    @PostMapping(value = "/route", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PermitAll
    @TenantIgnore
    public SfRoutePushRespVO routePush(@RequestBody SfRoutePushReqVO request) {
        try {
            service.process(request);
            return SfRoutePushRespVO.success();
        } catch (RuntimeException exception) {
            log.warn("[routePush][顺丰路由推送处理失败: {}]", exception.getMessage());
            return SfRoutePushRespVO.failure();
        }
    }
}
