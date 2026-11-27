package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeMerchantLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeMerchantLogisticsConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeMerchantLogisticsConfigServiceImplTest {

    @Mock
    private TradeMerchantLogisticsConfigMapper configMapper;
    @Mock
    private TradeLogisticsAccountMapper accountMapper;
    private final SfLabelTemplateResolver templates = new SfLabelTemplateResolver("");
    private TradeMerchantLogisticsConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TradeMerchantLogisticsConfigServiceImpl(configMapper, accountMapper, templates);
    }

    @Test
    void loadsMerchantMonthlyCardWhenAnotherConfigMapperExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("configMapper", Object.class);
            context.registerBean("tradeMerchantLogisticsConfigMapper", TradeMerchantLogisticsConfigMapper.class,
                    () -> configMapper);
            context.registerBean(TradeLogisticsAccountMapper.class, () -> accountMapper);
            context.registerBean(SfLabelTemplateResolver.class, () -> templates);
            context.register(TradeMerchantLogisticsConfigServiceImpl.class);
            context.refresh();

            when(configMapper.selectByMerchantId(12L)).thenReturn(new TradeMerchantLogisticsConfigDO()
                    .setMerchantId(12L).setMonthlyCard("SF123456789"));
            assertThat(context.getBean(TradeMerchantLogisticsConfigService.class).get(12L).getMonthlyCardMasked())
                    .isEqualTo("****6789");
        }
    }

    @Test
    void getForMerchantReturnsOnlyMaskedCard() {
        when(configMapper.selectByMerchantId(12L)).thenReturn(new TradeMerchantLogisticsConfigDO()
                .setId(3L).setMerchantId(12L).setMonthlyCard("SF123456789"));

        var result = service.getForMerchant(12L);

        assertThat(result.getMonthlyCardMasked()).isEqualTo("****6789");
    }

    @Test
    void saveUpdatesTheExistingMerchantConfiguration() {
        TradeMerchantLogisticsConfigDO config = new TradeMerchantLogisticsConfigDO()
                .setId(3L).setMerchantId(12L).setMonthlyCard("OLD-CARD");
        when(configMapper.selectByMerchantIdForUpdate(12L)).thenReturn(config);

        service.saveForMerchant(12L, new MerchantSfLogisticsConfigSaveReqVO()
                .setMonthlyCard(" ").setServiceCode("2").setSenderName("本店"));

        assertThat(config.getMonthlyCard()).isEqualTo("OLD-CARD");
        assertThat(config.getSenderName()).isEqualTo("本店");
        assertThat(config.getServiceCode()).isEqualTo("2");
        verify(configMapper).updateById(config);
    }

    @Test
    void merchantSaveUsesAuthenticatedMerchantId() {
        when(configMapper.selectByMerchantIdForUpdate(12L)).thenReturn(null);

        service.saveForMerchant(12L, new MerchantSfLogisticsConfigSaveReqVO().setMonthlyCard("MERCHANT-CARD"));

        var config = org.mockito.ArgumentCaptor.forClass(TradeMerchantLogisticsConfigDO.class);
        verify(configMapper).insert(config.capture());
        assertThat(config.getValue().getMerchantId()).isEqualTo(12L);
        assertThat(config.getValue().getMonthlyCard()).isEqualTo("MERCHANT-CARD");
    }

    @Test
    void merchantGetsAvailablePaperAndAdminGetsCompletionAndMaskedCard() {
        var config = new TradeMerchantLogisticsConfigDO().setMerchantId(12L).setMonthlyCard("SF123456")
                .setServiceCode("1").setDefaultWeightKg(BigDecimal.ONE)
                .setSenderName("仓库").setSenderPhone("13800138000").setSenderProvince("四川")
                .setSenderCity("成都").setSenderAddress("一号").setPaperWidthMm(76).setPaperHeightMm(130).setDpi(203);
        when(configMapper.selectByMerchantId(12L)).thenReturn(config);
        when(accountMapper.selectDefaultEnabled()).thenReturn(new TradeLogisticsAccountDO().setPartnerId("partner"));

        var merchant = service.getForMerchant(12L);
        assertThat(merchant.getSupportedPaperSpecs()).containsExactly("76x130");
        assertThat(merchant.getReturnSameAsSender()).isTrue();
        assertThat(merchant.getSenderName()).isEqualTo("仓库");
        assertThat(service.get(12L).isConfigured()).isTrue();
        assertThat(service.get(12L).getMonthlyCardMasked()).isEqualTo("****3456");
    }

    @Test
    void returnCanUseIndependentWarehouseWithoutOutboundLabelOrSender() {
        var config = new TradeMerchantLogisticsConfigDO().setMerchantId(12L).setMonthlyCard("card")
                .setServiceCode("2").setDefaultWeightKg(BigDecimal.ONE).setReturnSameAsSender(false)
                .setReturnName("售后").setReturnPhone("13800138000").setReturnProvince("四川")
                .setReturnCity("成都").setReturnAddress("二号");
        when(configMapper.selectByMerchantId(12L)).thenReturn(config);
        assertThat(service.getRequiredReturnConfig(12L)).isSameAs(config);
        assertThatThrownBy(() -> service.getRequiredShippingConfig(12L)).hasMessageContaining("发件");
        config.setReturnSameAsSender(true);
        assertThatThrownBy(() -> service.getRequiredReturnConfig(12L)).hasMessageContaining("退货收件人");
    }

    @Test
    void cannotChooseAnotherAccountsLegacyLargeTemplate() {
        var account = new TradeLogisticsAccountDO().setPartnerId("partner").setPaperWidthMm(100)
                .setPaperHeightMm(150).setTemplateCode("fm_old_large");
        assertThat(templates.requireTemplate(account, 100, 150, 203)).isEqualTo("fm_old_large");
        var another = new TradeLogisticsAccountDO().setPartnerId("other");
        assertThatThrownBy(() -> templates.requireTemplate(another, 100, 150, 203)).hasMessageContaining("规格");
        assertThat(templates.requireTemplate(another, 76, 130, 203)).isEqualTo("fm_76130_standard_other");
        assertThat(new SfLabelTemplateResolver("fm_server").supportedPaperSpecs(another))
                .containsExactly("76x130", "100x150");
    }
}
