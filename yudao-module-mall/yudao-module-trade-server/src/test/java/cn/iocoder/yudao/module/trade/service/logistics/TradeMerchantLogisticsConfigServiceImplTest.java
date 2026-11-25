package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigSaveReqVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeMerchantLogisticsConfigServiceImplTest {

    @Mock
    private TradeMerchantLogisticsConfigMapper configMapper;
    private TradeMerchantLogisticsConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TradeMerchantLogisticsConfigServiceImpl(configMapper);
    }

    @Test
    void loadsMerchantMonthlyCardWhenAnotherConfigMapperExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("configMapper", Object.class);
            context.registerBean("tradeMerchantLogisticsConfigMapper", TradeMerchantLogisticsConfigMapper.class,
                    () -> configMapper);
            context.register(TradeMerchantLogisticsConfigServiceImpl.class);
            context.refresh();

            when(configMapper.selectByMerchantId(12L)).thenReturn(new TradeMerchantLogisticsConfigDO()
                    .setMerchantId(12L).setMonthlyCard("SF123456789"));
            assertThat(context.getBean(TradeMerchantLogisticsConfigService.class).getRequiredMonthlyCard(12L))
                    .isEqualTo("SF123456789");
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

        Long id = service.save(new TradeMerchantLogisticsConfigSaveReqVO()
                .setMerchantId(12L).setMonthlyCard("NEW-CARD"));

        assertThat(id).isEqualTo(3L);
        assertThat(config.getMonthlyCard()).isEqualTo("NEW-CARD");
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
}
