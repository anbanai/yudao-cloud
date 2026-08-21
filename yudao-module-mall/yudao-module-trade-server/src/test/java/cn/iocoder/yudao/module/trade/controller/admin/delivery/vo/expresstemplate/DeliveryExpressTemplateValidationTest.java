package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.expresstemplate;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryExpressTemplateValidationTest {

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
    void testCharge_acceptsZeroPrices() {
        DeliveryExpressTemplateChargeBaseVO charge = validCharge()
                .setStartPrice(0).setExtraPrice(0);

        assertThat(validator.validate(charge)).isEmpty();
    }

    @Test
    void testFree_acceptsZeroThresholds() {
        DeliveryExpressTemplateFreeBaseVO free = validFree()
                .setFreePrice(0).setFreeCount(0);

        assertThat(validator.validate(free)).isEmpty();
    }

    @Test
    void testCharge_rejectsNegativePricesAndNonPositiveCounts() {
        assertHasViolation(validCharge().setStartPrice(-1), "startPrice");
        assertHasViolation(validCharge().setExtraPrice(-1), "extraPrice");
        assertHasViolation(validCharge().setStartCount(0D), "startCount");
        assertHasViolation(validCharge().setExtraCount(0D), "extraCount");
        assertHasViolation(validCharge().setExtraCount(-1D), "extraCount");
    }

    @Test
    void testFree_rejectsNegativeThresholds() {
        assertHasViolation(validFree().setFreePrice(-1), "freePrice");
        assertHasViolation(validFree().setFreeCount(-1), "freeCount");
    }

    private static DeliveryExpressTemplateChargeBaseVO validCharge() {
        return new DeliveryExpressTemplateChargeBaseVO().setAreaIds(java.util.List.of(1))
                .setStartCount(1D).setStartPrice(100).setExtraCount(1D).setExtraPrice(100);
    }

    private static DeliveryExpressTemplateFreeBaseVO validFree() {
        return new DeliveryExpressTemplateFreeBaseVO().setAreaIds(java.util.List.of(1))
                .setFreePrice(100).setFreeCount(1);
    }

    private static void assertHasViolation(Object target, String field) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(target);
        assertThat(violations).anyMatch(violation ->
                violation.getPropertyPath().toString().equals(field));
    }

}
