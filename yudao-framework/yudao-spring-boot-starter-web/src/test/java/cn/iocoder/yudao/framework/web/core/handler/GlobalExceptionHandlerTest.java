package cn.iocoder.yudao.framework.web.core.handler;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GlobalExceptionHandlerTest {

    @ParameterizedTest
    @CsvSource({",Unknown Source", "Product.java,Product.java"})
    void testDefaultExceptionHandler_recordsStackFrame(String sourceFile, String expectedFile) {
        ApiErrorLogCommonApi logApi = mock(ApiErrorLogCommonApi.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler("mall", logApi);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app-api/product/spu/list-by-ids");
        WebFrameworkUtils.setLoginUserType(request, UserTypeEnum.MEMBER.getValue());
        RuntimeException error = new UnsupportedOperationException();
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("Product", "list", sourceFile, -1)});

        handler.defaultExceptionHandler(request, error);

        ArgumentCaptor<ApiErrorLogCreateReqDTO> captor = ArgumentCaptor.forClass(ApiErrorLogCreateReqDTO.class);
        verify(logApi).createApiErrorLogAsync(captor.capture());
        assertThat(captor.getValue().getExceptionFileName()).isEqualTo(expectedFile);
        assertThat(captor.getValue().getExceptionClassName()).isEqualTo("Product");
        assertThat(captor.getValue().getExceptionMethodName()).isEqualTo("list");
    }
}
