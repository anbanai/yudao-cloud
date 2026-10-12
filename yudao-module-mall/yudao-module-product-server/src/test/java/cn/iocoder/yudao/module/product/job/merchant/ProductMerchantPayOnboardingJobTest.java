package cn.iocoder.yudao.module.product.job.merchant;

import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantPayOnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductMerchantPayOnboardingJobTest {

    @Test
    void missingOnboardingTableSkipsCurrentRunWithoutPropagatingException() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProductMerchantPayOnboardingService onboardingService = mock(ProductMerchantPayOnboardingService.class);
        ProductMerchantPayOnboardingJob job = new ProductMerchantPayOnboardingJob();
        ReflectionTestUtils.setField(job, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(job, "onboardingService", onboardingService);

        when(jdbcTemplate.queryForList(anyString(), eq(Long.class)))
                .thenThrow(new BadSqlGrammarException("query tenants", "SELECT ...",
                        new SQLException("Table 'brdx.product_merchant_pay_onboarding' doesn't exist", "42S02", 1146)));

        assertThatCode(job::execute).doesNotThrowAnyException();
        verifyNoInteractions(onboardingService);
    }

    @Test
    void otherSqlGrammarErrorsAreStillPropagated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProductMerchantPayOnboardingJob job = new ProductMerchantPayOnboardingJob();
        ReflectionTestUtils.setField(job, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(job, "onboardingService", mock(ProductMerchantPayOnboardingService.class));
        BadSqlGrammarException exception = new BadSqlGrammarException("query tenants", "SELECT ...",
                new SQLException("Unknown column 'state'", "42S22", 1054));
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class))).thenThrow(exception);

        assertThatThrownBy(job::execute).isSameAs(exception);
    }
}
