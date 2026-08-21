package cn.iocoder.yudao.module.trade.convert.delivery;

import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateChargeDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateFreeDO;
import cn.iocoder.yudao.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryExpressTemplateConvertTest {

    @Test
    void testConvertMap_provinceChargeMatchesDistrictAndOverridesCountryFree() {
        DeliveryExpressTemplateRespBO result = convert(650102,
                charge(1L, List.of(650000), 1200),
                free(1L, List.of(1), 0, 0));

        assertThat(result.getCharge().getStartPrice()).isEqualTo(1200);
        assertThat(result.getFree()).isNull();
    }

    @Test
    void testConvertMap_countryFreeMatchesOtherDistrict() {
        DeliveryExpressTemplateRespBO result = convert(110105,
                charge(1L, List.of(650000), 1200),
                free(1L, List.of(1), 0, 0));

        assertThat(result.getCharge()).isNull();
        assertThat(result.getFree().getFreePrice()).isZero();
        assertThat(result.getFree().getFreeCount()).isZero();
    }

    @Test
    void testConvertMap_sameSpecificityKeepsChargeAndFreeThreshold() {
        DeliveryExpressTemplateRespBO result = convert(650102,
                charge(1L, List.of(650102), 1200),
                free(1L, List.of(650102), 5000, 2));

        assertThat(result.getCharge()).isNotNull();
        assertThat(result.getFree().getFreePrice()).isEqualTo(5000);
        assertThat(result.getFree().getFreeCount()).isEqualTo(2);
    }

    @Test
    void testConvertMap_withoutMatchingAreaDoesNotReturnTemplate() {
        assertThat(DeliveryExpressTemplateConvert.INSTANCE
                .convertMap(110105, List.of(template(1L)),
                        List.of(charge(1L, List.of(650000), 1200)), List.of()))
                .isEmpty();
    }

    private static DeliveryExpressTemplateRespBO convert(Integer areaId,
                                                         DeliveryExpressTemplateChargeDO charge,
                                                         DeliveryExpressTemplateFreeDO free) {
        return DeliveryExpressTemplateConvert.INSTANCE
                .convertMap(areaId, List.of(template(1L)), List.of(charge), List.of(free))
                .get(1L);
    }

    private static DeliveryExpressTemplateDO template(Long id) {
        return new DeliveryExpressTemplateDO().setId(id).setChargeMode(1).setName("test").setSort(0);
    }

    private static DeliveryExpressTemplateChargeDO charge(Long templateId, List<Integer> areaIds,
                                                           Integer startPrice) {
        return new DeliveryExpressTemplateChargeDO().setTemplateId(templateId).setAreaIds(areaIds)
                .setChargeMode(1).setStartCount(1D).setStartPrice(startPrice)
                .setExtraCount(1D).setExtraPrice(0);
    }

    private static DeliveryExpressTemplateFreeDO free(Long templateId, List<Integer> areaIds,
                                                      Integer freePrice, Integer freeCount) {
        return new DeliveryExpressTemplateFreeDO().setTemplateId(templateId).setAreaIds(areaIds)
                .setFreePrice(freePrice).setFreeCount(freeCount);
    }

}
