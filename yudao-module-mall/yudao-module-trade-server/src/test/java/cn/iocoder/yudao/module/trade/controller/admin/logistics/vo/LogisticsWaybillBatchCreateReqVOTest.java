package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsWaybillBatchCreateReqVOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void orderIdsRejectsEmptyList() {
        var violations = validator.validate(new LogisticsWaybillBatchCreateReqVO().setOrderIds(List.of()));

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("orderIds"));
    }

    @Test
    void orderIdsRejectsMoreThanOneHundredOrders() {
        List<Long> orderIds = LongStream.rangeClosed(1, 101).boxed().toList();

        var violations = validator.validate(new LogisticsWaybillBatchCreateReqVO().setOrderIds(orderIds));

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("orderIds")
                && violation.getMessage().contains("100"));
    }

    @Test
    void orderIdsRejectsNullElement() {
        var violations = validator.validate(new LogisticsWaybillBatchCreateReqVO().setOrderIds(Arrays.asList(1L, null)));

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().startsWith("orderIds[1]"));
    }

}
