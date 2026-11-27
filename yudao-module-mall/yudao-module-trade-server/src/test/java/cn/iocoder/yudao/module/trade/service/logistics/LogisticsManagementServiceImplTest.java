package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.SfLogisticsAccountSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintDeviceMapper;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfOpenApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintDeviceSaveReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintTaskRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintTaskMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogisticsManagementServiceImplTest {

    @Mock private TradeLogisticsAccountMapper accountMapper;
    @Mock private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Mock private DeliveryExpressMapper expressMapper;
    @Mock private FileApi fileApi;
    @Mock private TradeLogisticsPrintTaskMapper taskMapper;
    @Mock private PrintBridgeConfigFileGenerator configFileGenerator;
    private LogisticsManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogisticsManagementServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(service, "expressMapper", expressMapper);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "configFileGenerator", configFileGenerator);
        ReflectionTestUtils.setField(service, "printBridgeTaskEndpoint",
                "https://api.example.test/internal-api/trade/logistics/printbridge/tasks");
        ReflectionTestUtils.setField(service, "printBridgeMerchantOrigin", "https://admin.example.test");
    }

    @Test
    void saveAccountAutomaticallyUsesSfExpressAndOfficialEndpoint() {
        SfLogisticsAccountSaveReqVO request = validRequest();
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));

        service.saveAccount(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsAccountDO.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getLogisticsId()).isEqualTo(6L);
        assertThat(captor.getValue().getEndpoint()).isEqualTo(SfOpenApiClient.PRODUCTION_ENDPOINT);
    }

    @Test
    void saveAccountRejectsMissingExpress() {
        SfLogisticsAccountSaveReqVO request = validRequest();
        when(expressMapper.selectByCode("SF")).thenReturn(null);

        assertThatThrownBy(() -> service.saveAccount(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("顺丰");
        verifyNoInteractions(accountMapper);
    }

    @Test
    void saveAccountKeepsVerifiedTemplateWhenHiddenOnEdit() {
        SfLogisticsAccountSaveReqVO request = validRequest().setId(7L);
        TradeLogisticsAccountDO existing = new TradeLogisticsAccountDO().setId(7L)
                .setTemplateCode("fm_verified_merchant_template")
                .setPartnerId("partner").setCheckWord("check-word");
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));
        when(accountMapper.selectById(7L)).thenReturn(existing);

        service.saveAccount(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsAccountDO.class);
        verify(accountMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("fm_verified_merchant_template");
    }

    @Test
    void saveAccountClearsDedicatedTemplateWhenPartnerChanges() {
        var request = validRequest().setId(7L).setPartnerId("new-partner");
        var existing = new TradeLogisticsAccountDO().setId(7L).setPartnerId("partner")
                .setTemplateCode("fm_verified_merchant_template").setPaperWidthMm(100).setPaperHeightMm(150);
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L));
        when(accountMapper.selectById(7L)).thenReturn(existing);

        service.saveAccount(request);

        verify(accountMapper).updateById(existing);
        assertThat(existing.getPartnerId()).isEqualTo("new-partner");
        assertThat(existing.getTemplateCode()).isNull();
        assertThat(new SfLabelTemplateResolver("").supportedPaperSpecs(existing)).containsExactly("76x130");
    }

    @Test
    void saveDisabledAccountDoesNotReplaceDefaultAccount() {
        var request = validRequest().setStatus(1).setDefaultFlag(true);
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L));

        service.saveAccount(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsAccountDO.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getDefaultFlag()).isFalse();
        verify(accountMapper, never()).clearDefault(null);
    }

    @Test
    void enrollDeviceUsesOnlyServerConfigurationAndDoesNotExposeToken() {
        when(deviceMapper.selectPendingEnrollmentForUpdate(41L)).thenReturn(null);
        when(deviceMapper.selectDefaultEnabled(41L)).thenReturn(null);
        when(configFileGenerator.generate(any(), any(), any(), any(), any())).thenReturn("encrypted-config");

        var response = service.enrollDevice(41L);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsPrintDeviceDO.class);
        verify(deviceMapper).insert(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo(41L);
        assertThat(captor.getValue().getEnrollmentKey()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getDeviceCode()).doesNotStartWith("pending-");
        assertThat(captor.getValue().getDeviceName()).startsWith("打印工作站-");
        assertThat(response.getConfigFile()).isEqualTo("encrypted-config");
        assertThat(response.getEnrollmentExpiresTime()).isAfter(LocalDateTime.now().plusMinutes(9));
        verify(configFileGenerator).generate(
                eq("https://api.example.test/internal-api/trade/logistics/printbridge/tasks"),
                eq("https://admin.example.test"), any(), eq(captor.getValue().getDeviceCode()),
                eq(captor.getValue().getDeviceName()));
    }

    @Test
    void enrollDeviceRejectsDuplicateFreshEnrollment() {
        when(deviceMapper.selectPendingEnrollmentForUpdate(41L)).thenReturn(new TradeLogisticsPrintDeviceDO()
                .setId(9L).setEnrollmentKey("ACTIVE").setTokenCreatedTime(LocalDateTime.now()));

        assertThatThrownBy(() -> service.enrollDevice(41L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("配置已生成");
        verifyNoInteractions(configFileGenerator);
    }

    @Test
    void enrollDeviceRejectsMissingServerConfiguration() {
        ReflectionTestUtils.setField(service, "printBridgeTaskEndpoint", "");

        assertThatThrownBy(() -> service.enrollDevice(41L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PRINTBRIDGE_TASK_ENDPOINT");
        verifyNoInteractions(deviceMapper, configFileGenerator);
    }

    @Test
    void enrollDeviceRejectsAdminOriginWithPathOrTrailingSlash() {
        ReflectionTestUtils.setField(service, "printBridgeMerchantOrigin", "https://admin.example.test/");

        assertThatThrownBy(() -> service.enrollDevice(41L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能带路径或尾部斜杠");
        verifyNoInteractions(deviceMapper, configFileGenerator);
    }

    @Test
    void saveDeviceRejectsAnotherMerchantDevice() {
        var request = new LogisticsPrintDeviceSaveReqVO()
                .setId(7L).setPrinterName("printer").setDefaultFlag(true);
        assertThatThrownBy(() -> service.saveDevice(41L, request)).hasMessageContaining("打印设备不存在");
        verify(deviceMapper).selectByIdAndMerchantId(7L, 41L);
        org.mockito.Mockito.verifyNoMoreInteractions(deviceMapper);
    }

    @Test
    void changingDefaultPrinterOnlyClearsDefaultsForThisMerchant() {
        var device = new TradeLogisticsPrintDeviceDO().setId(7L).setMerchantId(41L);
        when(deviceMapper.selectByIdAndMerchantId(7L, 41L)).thenReturn(device);
        var request = new LogisticsPrintDeviceSaveReqVO().setId(7L).setPrinterName("Packing").setDefaultFlag(true);

        var response = service.saveDevice(41L, request);

        assertThat(response.getPrinterName()).isEqualTo("Packing");
        verify(deviceMapper).clearDefault(41L, 7L);
        verify(deviceMapper).updateSettings(device);
    }

    @Test
    void diagnosticRejectsAnotherMerchantDeviceBeforeCreatingFile() {
        assertThatThrownBy(() -> service.createTestPrint(41L, 7L, 76, 130))
                .hasMessageContaining("打印设备不存在");
        verifyNoInteractions(fileApi);
    }

    @Test
    void testPrintQueuesRequested76x130LabelForOwnedDevice() throws Exception {
        doReturn(new FileCreateRespDTO().setId(88L).setUrl("https://files.example/diagnostic"))
                .when(fileApi).createPrivateFile(any(byte[].class), eq("printbridge-test-76x130.png"),
                        eq("trade/logistics/9/41/diagnostics"), eq("image/png"));

        when(deviceMapper.selectByIdAndMerchantId(7L, 41L)).thenReturn(new TradeLogisticsPrintDeviceDO()
                .setId(7L).setStatus(0).setPrinterName("Printer"));
        TenantContextHolder.setTenantId(9L);
        LogisticsPrintTaskRespVO response;
        try {
            response = service.createTestPrint(41L, 7L, 76, 130);
        } finally {
            TenantContextHolder.clear();
        }

        var contentCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(fileApi).createPrivateFile(contentCaptor.capture(),
                eq("printbridge-test-76x130.png"), eq("trade/logistics/9/41/diagnostics"), eq("image/png"));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(contentCaptor.getValue()));
        assertThat(image.getWidth()).isEqualTo(607);
        assertThat(image.getHeight()).isEqualTo(1039);
        var taskCaptor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsPrintTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        TradeLogisticsPrintTaskDO task = taskCaptor.getValue();
        assertThat(task.getMerchantId()).isEqualTo(41L);
        assertThat(task.getDeviceId()).isEqualTo(7L);
        assertThat(task.getLabelFileId()).isEqualTo(88L);
        assertThat(task.getTestFlag()).isTrue();
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    private static SfLogisticsAccountSaveReqVO validRequest() {
        return new SfLogisticsAccountSaveReqVO().setPartnerId("partner").setCheckWord("check-word");
    }
}
