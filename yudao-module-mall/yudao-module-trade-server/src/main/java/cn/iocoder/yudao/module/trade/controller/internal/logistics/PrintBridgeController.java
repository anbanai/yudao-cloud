package cn.iocoder.yudao.module.trade.controller.internal.logistics;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeStatusReqVO;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeTaskRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsPrintBridgeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.LOGISTICS_DEVICE_AUTH_FAILED;

@RestController
@RequestMapping("/internal-api/trade/logistics/printbridge/tasks")
@TenantIgnore
public class PrintBridgeController {

    @Resource
    private LogisticsPrintBridgeService service;

    @GetMapping
    public ResponseEntity<PrintBridgeTaskRespVO> pull(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader("X-PrintBridge-Device-Id") String deviceCode,
            @RequestHeader(value = "X-PrintBridge-Device-Name", required = false) String deviceName,
            @RequestHeader(value = "X-PrintBridge-Test", required = false) Boolean test) {
        TradeLogisticsPrintDeviceDO device = service.authenticate(bearerToken(authorization), deviceCode, deviceName);
        if (Boolean.TRUE.equals(test)) {
            return ResponseEntity.noContent().build();
        }
        PrintBridgeTaskRespVO task = service.pull(device, deviceName);
        return task == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(task);
    }

    @PostMapping
    public ResponseEntity<Void> report(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader("X-PrintBridge-Device-Id") String deviceCode,
            @RequestHeader(value = "X-PrintBridge-Device-Name", required = false) String deviceName,
            @RequestHeader(value = "X-PrintBridge-Test", required = false) Boolean test,
            @Valid @RequestBody PrintBridgeStatusReqVO request) {
        TradeLogisticsPrintDeviceDO device = service.authenticate(bearerToken(authorization), deviceCode, deviceName);
        if (Boolean.TRUE.equals(test)) {
            return ResponseEntity.noContent().build();
        }
        service.report(device, request);
        return ResponseEntity.noContent().build();
    }

    private static String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Map<String, Object>> handleServiceException(ServiceException exception) {
        HttpStatus status = exception.getCode().equals(LOGISTICS_DEVICE_AUTH_FAILED.getCode())
                ? HttpStatus.UNAUTHORIZED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("code", exception.getCode(),
                "message", exception.getMessage()));
    }
}
