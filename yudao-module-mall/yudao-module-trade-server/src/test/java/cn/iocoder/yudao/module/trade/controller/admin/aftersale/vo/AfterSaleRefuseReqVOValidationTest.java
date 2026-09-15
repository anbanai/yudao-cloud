package cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AfterSaleRefuseReqVOValidationTest {

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
    void refuseMemo_requiresAtLeastTwoCharacters() {
        assertThat(validator.validate(new AfterSaleRefuseReqVO().setId(1L).setRefuseMemo(null)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("refuseMemo"));
        assertThat(validator.validate(new AfterSaleRefuseReqVO().setId(1L).setRefuseMemo("")))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("refuseMemo"));
        assertThat(validator.validate(new AfterSaleRefuseReqVO().setId(1L).setRefuseMemo("x")))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("refuseMemo"));
        assertThat(validator.validate(new AfterSaleRefuseReqVO().setId(1L).setRefuseMemo("ok")))
                .noneMatch(violation -> violation.getPropertyPath().toString().equals("refuseMemo"));
    }
}
