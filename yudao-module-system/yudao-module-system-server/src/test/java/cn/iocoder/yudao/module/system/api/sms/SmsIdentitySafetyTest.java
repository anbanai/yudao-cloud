package cn.iocoder.yudao.module.system.api.sms;

import cn.iocoder.yudao.module.system.framework.sms.config.SmsCodeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class SmsIdentitySafetyTest {
    @Test void fixedExamplesCannotAuthenticateSharedIdentityPhone() {
        SmsCodeProperties p = new SmsCodeProperties(); p.setBeginCode(9999); p.setEndCode(9999); p.setExpireTimes(Duration.ofMinutes(5));
        SmsCodeApiImpl api = new SmsCodeApiImpl(); ReflectionTestUtils.setField(api, "smsCodeProperties", p);
        assertFalse(api.isIdentityVerificationSafe().getCheckedData());
        p.setBeginCode(100000); p.setEndCode(999999);
        assertTrue(api.isIdentityVerificationSafe().getCheckedData());
        p.setExpireTimes(Duration.ofHours(1)); assertFalse(api.isIdentityVerificationSafe().getCheckedData());
    }
}
