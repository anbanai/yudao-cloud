package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaExpressAddOrderReqDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void build_shouldAddSpecificationCodePriceAndCountToPrintableFields() {
        TradeOrderDO order = new TradeOrderDO().setId(42L).setNo("T202608230001")
                .setReceiverName("李四").setReceiverMobile("13800000000")
                .setReceiverDetailAddress("龙岗区茶园路 1 号");
        TradeOrderItemDO item = new TradeOrderItemDO().setSkuId(1001L).setSpuName("寻味勐海-2013年孔雀班章生普")
                .setProperties(List.of(
                        new TradeOrderItemDO.Property().setPropertyName("规格").setValueName("1饼/357g"),
                        new TradeOrderItemDO.Property().setPropertyName("包装").setValueName("礼盒")))
                .setPrice(35700).setCount(2).setPicUrl("https://cdn.example.com/tea.jpg");
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(1001L);
        sku.setBarCode("CD74912B");
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
                .build(7L, order, List.of(item), Map.of(1001L, sku), config, "o-user");

        assertEquals("寻味勐海-2013年孔雀班章生普 规格:1饼/357g/包装:礼盒 编码:CD74912B 价格:¥357.00",
                request.getCargo().getDetailList().get(0).getName());
        assertEquals(2, request.getCargo().getDetailList().get(0).getCount());
        assertEquals("寻味勐海-2013年孔雀班章生普 规格:1饼/357g/包装:礼盒",
                request.getShop().getDetailList().get(0).getGoodsName());
        assertEquals("编码:CD74912B 价格:¥357.00 数量:2",
                request.getShop().getDetailList().get(0).getGoodsDesc());
        assertEquals("寻味勐海-2013年孔雀班章生普 规格:1饼/357g/包装:礼盒 编码:CD74912B 价格:¥357.00 数量:2",
                request.getCustomRemark());
    }

    @Test
    void build_shouldPreserveCodeAndPriceWhenCargoNameExceedsWechatLimit() {
        TradeOrderDO order = new TradeOrderDO().setId(42L).setNo("T202608230001")
                .setReceiverName("李四").setReceiverMobile("13800000000")
                .setReceiverDetailAddress("龙岗区茶园路 1 号");
        TradeOrderItemDO item = new TradeOrderItemDO().setSkuId(1001L)
                .setSpuName("这是一款名称非常长需要验证微信物流商品名称字节限制仍然保留编码和价格的测试商品")
                .setPrice(35700).setCount(1);
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(1001L);
        sku.setBarCode("CD74912B");
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
                .build(7L, order, List.of(item), Map.of(1001L, sku), config, "o-user");
        String cargoName = request.getCargo().getDetailList().get(0).getName();

        assertTrue(cargoName.getBytes(StandardCharsets.UTF_8).length <= 128);
        assertTrue(cargoName.endsWith("编码:CD74912B 价格:¥357.00"));
    }

    @Test
    void build_shouldKeepCargoNameWithinLimitWhenCodeAloneIsTooLong() {
        TradeOrderDO order = new TradeOrderDO().setId(42L).setNo("T202608230001")
                .setReceiverName("李四").setReceiverMobile("13800000000")
                .setReceiverDetailAddress("龙岗区茶园路 1 号");
        TradeOrderItemDO item = new TradeOrderItemDO().setSkuId(1001L).setSpuName("测试商品")
                .setPrice(35700).setCount(1);
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(1001L);
        sku.setBarCode("超长编码".repeat(16));
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
                .build(7L, order, List.of(item), Map.of(1001L, sku), config, "o-user");
        String cargoName = request.getCargo().getDetailList().get(0).getName();

        assertTrue(cargoName.getBytes(StandardCharsets.UTF_8).length <= 128);
        assertTrue(cargoName.endsWith("价格:¥357.00"));
        assertFalse(cargoName.contains("编码:"));
    }

    @Test
    void build_shouldOnlyAddCompleteItemsWhenCustomRemarkExceedsLimit() {
        TradeOrderDO order = new TradeOrderDO().setId(42L).setNo("T202608230001")
                .setReceiverName("李四").setReceiverMobile("13800000000")
                .setReceiverDetailAddress("龙岗区茶园路 1 号");
        TradeOrderItemDO item = new TradeOrderItemDO().setSkuId(1001L)
                .setSpuName("这是用于验证超长备注不会截断商品价格和数量的商品名称")
                .setPrice(35700).setCount(1);
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(1001L);
        sku.setBarCode("CD74912B");
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
                .build(7L, order, Collections.nCopies(20, item), Map.of(1001L, sku), config, "o-user");
        String customRemark = request.getCustomRemark();

        assertTrue(customRemark.getBytes(StandardCharsets.UTF_8).length <= 1024);
        assertFalse(customRemark.isBlank());
        for (String entry : customRemark.split("; ")) {
            assertTrue(entry.endsWith("编码:CD74912B 价格:¥357.00 数量:1"), entry);
        }
    }
}
