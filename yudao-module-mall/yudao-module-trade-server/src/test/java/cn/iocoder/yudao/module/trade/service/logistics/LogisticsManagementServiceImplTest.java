package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogisticsManagementServiceImplTest {

    @Mock private TradeLogisticsAccountMapper accountMapper;
    @Mock private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Mock private DeliveryExpressMapper expressMapper;
    @Mock private FileApi fileApi;
    @Mock private PrintBridgeConfigFileGenerator configFileGenerator;
    private LogisticsManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogisticsManagementServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(service, "expressMapper", expressMapper);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "configFileGenerator", configFileGenerator);
        ReflectionTestUtils.setField(service, "printBridgeTaskEndpoint",
                "https://api.example.test/internal-api/trade/logistics/printbridge/tasks");
        ReflectionTestUtils.setField(service, "printBridgeAdminOrigin", "https://admin.example.test");
        ReflectionTestUtils.setField(service, "template100x150Code", "fm_server_100150");
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
    void saveAccountRejectsUnsupportedProductCode() {
        SfLogisticsAccountSaveReqVO request = validRequest().setServiceCode("99");
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));

        assertThatThrownBy(() -> service.saveAccount(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("顺丰特快或顺丰标快");
        verifyNoInteractions(accountMapper);
    }

    @Test
    void saveAccountKeepsVerifiedTemplateWhenHiddenOnEdit() {
        SfLogisticsAccountSaveReqVO request = validRequest().setId(7L);
        TradeLogisticsAccountDO existing = new TradeLogisticsAccountDO().setId(7L)
                .setTemplateCode("fm_verified_merchant_template")
                .setPartnerId("partner").setCheckWord("check-word").setMonthlyCard("monthly-card");
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));
        when(accountMapper.selectById(7L)).thenReturn(existing);

        service.saveAccount(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsAccountDO.class);
        verify(accountMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("fm_verified_merchant_template");
    }

    @Test
    void saveAccountGeneratesDocumented76x130StandardTemplate() {
        SfLogisticsAccountSaveReqVO request = validRequest().setPaperWidthMm(76).setPaperHeightMm(130)
                .setPartnerId("partner-001");
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));

        service.saveAccount(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsAccountDO.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("fm_76130_standard_partner-001");
    }

    @Test
    void saveAccountUsesServerTemplateFor100x150() {
        SfLogisticsAccountSaveReqVO request = validRequest().setPaperWidthMm(100).setPaperHeightMm(150);
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));

        service.saveAccount(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsAccountDO.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("fm_server_100150");
    }

    @Test
    void saveAccountRequiresServerTemplateFor100x150() {
        ReflectionTestUtils.setField(service, "template100x150Code", "");
        SfLogisticsAccountSaveReqVO request = validRequest().setPaperWidthMm(100).setPaperHeightMm(150);
        when(expressMapper.selectByCode("SF")).thenReturn(new DeliveryExpressDO().setId(6L).setCode("SF"));

        assertThatThrownBy(() -> service.saveAccount(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("服务器未配置顺丰 100×150 模板");
        verifyNoInteractions(accountMapper);
    }

    @Test
    void enrollDeviceUsesOnlyServerConfigurationAndDoesNotExposeToken() {
        when(deviceMapper.selectPendingEnrollmentForUpdate()).thenReturn(null);
        when(deviceMapper.selectDefaultEnabled()).thenReturn(null);
        when(configFileGenerator.generate(any(), any(), any(), any(), any())).thenReturn("encrypted-config");

        var response = service.enrollDevice();

        var captor = org.mockito.ArgumentCaptor.forClass(TradeLogisticsPrintDeviceDO.class);
        verify(deviceMapper).insert(captor.capture());
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
        when(deviceMapper.selectPendingEnrollmentForUpdate()).thenReturn(new TradeLogisticsPrintDeviceDO()
                .setId(9L).setEnrollmentKey("ACTIVE").setTokenCreatedTime(LocalDateTime.now()));

        assertThatThrownBy(() -> service.enrollDevice())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("配置已生成");
        verifyNoInteractions(configFileGenerator);
    }

    @Test
    void enrollDeviceRejectsMissingServerConfiguration() {
        ReflectionTestUtils.setField(service, "printBridgeTaskEndpoint", "");

        assertThatThrownBy(() -> service.enrollDevice())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PRINTBRIDGE_TASK_ENDPOINT");
        verifyNoInteractions(deviceMapper, configFileGenerator);
    }

    @Test
    void enrollDeviceRejectsAdminOriginWithPathOrTrailingSlash() {
        ReflectionTestUtils.setField(service, "printBridgeAdminOrigin", "https://admin.example.test/");

        assertThatThrownBy(() -> service.enrollDevice())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能带路径或尾部斜杠");
        verifyNoInteractions(deviceMapper, configFileGenerator);
    }

    @Test
    void createDiagnosticPayloadUsesRequested76x130Specification() throws Exception {
        doReturn("private://diagnostic").when(fileApi).createFile(any(byte[].class),
                eq("printbridge-test-76x130.png"), eq("trade/logistics/diagnostics"), eq("image/png"));
        when(fileApi.presignGetUrl("private://diagnostic", 15 * 60))
                .thenReturn(success("https://files.example.test/diagnostic?signature=x&expires=1"));

        String url = service.createDiagnosticPayload(76, 130);

        var contentCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(fileApi).createFile(contentCaptor.capture(),
                eq("printbridge-test-76x130.png"), eq("trade/logistics/diagnostics"), eq("image/png"));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(contentCaptor.getValue()));
        assertThat(image.getWidth()).isEqualTo(607);
        assertThat(image.getHeight()).isEqualTo(1039);
        assertThat(url).contains("diagnostic");
    }

    private static SfLogisticsAccountSaveReqVO validRequest() {
        return new SfLogisticsAccountSaveReqVO().setName("顺丰月结账号").setPartnerId("partner")
                .setCheckWord("check-word").setMonthlyCard("monthly-card").setServiceCode("1")
                .setSenderName("仓库").setSenderPhone("13800138000")
                .setSenderProvince("四川省").setSenderCity("成都市").setSenderAddress("高新区 1 号")
                .setDefaultWeightKg(BigDecimal.ONE).setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203);
    }
}
