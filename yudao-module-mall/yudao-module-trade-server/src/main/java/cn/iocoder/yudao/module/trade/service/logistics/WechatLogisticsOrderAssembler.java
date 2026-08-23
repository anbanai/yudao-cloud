package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaExpressAddOrderReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将交易订单组装为微信物流助手 addOrder 请求。
 */
@Slf4j
public class WechatLogisticsOrderAssembler {

    public SocialWxaExpressAddOrderReqDTO build(Long tenantId, TradeOrderDO order,
                                                 List<TradeOrderItemDO> items,
                                                 Map<Long, ProductSkuRespDTO> skuMap,
                                                 TradeWechatLogisticsConfigDO config,
                                                 String openid) {
        SocialWxaExpressAddOrderReqDTO request = new SocialWxaExpressAddOrderReqDTO()
                .setAddSource(0)
                .setOrderId(tenantId + "-" + order.getNo())
                .setOpenid(openid)
                .setDeliveryId(config.getDeliveryId())
                .setBizId(config.getBizId())
                .setExpectTime(0L)
                .setSender(buildSender(config))
                .setReceiver(buildReceiver(order))
                .setCargo(buildCargo(items, skuMap, config))
                .setShop(buildShop(items, order))
                .setInsured(new SocialWxaExpressAddOrderReqDTO.Insured()
                        .setUseInsured(0).setInsuredValue(0))
                .setService(new SocialWxaExpressAddOrderReqDTO.Service()
                        .setServiceType(config.getServiceType()).setServiceName(config.getServiceName()));
        return request;
    }

    private SocialWxaExpressAddOrderReqDTO.Person buildSender(TradeWechatLogisticsConfigDO config) {
        return new SocialWxaExpressAddOrderReqDTO.Person()
                .setName(config.getSenderName()).setTel(config.getSenderTel()).setMobile(config.getSenderMobile())
                .setCompany(config.getSenderCompany()).setPostCode(config.getSenderPostCode())
                .setCountry(config.getSenderCountry()).setProvince(config.getSenderProvince())
                .setCity(config.getSenderCity()).setArea(config.getSenderArea()).setAddress(config.getSenderAddress());
    }

    private SocialWxaExpressAddOrderReqDTO.Person buildReceiver(TradeOrderDO order) {
        SocialWxaExpressAddOrderReqDTO.Person receiver = new SocialWxaExpressAddOrderReqDTO.Person()
                .setName(order.getReceiverName()).setMobile(order.getReceiverMobile())
                .setAddress(order.getReceiverDetailAddress());
        Area area = order.getReceiverAreaId() == null ? null : AreaUtils.getArea(order.getReceiverAreaId());
        while (area != null) {
            if (AreaTypeEnum.PROVINCE.getType().equals(area.getType())) {
                receiver.setProvince(area.getName());
            } else if (AreaTypeEnum.CITY.getType().equals(area.getType())) {
                receiver.setCity(area.getName());
            } else if (AreaTypeEnum.DISTRICT.getType().equals(area.getType())) {
                receiver.setArea(area.getName());
            }
            area = area.getParent();
        }
        return receiver;
    }

    private SocialWxaExpressAddOrderReqDTO.Cargo buildCargo(List<TradeOrderItemDO> items,
                                                              Map<Long, ProductSkuRespDTO> skuMap,
                                                              TradeWechatLogisticsConfigDO config) {
        List<TradeOrderItemDO> safeItems = items == null ? Collections.emptyList() : items;
        Map<Long, ProductSkuRespDTO> safeSkuMap = skuMap == null ? Collections.emptyMap() : skuMap;
        BigDecimal totalWeight = BigDecimal.ZERO;
        boolean hasSkuWeight = !safeItems.isEmpty();
        List<SocialWxaExpressAddOrderReqDTO.CargoDetail> details = new ArrayList<>();
        for (TradeOrderItemDO item : safeItems) {
            ProductSkuRespDTO sku = safeSkuMap.get(item.getSkuId());
            if (sku == null || sku.getWeight() == null || sku.getWeight() <= 0) {
                hasSkuWeight = false;
            } else {
                totalWeight = totalWeight.add(BigDecimal.valueOf(sku.getWeight())
                        .multiply(BigDecimal.valueOf(item.getCount() == null ? 0 : item.getCount())));
            }
            details.add(new SocialWxaExpressAddOrderReqDTO.CargoDetail()
                    .setName(item.getSpuName()).setCount(item.getCount()));
        }
        if (!hasSkuWeight || totalWeight.signum() <= 0) {
            totalWeight = config.getDefaultWeight();
        }
        return new SocialWxaExpressAddOrderReqDTO.Cargo()
                .setCount(1).setWeight(toDouble(totalWeight))
                .setSpaceX(toDouble(config.getDefaultSpaceLength()))
                .setSpaceY(toDouble(config.getDefaultSpaceWidth()))
                .setSpaceZ(toDouble(config.getDefaultSpaceHeight()))
                .setDetailList(details);
    }

    private SocialWxaExpressAddOrderReqDTO.Shop buildShop(List<TradeOrderItemDO> items, TradeOrderDO order) {
        List<TradeOrderItemDO> safeItems = items == null ? Collections.emptyList() : items;
        List<SocialWxaExpressAddOrderReqDTO.ShopDetail> details = safeItems.stream()
                .map(item -> new SocialWxaExpressAddOrderReqDTO.ShopDetail()
                        .setGoodsName(item.getSpuName()).setGoodsImgUrl(item.getPicUrl())
                        .setGoodsDesc(item.getSpuName()))
                .toList();
        TradeOrderItemDO first = safeItems.isEmpty() ? null : safeItems.get(0);
        int count = safeItems.stream().mapToInt(item -> item.getCount() == null ? 0 : item.getCount()).sum();
        return new SocialWxaExpressAddOrderReqDTO.Shop()
                .setWxaPath("pages/order/detail?id=" + order.getId())
                .setGoodsName(first == null ? "订单商品" : first.getSpuName())
                .setGoodsCount(count).setDetailList(details);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
