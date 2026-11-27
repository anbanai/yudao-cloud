package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SfLogisticsAccountSaveReqVOTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validateRejectsNonNumericServiceCode() {
        assertHasViolation(validRequest().setServiceCode("SF_STANDARD"), "serviceCode");
    }

    @Test
    void validateRejectsNonPositiveDefaultWeight() {
        assertHasViolation(validRequest().setDefaultWeightKg(BigDecimal.ZERO), "defaultWeightKg");
    }

    @Test
    void platformAccountCanBeCreatedWithCredentialsOnly() {
        assertThat(validator.validate(new SfLogisticsAccountSaveReqVO()
                .setPartnerId("sf-partner").setCheckWord("secret"))).isEmpty();
    }

    private static MerchantSfLogisticsConfigSaveReqVO validRequest() {
        return new MerchantSfLogisticsConfigSaveReqVO().setServiceCode("1").setDefaultWeightKg(BigDecimal.ONE)
                .setSenderName("仓库").setSenderPhone("13800138000")
                .setSenderProvince("四川省").setSenderCity("成都市").setSenderAddress("高新区 1 号");
    }
    private static void assertHasViolation(Object target, String field) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(target);
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals(field));
    }
}
