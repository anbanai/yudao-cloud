package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileApiImplTest {

    private final FileService fileService = mock(FileService.class);
    private final FileApiImpl fileApi = new FileApiImpl();

    FileApiImplTest() {
        ReflectionTestUtils.setField(fileApi, "fileService", fileService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void qualificationFile_requiresMerchantAndOwnedDirectory() {
        authenticateAsMerchant(7L);
        FileDO qualificationFile = new FileDO().setPath("merchant-qualifications/7/license.pdf");
        qualificationFile.setCreator("7");
        FileDO productFile = new FileDO().setPath("merchant-products/7/image.png");
        productFile.setCreator("7");
        when(fileService.getFile(1L)).thenReturn(qualificationFile);
        when(fileService.getFile(2L)).thenReturn(productFile);

        assertThat(fileApi.isMerchantQualificationFileOwnedByCurrentUser(1L).getCheckedData()).isTrue();
        assertThat(fileApi.isMerchantQualificationFileOwnedByCurrentUser(2L).getCheckedData()).isFalse();
    }

    @Test
    void qualificationFile_rejectsAdminEvenWhenCreatorMatches() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(7L);
        loginUser.setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
        FileDO qualificationFile = new FileDO().setPath("merchant-qualifications/7/license.pdf");
        qualificationFile.setCreator("7");
        when(fileService.getFile(1L)).thenReturn(qualificationFile);

        assertThat(fileApi.isMerchantQualificationFileOwnedByCurrentUser(1L).getCheckedData()).isFalse();
    }

    @Test
    void qualificationFile_canValidateExplicitOwnerWithoutRequestContext() {
        FileDO qualificationFile = new FileDO().setPath("merchant-qualifications/7/license.pdf");
        qualificationFile.setCreator("7");
        when(fileService.getFile(1L)).thenReturn(qualificationFile);

        assertThat(fileApi.isMerchantQualificationFileOwnedBy(1L, 7L).getCheckedData()).isTrue();
        assertThat(fileApi.isMerchantQualificationFileOwnedBy(1L, 8L).getCheckedData()).isFalse();
    }

    private static void authenticateAsMerchant(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setUserType(UserTypeEnum.MERCHANT.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }
}
