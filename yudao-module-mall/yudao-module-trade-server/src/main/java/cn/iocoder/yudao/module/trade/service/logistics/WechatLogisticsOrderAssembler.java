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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将交易订单组装为微信物流助手 addOrder 请求。
 */
@Slf4j
public class WechatLogisticsOrderAssembler {

    private static final int GOODS_NAME_MAX_BYTES = 128;
    private static final int CUSTOM_REMARK_MAX_BYTES = 1024;

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
                .setCustomRemark(buildCustomRemark(items, skuMap))
                .setExpectTime(0L)
                .setSender(buildSender(config))
                .setReceiver(buildReceiver(order))
                .setCargo(buildCargo(items, skuMap, config))
                .setShop(buildShop(items, skuMap, order))
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
                    .setName(buildPrintableCargoName(item, sku))
                    .setCount(item.getCount()));
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

    private SocialWxaExpressAddOrderReqDTO.Shop buildShop(List<TradeOrderItemDO> items,
                                                           Map<Long, ProductSkuRespDTO> skuMap,
                                                           TradeOrderDO order) {
        List<TradeOrderItemDO> safeItems = items == null ? Collections.emptyList() : items;
        Map<Long, ProductSkuRespDTO> safeSkuMap = skuMap == null ? Collections.emptyMap() : skuMap;
        List<SocialWxaExpressAddOrderReqDTO.ShopDetail> details = safeItems.stream()
                .map(item -> new SocialWxaExpressAddOrderReqDTO.ShopDetail()
                        .setGoodsName(truncateUtf8(buildProductName(item), GOODS_NAME_MAX_BYTES))
                        .setGoodsImgUrl(item.getPicUrl())
                        .setGoodsDesc(buildShopDescription(item, safeSkuMap.get(item.getSkuId()))))
                .toList();
        TradeOrderItemDO first = safeItems.isEmpty() ? null : safeItems.get(0);
        int count = safeItems.stream().mapToInt(item -> item.getCount() == null ? 0 : item.getCount()).sum();
        return new SocialWxaExpressAddOrderReqDTO.Shop()
                .setWxaPath("pages/order/detail?id=" + order.getId())
                .setGoodsName(first == null ? "订单商品"
                        : truncateUtf8(buildProductName(first), GOODS_NAME_MAX_BYTES))
                .setGoodsCount(count).setDetailList(details);
    }

    private String buildCustomRemark(List<TradeOrderItemDO> items, Map<Long, ProductSkuRespDTO> skuMap) {
        List<TradeOrderItemDO> safeItems = items == null ? Collections.emptyList() : items;
        Map<Long, ProductSkuRespDTO> safeSkuMap = skuMap == null ? Collections.emptyMap() : skuMap;
        StringBuilder remark = new StringBuilder();
        for (TradeOrderItemDO item : safeItems) {
            String entry = joinNonBlank(buildPrintableCargoName(item, safeSkuMap.get(item.getSkuId())),
                    formatCount(item.getCount()));
            String separator = remark.isEmpty() ? "" : "; ";
            int nextLength = remark.toString().getBytes(StandardCharsets.UTF_8).length
                    + separator.getBytes(StandardCharsets.UTF_8).length
                    + entry.getBytes(StandardCharsets.UTF_8).length;
            if (nextLength > CUSTOM_REMARK_MAX_BYTES) {
                break;
            }
            remark.append(separator).append(entry);
        }
        return remark.toString();
    }

    private String buildPrintableCargoName(TradeOrderItemDO item, ProductSkuRespDTO sku) {
        String suffix = buildPrintableCargoSuffix(sku, item.getPrice());
        int productNameMaxBytes = suffix.isBlank() ? GOODS_NAME_MAX_BYTES
                : GOODS_NAME_MAX_BYTES - suffix.getBytes(StandardCharsets.UTF_8).length - 1;
        return joinNonBlank(truncateUtf8(buildProductName(item), Math.max(productNameMaxBytes, 0)), suffix);
    }

    private String buildPrintableCargoSuffix(ProductSkuRespDTO sku, Integer price) {
        String priceText = formatPrice(price);
        int codeMaxBytes = GOODS_NAME_MAX_BYTES - priceText.getBytes(StandardCharsets.UTF_8).length
                - (priceText.isBlank() ? 0 : 1);
        String codeText = formatCode(sku);
        if (codeText.getBytes(StandardCharsets.UTF_8).length > Math.max(codeMaxBytes, 0)) {
            codeText = "";
        }
        return joinNonBlank(codeText, priceText);
    }

    private String buildProductName(TradeOrderItemDO item) {
        String properties = item.getProperties() == null ? "" : item.getProperties().stream()
                .filter(Objects::nonNull)
                .map(property -> joinProperty(property.getPropertyName(), property.getValueName()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("/"));
        return joinNonBlank(item.getSpuName(), properties);
    }

    private String buildShopDescription(TradeOrderItemDO item, ProductSkuRespDTO sku) {
        return joinNonBlank(formatCode(sku), formatPrice(item.getPrice()), formatCount(item.getCount()));
    }

    private String joinProperty(String name, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return name == null || name.isBlank() ? value : name + ":" + value;
    }

    private String formatCode(ProductSkuRespDTO sku) {
        return sku == null || sku.getBarCode() == null || sku.getBarCode().isBlank()
                ? "" : "编码:" + sku.getBarCode();
    }

    private String formatPrice(Integer price) {
        return price == null ? "" : "价格:¥" + BigDecimal.valueOf(price, 2).toPlainString();
    }

    private String formatCount(Integer count) {
        return count == null ? "" : "数量:" + count;
    }

    private String joinNonBlank(String... values) {
        return java.util.Arrays.stream(values).filter(Objects::nonNull).filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String truncateUtf8(String value, int maxBytes) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int byteCount = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + characterBytes > maxBytes) {
                break;
            }
            result.append(character);
            byteCount += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
