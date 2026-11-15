package cn.iocoder.yudao.module.promotion.controller.admin.coupon;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.CouponSendBatchReqVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponSendBatchDO;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponBatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponControllerBatchTest {

    @InjectMocks
    private CouponController controller;
    @Mock
    private CouponBatchService batchService;

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void previewAndSubmitUseAuthenticatedOperator() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(17L);
        SecurityFrameworkUtils.setLoginUser(loginUser, org.mockito.Mockito.mock(HttpServletRequest.class));
        CouponSendBatchReqVO request = new CouponSendBatchReqVO().setTemplateId(9L).setIdempotencyKey("preview-1");
        CouponSendBatchDO batch = new CouponSendBatchDO().setId(23L).setOperatorId(17L);
        when(batchService.preview(request, 17L)).thenReturn(batch);
        when(batchService.submit(23L, 17L)).thenReturn(batch);

        assertEquals(batch, controller.preview(request).getCheckedData());
        assertEquals(batch, controller.submit(23L).getCheckedData());
        verify(batchService).preview(request, 17L);
        verify(batchService).submit(23L, 17L);
    }

    @Test
    void batchReadRequiresQueryPermission() throws Exception {
        when(batchService.get(23L)).thenReturn(new CouponSendBatchDO().setId(23L));

        assertEquals(23L, controller.batch(23L).getCheckedData().getId());
        assertEquals("@ss.hasPermission('promotion:coupon:query')", CouponController.class.getMethod("batch", Long.class)
                .getAnnotation(PreAuthorize.class).value());
        assertEquals("/send-preview", CouponController.class.getMethod("preview", CouponSendBatchReqVO.class)
                .getAnnotation(PostMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('promotion:coupon:send')", CouponController.class.getMethod("preview", CouponSendBatchReqVO.class)
                .getAnnotation(PreAuthorize.class).value());
        assertEquals("/send-batch", CouponController.class.getMethod("batch", Long.class)
                .getAnnotation(GetMapping.class).value()[0]);
    }
}
