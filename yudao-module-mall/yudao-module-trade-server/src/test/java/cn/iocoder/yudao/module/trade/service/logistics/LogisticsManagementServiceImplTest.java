package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.SfLogisticsAccountSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintDeviceMapper;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsManagementServiceImplTest {

    @Mock private TradeLogisticsAccountMapper accountMapper;
    @Mock private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Mock private DeliveryExpressMapper expressMapper;
    @Mock private FileApi fileApi;
    private LogisticsManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogisticsManagementServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(service, "expressMapper", expressMapper);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
    }

    @Test
    void saveAccountRejectsNonSfExpress() {
        SfLogisticsAccountSaveReqVO request = validRequest().setLogisticsId(8L);
        when(expressMapper.selectById(8L)).thenReturn(new DeliveryExpressDO().setId(8L).setCode("YTO"));

        assertThatThrownBy(() -> service.saveAccount(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("顺丰");
        verifyNoInteractions(accountMapper);
    }

    @Test
    void saveAccountRejectsMissingExpress() {
        SfLogisticsAccountSaveReqVO request = validRequest().setLogisticsId(8L);
        when(expressMapper.selectById(8L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveAccount(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("顺丰");
        verifyNoInteractions(accountMapper);
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
        return new SfLogisticsAccountSaveReqVO().setName("顺丰月结账号").setLogisticsId(1L)
                .setEndpoint("https://sfapi.sf-express.com/std/service").setPartnerId("partner")
                .setCheckWord("check-word").setMonthlyCard("monthly-card").setServiceCode("1")
                .setTemplateCode("fm_100150").setSenderName("仓库").setSenderPhone("13800138000")
                .setSenderProvince("四川省").setSenderCity("成都市").setSenderAddress("高新区 1 号")
                .setDefaultWeightKg(BigDecimal.ONE).setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203);
    }
}
