package cn.iocoder.yudao.module.trade.framework.logistics.sf;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Component
public class SfLogisticsClient {

    public static final String CREATE_ORDER = "EXP_RECE_CREATE_ORDER";
    public static final String SEARCH_ORDER = "EXP_RECE_SEARCH_ORDER_RESP";
    public static final String UPDATE_ORDER = "EXP_RECE_UPDATE_ORDER";
    public static final String CLOUD_PRINT = "COM_RECE_CLOUD_PRINT_WAYBILLS";
    public static final String SEARCH_ROUTES = "EXP_RECE_SEARCH_ROUTES";

    @Resource
    private SfOpenApiClient openApiClient;

    public WaybillResult createWaybill(TradeLogisticsAccountDO account, String providerOrderNo,
                                       TradeOrderDO order, List<TradeOrderItemDO> items,
                                       Map<Long, ProductSkuRespDTO> skuMap) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("language", "zh-CN").put("orderId", providerOrderNo)
                .put("monthlyCard", account.getMonthlyCard())
                .put("expressTypeId", Integer.parseInt(account.getServiceCode()))
                .put("payMethod", 1).put("parcelQty", 1).put("isReturnRoutelabel", 1)
                .put("totalWeight", calculateTotalWeight(items, skuMap, account.getDefaultWeightKg()));
        ArrayNode contacts = request.putArray("contactInfoList");
        contacts.add(contact(1, account.getSenderName(), account.getSenderPhone(), account.getSenderProvince(),
                account.getSenderCity(), account.getSenderDistrict(), account.getSenderAddress()));
        ReceiverArea receiverArea = resolveArea(order.getReceiverAreaId());
        contacts.add(contact(2, order.getReceiverName(), order.getReceiverMobile(), receiverArea.province(),
                receiverArea.city(), receiverArea.district(), order.getReceiverDetailAddress()));
        ArrayNode cargos = request.putArray("cargoDetails");
        for (TradeOrderItemDO item : items) {
            cargos.addObject().put("name", StrUtil.maxLength(item.getSpuName(), 100))
                    .put("count", item.getCount()).put("unit", "件");
        }
        JsonNode response;
        try {
            response = openApiClient.invoke(account, CREATE_ORDER, request);
        } catch (SfApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SfApiException("INVALID_RESPONSE", "顺丰创建运单响应无法解析", true, exception);
        }
        return new WaybillResult(providerOrderNo, findWaybillNo(response), response);
    }

    public WaybillResult queryByProviderOrderNo(TradeLogisticsAccountDO account, String providerOrderNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("orderId", providerOrderNo).put("searchType", 1).put("language", "zh-CN");
        JsonNode response = openApiClient.invoke(account, SEARCH_ORDER, request);
        return new WaybillResult(providerOrderNo, findWaybillNo(response), response);
    }

    public byte[] getLabel(TradeLogisticsAccountDO account, String waybillNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("templateCode", account.getTemplateCode()).put("version", "2.0").put("fileType", "pdf");
        request.putArray("documents").addObject().put("masterWaybillNo", waybillNo);
        JsonNode response = openApiClient.invoke(account, CLOUD_PRINT, request);
        String url = findText(response, "fileUrl", "url", "downloadUrl");
        if (StrUtil.isNotBlank(url)) {
            return HttpUtil.downloadBytes(url);
        }
        String content = findText(response, "fileContent", "base64", "pdfBase64");
        if (StrUtil.isBlank(content)) {
            throw new SfApiException("LABEL_NOT_FOUND", "顺丰云打印返回中没有 PDF 文件");
        }
        int comma = content.indexOf(',');
        return Base64.decode(comma >= 0 ? content.substring(comma + 1) : content);
    }

    public void cancelWaybill(TradeLogisticsAccountDO account, String providerOrderNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("orderId", providerOrderNo).put("dealType", 2).put("language", "zh-CN");
        openApiClient.invoke(account, UPDATE_ORDER, request);
    }

    public JsonNode queryTrace(TradeLogisticsAccountDO account, String waybillNo) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("trackingType", 1).put("methodType", 1).put("language", "zh-CN");
        request.putArray("trackingNumber").add(waybillNo);
        return openApiClient.invoke(account, SEARCH_ROUTES, request);
    }

    private ObjectNode contact(int type, String name, String phone, String province, String city,
                               String district, String address) {
        return JsonNodeFactory.instance.objectNode().put("contactType", type).put("contact", name)
                .put("tel", phone).put("province", province).put("city", city).put("county", district)
                .put("address", address);
    }

    private String findWaybillNo(JsonNode response) {
        String value = findText(response, "waybillNo", "waybillNoInfo", "mailNo");
        if (StrUtil.isBlank(value)) {
            JsonNode list = response.path("waybillNoInfoList");
            if (list.isArray() && !list.isEmpty()) {
                value = findText(list.get(0), "waybillNo", "mailNo");
            }
        }
        if (StrUtil.isBlank(value)) {
            throw new SfApiException("WAYBILL_NOT_FOUND", "顺丰返回中没有运单号", true, null);
        }
        return value;
    }

    private BigDecimal calculateTotalWeight(List<TradeOrderItemDO> items,
                                            Map<Long, ProductSkuRespDTO> skuMap,
                                            BigDecimal defaultWeight) {
        if (items == null || items.isEmpty() || skuMap == null) {
            return defaultWeight;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (TradeOrderItemDO item : items) {
            ProductSkuRespDTO sku = skuMap.get(item.getSkuId());
            if (sku == null || sku.getWeight() == null || sku.getWeight() <= 0
                    || item.getCount() == null || item.getCount() <= 0) {
                return defaultWeight;
            }
            total = total.add(BigDecimal.valueOf(sku.getWeight())
                    .multiply(BigDecimal.valueOf(item.getCount())));
        }
        return total.signum() > 0 ? total : defaultWeight;
    }

    private String findText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode() && StrUtil.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String found = findText(child, names);
                if (StrUtil.isNotBlank(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private ReceiverArea resolveArea(Integer areaId) {
        String province = "";
        String city = "";
        String district = "";
        Area area = areaId == null ? null : AreaUtils.getArea(areaId);
        while (area != null) {
            if (AreaTypeEnum.PROVINCE.getType().equals(area.getType())) {
                province = area.getName();
            } else if (AreaTypeEnum.CITY.getType().equals(area.getType())) {
                city = area.getName();
            } else if (AreaTypeEnum.DISTRICT.getType().equals(area.getType())) {
                district = area.getName();
            }
            area = area.getParent();
        }
        return new ReceiverArea(province, city, district);
    }

    public record WaybillResult(String providerOrderNo, String waybillNo, JsonNode rawResponse) {
    }

    private record ReceiverArea(String province, String city, String district) {
    }
}
