package cn.iocoder.yudao.module.trade.controller.merchant.logistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsDiagnosticPayloadReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintDeviceRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintDeviceSaveReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintTaskRespVO;
import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsManagementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/trade/merchant/logistics/sf")
@PreAuthorize("isAuthenticated()")
public class MerchantSfPrintDeviceController {

    private final MerchantSecurityService merchantSecurityService;
    private final LogisticsManagementService managementService;

    @GetMapping("/devices")
    public CommonResult<List<LogisticsPrintDeviceRespVO>> getDevices() {
        return success(managementService.getMerchantDevices(merchantSecurityService.requireOperatingMerchantId()));
    }

    @PostMapping("/devices")
    public CommonResult<LogisticsPrintDeviceRespVO> saveDevice(@Valid @RequestBody LogisticsPrintDeviceSaveReqVO request) {
        return success(managementService.saveDevice(merchantSecurityService.requireOperatingMerchantId(), request));
    }

    @PostMapping("/devices/enroll")
    public CommonResult<LogisticsPrintDeviceRespVO> enrollDevice(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return success(managementService.enrollDevice(merchantSecurityService.requireOperatingMerchantId()));
    }

    @PostMapping("/devices/{id}/test-print")
    public CommonResult<LogisticsPrintTaskRespVO> testPrint(@PathVariable("id") Long id,
                                                            @Valid @RequestBody LogisticsDiagnosticPayloadReqVO request) {
        return success(managementService.createTestPrint(merchantSecurityService.requireOperatingMerchantId(), id,
                request.getPaperWidthMm(), request.getPaperHeightMm()));
    }
}
