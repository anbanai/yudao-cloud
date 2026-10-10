package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TradeDiscountActivityPriceCalculatorTest {

    @Test
    void getMemberLevel_whenUserIdIsNull_returnsNullWithoutCallingMemberApi() {
        MemberUserApi memberUserApi = mock(MemberUserApi.class);
        TradeDiscountActivityPriceCalculator calculator = new TradeDiscountActivityPriceCalculator();
        ReflectionTestUtils.setField(calculator, "memberUserApi", memberUserApi);

        assertThat(calculator.getMemberLevel(null)).isNull();
        verifyNoInteractions(memberUserApi);
    }

}
