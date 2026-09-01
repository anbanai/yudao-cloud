package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

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
    void validateRejectsUnsupportedPaperOrDpi() {
        assertHasViolation(validRequest().setPaperWidthMm(80), "paperWidthMm");
        assertHasViolation(validRequest().setPaperHeightMm(100), "paperHeightMm");
        assertHasViolation(validRequest().setDpi(300), "dpi");
    }

    @Test
    void validateAcceptsV1PrintSpecification() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    private static SfLogisticsAccountSaveReqVO validRequest() {
        return new SfLogisticsAccountSaveReqVO().setName("顺丰月结账号").setLogisticsId(1L)
                .setEndpoint("https://sfapi.sf-express.com/std/service").setServiceCode("1")
                .setTemplateCode("fm_100150").setSenderName("仓库").setSenderPhone("13800138000")
                .setSenderProvince("四川省").setSenderCity("成都市").setSenderAddress("高新区 1 号")
                .setDefaultWeightKg(BigDecimal.ONE).setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203);
    }

    private static void assertHasViolation(Object target, String field) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(target);
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals(field));
    }
}
