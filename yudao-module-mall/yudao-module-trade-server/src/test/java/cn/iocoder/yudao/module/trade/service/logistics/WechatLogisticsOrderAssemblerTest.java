package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaExpressAddOrderReqDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WechatLogisticsOrderAssemblerTest {

    @Test
    void build_shouldUseTenantOrderAndConfiguredDefaults() {
        TradeOrderDO order = new TradeOrderDO().setId(42L).setNo("T202608230001")
                .setReceiverName("李四").setReceiverMobile("13800000000")
                .setReceiverDetailAddress("龙岗区茶园路 1 号");
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuName("明前龙井").setCount(2)
                .setPicUrl("https://cdn.example.com/tea.jpg");
        TradeWechatLogisticsConfigDO config = new TradeWechatLogisticsConfigDO()
                .setDeliveryId("SF").setBizId("sf-biz").setServiceType(0).setServiceName("标准快递")
                .setSenderName("张三").setSenderMobile("13900000000")
                .setSenderProvince("广东省").setSenderCity("深圳市").setSenderArea("龙岗区")
                .setSenderAddress("龙岗区仓库 1 号")
                .setDefaultWeight(new BigDecimal("1.20"))
                .setDefaultSpaceLength(new BigDecimal("30"))
                .setDefaultSpaceWidth(new BigDecimal("20"))
                .setDefaultSpaceHeight(new BigDecimal("10"));

        SocialWxaExpressAddOrderReqDTO request = new WechatLogisticsOrderAssembler()
                .build(7L, order, List.of(item), Collections.emptyMap(), config, "o-user");

        assertEquals(0, request.getAddSource());
        assertEquals("7-T202608230001", request.getOrderId());
        assertEquals("o-user", request.getOpenid());
        assertEquals("SF", request.getDeliveryId());
        assertEquals("sf-biz", request.getBizId());
        assertEquals(0L, request.getExpectTime());
        assertEquals("张三", request.getSender().getName());
        assertEquals("李四", request.getReceiver().getName());
        assertEquals("明前龙井", request.getCargo().getDetailList().get(0).getName());
        assertEquals(2, request.getCargo().getDetailList().get(0).getCount());
        assertEquals(new BigDecimal("1.20").doubleValue(), request.getCargo().getWeight());
        assertEquals("明前龙井", request.getShop().getDetailList().get(0).getGoodsName());
        assertEquals(0, request.getInsured().getUseInsured());
        assertEquals(0, request.getService().getServiceType());
    }
}
