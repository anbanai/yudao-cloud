package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeOrderUpdateServiceImplTest {

    private final TradeOrderUpdateServiceImpl service = new TradeOrderUpdateServiceImpl();

    @Test
    void validateExpectedPrice_acceptsMatchingSnapshotAndLegacyClient() {
        TradePriceCalculateRespBO actual = actualPrice(8801, 3889);

        assertDoesNotThrow(() -> service.validateExpectedPrice(2L, new AppTradeOrderCreateReqVO(), actual));
        assertDoesNotThrow(() -> service.validateExpectedPrice(2L,
                new AppTradeOrderCreateReqVO().setExpectedPayPrice(8801).setExpectedUsePoint(3889), actual));
    }

    @Test
    void validateExpectedPrice_rejectsChangedPayPriceOrPoints() {
        TradePriceCalculateRespBO actual = actualPrice(8801, 3889);

        ServiceException priceException = assertThrows(ServiceException.class, () -> service.validateExpectedPrice(2L,
                new AppTradeOrderCreateReqVO().setExpectedPayPrice(8800).setExpectedUsePoint(3889), actual));
        assertThat(priceException.getCode()).isEqualTo(1_011_000_040);

        ServiceException pointException = assertThrows(ServiceException.class, () -> service.validateExpectedPrice(2L,
                new AppTradeOrderCreateReqVO().setExpectedPayPrice(8801).setExpectedUsePoint(3888), actual));
        assertThat(pointException.getCode()).isEqualTo(1_011_000_040);
    }

    private static TradePriceCalculateRespBO actualPrice(int payPrice, int usePoint) {
        return new TradePriceCalculateRespBO()
                .setPrice(new TradePriceCalculateRespBO.Price().setPayPrice(payPrice))
                .setUsePoint(usePoint);
    }

}
