package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsWaybillStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatLogisticsServiceImplTest {

    @Test
    void shouldRetryCreateWithoutWaybill_onlyForInvalidWaybillPrequery() {
        TradeWechatLogisticsWaybillDO invalidPrequery = new TradeWechatLogisticsWaybillDO()
                .setStatus(WechatLogisticsWaybillStatusEnum.UNKNOWN.name()).setErrorCode(9300528);
        TradeWechatLogisticsWaybillDO timeout = new TradeWechatLogisticsWaybillDO()
                .setStatus(WechatLogisticsWaybillStatusEnum.UNKNOWN.name());
        TradeWechatLogisticsWaybillDO knownWaybill = new TradeWechatLogisticsWaybillDO()
                .setStatus(WechatLogisticsWaybillStatusEnum.UNKNOWN.name()).setErrorCode(9300528)
                .setWaybillId("SF123");
        TradeWechatLogisticsWaybillDO failed = new TradeWechatLogisticsWaybillDO()
                .setStatus(WechatLogisticsWaybillStatusEnum.FAILED.name()).setErrorCode(9300528);

        assertTrue(WechatLogisticsServiceImpl.shouldRetryCreateWithoutWaybill(invalidPrequery));
        assertFalse(WechatLogisticsServiceImpl.shouldRetryCreateWithoutWaybill(timeout));
        assertFalse(WechatLogisticsServiceImpl.shouldRetryCreateWithoutWaybill(knownWaybill));
        assertFalse(WechatLogisticsServiceImpl.shouldRetryCreateWithoutWaybill(failed));
    }
}
