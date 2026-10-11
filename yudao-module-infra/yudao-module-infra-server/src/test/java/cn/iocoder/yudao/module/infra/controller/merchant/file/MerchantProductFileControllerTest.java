package cn.iocoder.yudao.module.infra.controller.merchant.file;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.infra.enums.FileUploadSceneEnum;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.product.api.merchant.ProductMerchantApi;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantProductFileControllerTest {

    private final FileService fileService = mock(FileService.class);
    private final ProductMerchantApi productMerchantApi = mock(ProductMerchantApi.class);
    private final SystemMerchantUserApi systemMerchantUserApi = mock(SystemMerchantUserApi.class);
    private final MerchantFileController controller = new MerchantFileController();

    MerchantProductFileControllerTest() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "fileService", fileService);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "productMerchantApi", productMerchantApi);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "systemMerchantUserApi", systemMerchantUserApi);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadProductImage_usesProductPolicyAndAccountDirectory() throws Exception {
        authenticateAs(7L, UserTypeEnum.MERCHANT.getValue(), java.util.Map.of("merchantId", "17"));
        byte[] image = imageBytes();
        MockMultipartFile file = new MockMultipartFile("file", "tea.png", "image/png", image);
        when(productMerchantApi.validateMerchantAvailable(17L)).thenReturn(CommonResult.success(true));
        when(systemMerchantUserApi.validateMerchantBinding(7L, 17L)).thenReturn(CommonResult.success(true));
        when(fileService.createFile(eq(image), eq("tea.png"), eq("merchant-products/7"), eq("image/png"),
                eq(FileUploadSceneEnum.PRODUCT))).thenReturn("https://cdn.example.test/tea.png");

        String result = controller.uploadProductImage(file).getData();

        assertEquals("https://cdn.example.test/tea.png", result);
        verify(fileService).createFile(image, "tea.png", "merchant-products/7", "image/png",
                FileUploadSceneEnum.PRODUCT);
    }

    @Test
    void uploadProductImage_rejectsAccountBoundToDifferentMerchant() {
        authenticateAs(7L, UserTypeEnum.MERCHANT.getValue(), java.util.Map.of("merchantId", "17"));
        MockMultipartFile file = new MockMultipartFile("file", "tea.png", "image/png", new byte[]{1});
        when(systemMerchantUserApi.validateMerchantBinding(7L, 17L)).thenReturn(CommonResult.success(false));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.uploadProductImage(file));
        verify(productMerchantApi, never()).validateMerchantAvailable(17L);
        verify(fileService, never()).createFile(any(), any(), any(), any(), any());
    }

    @Test
    void uploadQualification_rejectsNonMerchantBeforeWritingFile() {
        authenticateAs(7L, UserTypeEnum.ADMIN.getValue(), java.util.Map.of());
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf", new byte[]{1});

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.uploadQualification(file));

        verify(fileService, never()).createPrivateFile(any(), any(), any(), any());
    }

    @Test
    void uploadQualification_allowsMerchantAndStoresFilePrivately() throws Exception {
        authenticateAs(7L, UserTypeEnum.MERCHANT.getValue(), java.util.Map.of());
        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf", pdf);
        FileDO savedFile = new FileDO().setId(123L);
        when(fileService.createPrivateFile(eq(pdf), eq("license.pdf"), eq("merchant-qualifications/7"),
                eq("application/pdf"))).thenReturn(savedFile);
        when(fileService.presignGetUrl(123L, 15 * 60)).thenReturn("https://private.example.test/signed");

        var result = controller.uploadQualification(file).getData();

        assertEquals(123L, result.getFileId());
        assertEquals("https://private.example.test/signed", result.getPreviewUrl());
        verify(fileService).createPrivateFile(pdf, "license.pdf", "merchant-qualifications/7", "application/pdf");
    }

    private static void authenticateAs(Long userId, Integer userType, java.util.Map<String, String> info) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setUserType(userType);
        loginUser.setInfo(info);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    private static byte[] imageBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }
}
