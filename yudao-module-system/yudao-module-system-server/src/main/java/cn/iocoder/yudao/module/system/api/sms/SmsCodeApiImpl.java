package cn.iocoder.yudao.module.system.api.sms;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeValidateReqDTO;
import cn.iocoder.yudao.module.system.service.sms.SmsCodeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class SmsCodeApiImpl implements SmsCodeApi {

    @Resource
    private cn.iocoder.yudao.module.system.framework.sms.config.SmsCodeProperties smsCodeProperties;

    @Override
    public CommonResult<Boolean> isIdentityVerificationSafe() {
        Integer begin = smsCodeProperties.getBeginCode();
        Integer end = smsCodeProperties.getEndCode();
        return success(begin != null && end != null && begin >= 100000 && end <= 999999
                && end - begin >= 899999 && smsCodeProperties.getExpireTimes() != null
                && !smsCodeProperties.getExpireTimes().isNegative() && !smsCodeProperties.getExpireTimes().isZero()
                && smsCodeProperties.getExpireTimes().compareTo(java.time.Duration.ofMinutes(10)) <= 0);
    }

    @Resource
    private SmsCodeService smsCodeService;

    @Override
    public CommonResult<Boolean> sendSmsCode(SmsCodeSendReqDTO reqDTO) {
        smsCodeService.sendSmsCode(reqDTO);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> useSmsCode(SmsCodeUseReqDTO reqDTO) {
        smsCodeService.useSmsCode(reqDTO);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> validateSmsCode(SmsCodeValidateReqDTO reqDTO) {
        smsCodeService.validateSmsCode(reqDTO);
        return success(true);
    }

}
