package cn.iocoder.yudao.module.trade.controller.admin.aftersale;

import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleDisagreeReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSaleRefuseReqVO;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AfterSaleControllerValidationTest {

    @Test
    void mutationRequests_areValidatedBeforeServiceInvocation() throws NoSuchMethodException {
        Method disagree = AfterSaleController.class.getMethod("disagreeAfterSale", AfterSaleDisagreeReqVO.class);
        Method refuse = AfterSaleController.class.getMethod("refuseAfterSale", AfterSaleRefuseReqVO.class);

        assertThat(Arrays.stream(disagree.getParameterAnnotations()[0]).anyMatch(annotation -> annotation.annotationType() == Valid.class)).isTrue();
        assertThat(Arrays.stream(refuse.getParameterAnnotations()[0]).anyMatch(annotation -> annotation.annotationType() == Valid.class)).isTrue();
    }
}
